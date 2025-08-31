package com.sd.lib.compose.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.toIntRect
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CalibrationView(
  modifier: Modifier = Modifier,
  state: CalibrationState,
  sourceSize: Size?,
  getConfig: (current: Calibration, selected: CalibrationGroup?) -> Calibration.Config = { current, selected ->
    if (selected != null && selected.containsCalibration(current.id)) {
      Calibration.Config.DefaultSelected
    } else {
      Calibration.Config.Default
    }
  },
) {
  val textMeasurer = rememberTextMeasurer()
  val drawer = remember(textMeasurer) {
    val pointNameDrawer = CalibrationDrawer.pointNameDrawer(textMeasurer)
    object : CalibrationDrawer {
      override fun DrawScope.draw(calibration: Calibration, config: Calibration.Config) {
        CalibrationDrawer.DefaultLineDrawer.run { draw(calibration, config) }
        CalibrationDrawer.DefaultPointDrawer.run { draw(calibration, config) }
        pointNameDrawer.run { draw(calibration, config) }
      }
    }
  }

  val inspectionMode = LocalInspectionMode.current
  var touchedPoint by remember { mutableStateOf<Calibration.Point?>(null) }
  var selectedGroup by remember { mutableStateOf<CalibrationGroup?>(null) }

  LaunchedEffect(state) {
    touchedPoint = null
    selectedGroup = null
  }

  var canvasSize by remember { mutableStateOf<Size?>(null) }
  val viewSize = canvasSize
  if (viewSize != null) {
    LaunchedEffect(state, viewSize, sourceSize) {
      withContext(Dispatchers.Default) {
        state.setSize(viewSize = viewSize, sourceSize = sourceSize ?: viewSize)
      }
    }
  }

  val getConfigUpdated by rememberUpdatedState(getConfig)

  Canvas(
    modifier = modifier
      .onSizeChanged { canvasSize = it.toSize() }
      .pointerInput(state) {
        awaitEachGesture {
          val down = awaitFirstDown()
          touchedPoint = null

          selectedGroup?.also { group ->
            touchedPoint = findTouchedPoint(
              group = group,
              touched = down.position,
              getConfig = { getConfigUpdated(it, null) },
            )
          }

          if (touchedPoint == null) {
            for (group in state.stableGroups) {
              if (group == selectedGroup) continue
              val point = findTouchedPoint(
                group = group,
                touched = down.position,
                getConfig = { getConfigUpdated(it, null) },
              )
              if (point != null) {
                touchedPoint = point
                selectedGroup = group
                break
              }
            }
          }

          val targetPoint = touchedPoint ?: return@awaitEachGesture

          while (true) {
            val change = awaitDragOrCancellation(down.id) ?: break
            if (!change.pressed) break
            val dragAmount = change.positionChange()
            change.consume()
            targetPoint.updateOffset(offset = dragAmount, bounds = size.toIntRect())
          }
        }
      }
  ) {
    if (inspectionMode) {
      state.setSize(viewSize = size, sourceSize = sourceSize ?: size)
    }
    for (group in state.stableGroups) {
      if (group == selectedGroup) continue
      drawCalibrationGroup(
        group = group,
        drawer = drawer,
        getConfig = { getConfig(it, selectedGroup) }
      )
    }
    selectedGroup?.also { group ->
      drawCalibrationGroup(
        group = group,
        drawer = drawer,
        getConfig = { getConfig(it, selectedGroup) }
      )
    }
  }
}

/** 绘制标定组 */
private inline fun DrawScope.drawCalibrationGroup(
  group: CalibrationGroup,
  drawer: CalibrationDrawer,
  getConfig: (Calibration) -> Calibration.Config,
) {
  for (calibration in group.calibrations) {
    val config = getConfig(calibration)
    drawer.run { draw(calibration, config) }
  }
}

/** 查找触碰到的点 */
private inline fun Density.findTouchedPoint(
  group: CalibrationGroup,
  touched: Offset,
  getConfig: (Calibration) -> Calibration.Config,
): Calibration.Point? {
  return group.calibrations.firstNotNullOfOrNull { calibration ->
    val config = getConfig(calibration)
    calibration.findTouchedPoint(touched = touched, touchedSize = config.pointTouchedSize.toPx())
  }
}

/** 查找触碰到的点 */
private fun Calibration.findTouchedPoint(
  /** 触摸点 */
  touched: Offset,
  /** 触摸点大小 */
  touchedSize: Float,
): Calibration.Point? {
  return points.firstOrNull { point ->
    Rect(center = Offset(point.x, point.y), radius = touchedSize / 2f).contains(touched)
  }
}

/** 更新点的坐标 */
private fun Calibration.Point.updateOffset(
  /** 偏移量 */
  offset: Offset,
  /** 限制范围 */
  bounds: IntRect,
) {
  require(this is StablePoint)
  val newX = (x + offset.x).coerceIn(bounds.left.toFloat(), bounds.right.toFloat())
  val newY = (y + offset.y).coerceIn(bounds.top.toFloat(), bounds.bottom.toFloat())
  update(x = newX, y = newY)
}