package com.sd.lib.compose.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
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
  val lastSyncedGroupsRef = remember { SyncedGroupsRef(normalizedGroups) }
  var commitVersion by remember { mutableIntStateOf(0) }

  SideEffect {
    if (lastSyncedGroupsRef.value != normalizedGroups) {
      lastSyncedGroupsRef.value = normalizedGroups
      state.setGroups(normalizedGroups)
    }
  }

  val latestGroupsState = rememberUpdatedState(normalizedGroups)
  LaunchedEffect(normalizedGroups, commitVersion) {
    if (commitVersion == 0 || latestGroupsState.value == state.groups) return@LaunchedEffect
    withFrameNanos {}
    if (!state.hasUncommittedDrag && latestGroupsState.value != state.groups) {
      state.setGroups(latestGroupsState.value)
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
  val textCacheSize = remember(state, state.generation) {
    val totalPoints = Snapshot.withoutReadObservation {
      state.renderGroups.sumOf { group ->
        group.calibrations.sumOf { calibration -> calibration.points.size }
      }
    }
    maxOf(MIN_TEXT_CACHE_SIZE, totalPoints * 2)
  }
  val textMeasurer = rememberTextMeasurer(cacheSize = textCacheSize)

  val canvasModifier = if (enabled) {
    modifier.pointerInput(state, state.generation) {
      awaitEachGesture {
        val down = awaitFirstDown()
        val currentGroups = state.renderGroups
        val currentSelectedIndex = state.selectedGroupIndex
        val currentSelectedSnapshot = Snapshot.withoutReadObservation {
          currentGroups.getOrNull(currentSelectedIndex)?.toCalibrationGroup()
        }

        val hit = findNearestTouchedPoint(
          groups = currentGroups,
          selectedGroupIndex = currentSelectedIndex,
          touched = down.position,
          containerSize = size.toSize(),
          getConfig = { getConfigState.value(it, currentSelectedSnapshot) },
        ) ?: return@awaitEachGesture

        if (hit.groupIndex != currentSelectedIndex) {
          state.selectGroup(hit.groupIndex)
        }
        val targetPoint = hit.point
        down.consume()

        val startX = Snapshot.withoutReadObservation { targetPoint.x }
        val startY = Snapshot.withoutReadObservation { targetPoint.y }
        val dragGeneration = state.generation

        val touchSlopChange = awaitTouchSlopOrCancellation(down.id) { change, _ ->
          change.consume()
        } ?: return@awaitEachGesture

        state.markDirty()
        try {
          targetPoint.updateFromStart(
            startX = startX,
            startY = startY,
            totalOffset = touchSlopChange.position - down.position,
            containerSize = size.toSize(),
          )

          drag(touchSlopChange.id) { change ->
            change.consume()
            targetPoint.updateFromStart(
              startX = startX,
              startY = startY,
              totalOffset = change.position - down.position,
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
    if (!size.isValidContainerSize()) return@Canvas

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

internal class TouchedPointHit(
  val groupIndex: Int,
  val point: RenderPoint,
  val distanceSquared: Float,
)

private class SyncedGroupsRef(
  var value: List<CalibrationGroup>,
)

private const val MIN_TEXT_CACHE_SIZE = 64

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

/** 按最近距离与视觉绘制层级在所有标定组中查找命中的顶点 */
internal inline fun Density.findNearestTouchedPoint(
  groups: List<RenderGroup>,
  selectedGroupIndex: Int,
  touched: Offset,
  containerSize: Size,
  getConfig: (Calibration) -> CalibrationConfig,
): TouchedPointHit? {
  var bestHit: TouchedPointHit? = null

  groups.getOrNull(selectedGroupIndex)?.also { selectedGroup ->
    findNearestTouchedPointInGroup(
      groupIndex = selectedGroupIndex,
      group = selectedGroup,
      touched = touched,
      containerSize = containerSize,
      getConfig = getConfig,
    )?.also { hit ->
      bestHit = hit
    }
  }

  for (index in groups.indices.reversed()) {
    if (index == selectedGroupIndex) continue
    val candidate = findNearestTouchedPointInGroup(
      groupIndex = index,
      group = groups[index],
      touched = touched,
      containerSize = containerSize,
      getConfig = getConfig,
    ) ?: continue
    val currentBest = bestHit
    if (currentBest == null || candidate.distanceSquared < currentBest.distanceSquared) {
      bestHit = candidate
    }
  }

  return bestHit
}

/** 在单个标定组内查找距离触摸点最近的命中顶点，距离相等时上层标定项优先 */
internal inline fun Density.findNearestTouchedPointInGroup(
  groupIndex: Int,
  group: RenderGroup,
  touched: Offset,
  containerSize: Size,
  getConfig: (Calibration) -> CalibrationConfig,
): TouchedPointHit? {
  var bestHit: TouchedPointHit? = null
  for (index in group.calibrations.indices.reversed()) {
    val renderCalibration = group.calibrations[index]
    val calibration = Snapshot.withoutReadObservation {
      renderCalibration.toCalibration()
    }
    val config = getConfig(calibration)
    val candidate = renderCalibration.findNearestTouchedPoint(
      groupIndex = groupIndex,
      touched = touched,
      containerSize = containerSize,
      touchedSize = config.pointTouchedSize.toPx(),
    ) ?: continue
    val currentBest = bestHit
    if (currentBest == null || candidate.distanceSquared < currentBest.distanceSquared) {
      bestHit = candidate
    }
  }
  return bestHit
}

/** 在单个标定项内按圆形热区查找最近顶点，距离相等时后绘制的顶点优先 */
internal fun RenderCalibration.findNearestTouchedPoint(
  groupIndex: Int,
  touched: Offset,
  containerSize: Size,
  touchedSize: Float,
): TouchedPointHit? {
  if (!containerSize.isValidContainerSize() || !touchedSize.isFinite() || touchedSize <= 0f) {
    return null
  }
  val radius = touchedSize / 2f
  val maxDistanceSquared = radius * radius
  return Snapshot.withoutReadObservation {
    var bestPoint: RenderPoint? = null
    var bestDistanceSquared = Float.POSITIVE_INFINITY
    for (index in points.indices.reversed()) {
      val point = points[index]
      val center = point.toCalibrationPoint().toComposeOffset(containerSize)
      val distanceSquared = (touched - center).getDistanceSquared()
      if (distanceSquared <= maxDistanceSquared && distanceSquared < bestDistanceSquared) {
        bestPoint = point
        bestDistanceSquared = distanceSquared
      }
    }
    bestPoint?.let { point ->
      TouchedPointHit(
        groupIndex = groupIndex,
        point = point,
        distanceSquared = bestDistanceSquared,
      )
    }
  }
}

/** 按起始归一化坐标与相对按下点的像素总位移更新顶点坐标 */
internal fun RenderPoint.updateFromStart(
  startX: Float,
  startY: Float,
  totalOffset: Offset,
  containerSize: Size,
) {
  if (!containerSize.isValidContainerSize()) return
  update(
    x = startX + totalOffset.x / containerSize.width,
    y = startY + totalOffset.y / containerSize.height,
  )
}
