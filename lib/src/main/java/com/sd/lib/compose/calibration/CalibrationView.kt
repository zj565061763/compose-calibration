package com.sd.lib.compose.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize

/**
 * 受控模式的标定画布，拖拽结束或手势中断时通过[onValueChange]回调最新的不可变标定组列表
 *
 * @param modifier 修饰符
 * @param groups 当前标定组列表
 * @param onValueChange 标定点坐标变更后的回调
 * @param enabled 是否允许触摸拖拽标定点
 * @param getConfig 获取标定项的绘制样式配置
 * @param getDrawer 获取标定项的绘制器
 */
@Composable
fun CalibrationView(
  modifier: Modifier = Modifier,
  groups: List<CalibrationGroup>,
  onValueChange: (List<CalibrationGroup>) -> Unit,
  enabled: Boolean = true,
  getConfig: (current: Calibration, selected: CalibrationGroup?) -> CalibrationConfig = DefaultGetConfig,
  getDrawer: (Calibration) -> CalibrationDrawer = DefaultGetDrawer,
) {
  val normalizedGroups = remember(groups) { groups.normalized() }
  val state = remember { CalibrationState(initialGroups = normalizedGroups) }
  var lastSyncedGroups by remember { mutableStateOf(normalizedGroups) }
  var commitVersion by remember { mutableIntStateOf(0) }

  if (normalizedGroups != lastSyncedGroups) {
    lastSyncedGroups = normalizedGroups
    state.setGroups(normalizedGroups)
  }

  val latestGroupsState = rememberUpdatedState(normalizedGroups)
  LaunchedEffect(normalizedGroups, commitVersion) {
    if (commitVersion > 0 && latestGroupsState.value != state.groups) {
      withFrameNanos {}
      if (latestGroupsState.value != state.groups) {
        state.setGroups(latestGroupsState.value)
      }
    }
  }

  CalibrationView(
    modifier = modifier,
    state = state,
    enabled = enabled,
    onValueChange = { updatedGroups ->
      commitVersion++
      onValueChange(updatedGroups)
    },
    getConfig = getConfig,
    getDrawer = getDrawer,
  )
}

/**
 * 基于[CalibrationState]的标定画布
 *
 * @param modifier 修饰符
 * @param state 标定状态持有者
 * @param enabled 是否允许触摸拖拽标定点
 * @param onValueChange 标定点坐标变更后的可选回调
 * @param getConfig 获取标定项的绘制样式配置
 * @param getDrawer 获取标定项的绘制器
 */
@Composable
fun CalibrationView(
  modifier: Modifier = Modifier,
  state: CalibrationState,
  enabled: Boolean = true,
  onValueChange: ((List<CalibrationGroup>) -> Unit)? = null,
  getConfig: (current: Calibration, selected: CalibrationGroup?) -> CalibrationConfig = DefaultGetConfig,
  getDrawer: (Calibration) -> CalibrationDrawer = DefaultGetDrawer,
) {
  val onValueChangeState = rememberUpdatedState(onValueChange)
  val getConfigState = rememberUpdatedState(getConfig)
  val getDrawerState = rememberUpdatedState(getDrawer)
  val textMeasurer = rememberTextMeasurer()

  val canvasModifier = if (enabled) {
    modifier.pointerInput(state, state.generation) {
      awaitEachGesture {
        val down = awaitFirstDown()
        val currentGroups = state.renderGroups
        val currentSelectedIndex = state.selectedGroupIndex
        val currentSelectedSnapshot = Snapshot.withoutReadObservation {
          currentGroups.getOrNull(currentSelectedIndex)?.toCalibrationGroup()
        }

        var touchedPoint = currentGroups.getOrNull(currentSelectedIndex)?.let { group ->
          findTouchedPoint(
            group = group,
            touched = down.position,
            containerSize = size.toSize(),
            getConfig = { getConfigState.value(it, currentSelectedSnapshot) },
          )
        }

        if (touchedPoint == null) {
          for (index in currentGroups.indices.reversed()) {
            if (index == currentSelectedIndex) continue
            val group = currentGroups[index]
            touchedPoint = findTouchedPoint(
              group = group,
              touched = down.position,
              containerSize = size.toSize(),
              getConfig = { getConfigState.value(it, currentSelectedSnapshot) },
            )
            if (touchedPoint != null) {
              state.selectGroup(index)
              break
            }
          }
        }

        val targetPoint = touchedPoint ?: return@awaitEachGesture
        val dragGeneration = state.generation

        val touchSlopChange = awaitTouchSlopOrCancellation(down.id) { change, _ ->
          change.consume()
        } ?: return@awaitEachGesture

        state.markDirty()
        try {
          (touchSlopChange.position - down.position).also { initialDistance ->
            targetPoint.updatePercentageOffset(
              offset = initialDistance,
              containerSize = size.toSize(),
            )
          }

          drag(touchSlopChange.id) { change ->
            val dragAmount = change.positionChange()
            change.consume()
            targetPoint.updatePercentageOffset(
              offset = dragAmount,
              containerSize = size.toSize(),
            )
          }
        } finally {
          state.commitDrag(dragGeneration)?.also { updatedGroups ->
            onValueChangeState.value?.invoke(updatedGroups)
          }
        }
      }
    }
  } else {
    modifier
  }

  Canvas(modifier = canvasModifier) {
    val renderGroups = state.renderGroups
    val selectedIndex = state.selectedGroupIndex
    val selectedSnapshot = renderGroups.getOrNull(selectedIndex)?.toCalibrationGroup()

    for (index in renderGroups.indices) {
      if (index == selectedIndex) continue
      drawRenderGroup(
        group = renderGroups[index],
        getConfig = { getConfigState.value(it, selectedSnapshot) },
        getDrawer = getDrawerState.value,
        textMeasurer = textMeasurer,
      )
    }

    renderGroups.getOrNull(selectedIndex)?.also { selectedRenderGroup ->
      drawRenderGroup(
        group = selectedRenderGroup,
        getConfig = { getConfigState.value(it, selectedSnapshot) },
        getDrawer = getDrawerState.value,
        textMeasurer = textMeasurer,
      )
    }
  }
}

private val DefaultGetConfig: (Calibration, CalibrationGroup?) -> CalibrationConfig = { current, selected ->
  if (selected != null && selected.containsCalibration(current.id)) {
    CalibrationConfig.DefaultSelected
  } else {
    CalibrationConfig.Default
  }
}

private val DefaultGetDrawer: (Calibration) -> CalibrationDrawer = {
  CalibrationDrawer.Default
}

/** 绘制单个标定组 */
private inline fun DrawScope.drawRenderGroup(
  group: RenderGroup,
  getConfig: (Calibration) -> CalibrationConfig,
  getDrawer: (Calibration) -> CalibrationDrawer,
  textMeasurer: TextMeasurer,
) {
  for (renderCalibration in group.calibrations) {
    val calibration = renderCalibration.toCalibration()
    val config = getConfig(calibration)
    val drawer = getDrawer(calibration)
    drawer.run { draw(calibration, config, textMeasurer) }
  }
}

/** 在标定组中查找命中的顶点 */
private inline fun Density.findTouchedPoint(
  group: RenderGroup,
  touched: Offset,
  containerSize: Size,
  getConfig: (Calibration) -> CalibrationConfig,
): RenderPoint? {
  return group.calibrations.firstNotNullOfOrNull { renderCalibration ->
    val calibration = Snapshot.withoutReadObservation {
      renderCalibration.toCalibration()
    }
    val config = getConfig(calibration)
    renderCalibration.findTouchedPoint(
      touched = touched,
      containerSize = containerSize,
      touchedSize = config.pointTouchedSize.toPx(),
    )
  }
}

/** 在单个标定项中查找命中的顶点 */
private fun RenderCalibration.findTouchedPoint(
  touched: Offset,
  containerSize: Size,
  touchedSize: Float,
): RenderPoint? {
  return Snapshot.withoutReadObservation {
    points.firstOrNull { point ->
      val absoluteOffset = point.toCalibrationPoint().toComposeOffset(containerSize)
      Rect(center = absoluteOffset, radius = touchedSize / 2f).contains(touched)
    }
  }
}

/** 按画布像素偏移更新顶点的归一化坐标 */
private fun RenderPoint.updatePercentageOffset(
  offset: Offset,
  containerSize: Size,
) {
  if (containerSize.width <= 0f || containerSize.height <= 0f) return
  val currentX = Snapshot.withoutReadObservation { x }
  val currentY = Snapshot.withoutReadObservation { y }
  update(
    x = currentX + offset.x / containerSize.width,
    y = currentY + offset.y / containerSize.height,
  )
}
