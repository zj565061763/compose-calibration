package com.sd.lib.compose.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize

@Composable
fun CalibrationView(
  modifier: Modifier = Modifier,
  state: CalibrationState,
  getConfig: (current: Calibration, selected: CalibrationGroup?) -> CalibrationConfig = { current, selected ->
    if (selected != null && selected.containsCalibration(current.id)) {
      CalibrationConfig.DefaultSelected
    } else {
      CalibrationConfig.Default
    }
  },
) {
  var selectedGroup by remember(state) { mutableStateOf<CalibrationGroup?>(null) }

  val getConfigUpdated by rememberUpdatedState(getConfig)
  val textMeasurer = rememberTextMeasurer()

  Canvas(modifier = modifier.pointerInput(state) {
    awaitEachGesture {
      val down = awaitFirstDown()

      var touchedPoint = selectedGroup?.let { group ->
        findTouchedPoint(
          group = group,
          touched = down.position,
          containerSize = size.toSize(),
          getConfig = { getConfigUpdated(it, selectedGroup) },
        )
      }

      if (touchedPoint == null) {
        for (group in state.stableGroups.asReversed()) {
          if (group == selectedGroup) continue
          touchedPoint = findTouchedPoint(
            group = group,
            touched = down.position,
            containerSize = size.toSize(),
            getConfig = { getConfigUpdated(it, selectedGroup) },
          )
          if (touchedPoint != null) {
            selectedGroup = group
            break
          }
        }
      }

      if (touchedPoint == null) {
        return@awaitEachGesture
      }

      val touchSlopChange = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
      if (touchSlopChange == null) return@awaitEachGesture

      (touchSlopChange.position - down.position).also { initialDistance ->
        touchedPoint.updatePercentageOffset(offset = initialDistance, containerSize = size.toSize())
      }

      drag(touchSlopChange.id) { change ->
        val dragAmount = change.positionChange()
        change.consume()
        touchedPoint.updatePercentageOffset(offset = dragAmount, containerSize = size.toSize())
      }
    }
  }) {
    for (group in state.stableGroups) {
      if (group == selectedGroup) continue
      drawCalibrationGroup(
        group = group,
        getConfig = { getConfig(it, selectedGroup) },
        textMeasurer = textMeasurer,
      )
    }
    selectedGroup?.also { group ->
      drawCalibrationGroup(
        group = group,
        getConfig = { getConfig(it, selectedGroup) },
        textMeasurer = textMeasurer,
      )
    }
  }
}

/** 绘制标定组 */
private inline fun DrawScope.drawCalibrationGroup(
  group: CalibrationGroup,
  getConfig: (Calibration) -> CalibrationConfig,
  textMeasurer: TextMeasurer,
) {
  for (calibration in group.calibrations) {
    val config = getConfig(calibration)
    val drawer = (calibration as? CalibrationDrawer) ?: CalibrationDrawer.DefaultDrawer
    drawer.run { draw(calibration, config, textMeasurer) }
  }
}

/** 查找触碰到的点 */
private inline fun Density.findTouchedPoint(
  group: CalibrationGroup,
  touched: Offset,
  containerSize: Size,
  getConfig: (Calibration) -> CalibrationConfig,
): CalibrationPoint? {
  return group.calibrations.firstNotNullOfOrNull { calibration ->
    val config = getConfig(calibration)
    calibration.findTouchedPoint(
      touched = touched,
      containerSize = containerSize,
      touchedSize = config.pointTouchedSize.toPx(),
    )
  }
}

/** 查找触碰到的点 */
private fun Calibration.findTouchedPoint(
  /** 触摸点 */
  touched: Offset,
  /** 容器大小 */
  containerSize: Size,
  /** 触摸点大小 */
  touchedSize: Float,
): CalibrationPoint? {
  return points.firstOrNull { point ->
    val absoluteOffset = point.toComposeOffset(containerSize)
    Rect(center = absoluteOffset, radius = touchedSize / 2f).contains(touched)
  }
}

/** 更新点的百分比坐标 */
private fun CalibrationPoint.updatePercentageOffset(
  /** 偏移量 (像素) */
  offset: Offset,
  /** 容器大小 */
  containerSize: Size,
) {
  require(this is StablePoint)
  if (containerSize.width <= 0f || containerSize.height <= 0f) return
  val newX = x + offset.x / containerSize.width
  val newY = y + offset.y / containerSize.height
  update(x = newX, y = newY)
}