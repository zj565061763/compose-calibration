package com.sd.lib.compose.calibration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size

@Composable
fun rememberCalibrationState(
  groups: List<CalibrationGroup>,
): CalibrationState {
  return remember(groups) {
    CalibrationState(groups = groups)
  }
}

class CalibrationState internal constructor(
  private val groups: List<CalibrationGroup>,
) {
  private var _scaleX = 0f
  private var _scaleY = 0f

  internal var stableGroups by mutableStateOf<List<CalibrationGroup>>(emptyList())
    private set

  fun getCurrentGroups(): List<CalibrationGroup> {
    val scaleX = _scaleX.takeIf { it > 0f } ?: 1f
    val scaleY = _scaleY.takeIf { it > 0f } ?: 1f
    return stableGroups.map { group ->
      val calibrations = group.calibrations.map { calibration ->
        val points = calibration.points.map { point ->
          CalibrationPoint.create(
            name = point.name,
            x = point.x / scaleX,
            y = point.y / scaleY,
          )
        }
        calibration.overridePoints(points = points)
      }
      group.copy(calibrations = calibrations)
    }
  }

  internal fun setSize(viewSize: Size, sourceSize: Size) {
    val scaleX = if (sourceSize.width > 0) viewSize.width / sourceSize.width else 1f
    val scaleY = if (sourceSize.height > 0) viewSize.height / sourceSize.height else 1f
    if (_scaleX != scaleX || _scaleY != scaleY) {
      _scaleX = scaleX
      _scaleY = scaleY
      stableGroups = groups.map { it.toStableCalibrationGroup() }
        .onEach { it.scalePoints(scaleX = scaleX, scaleY = scaleY) }
    }
  }
}

private fun CalibrationGroup.scalePoints(scaleX: Float, scaleY: Float) {
  calibrations.forEach { it.scalePoints(scaleX = scaleX, scaleY = scaleY) }
}

private fun Calibration.scalePoints(scaleX: Float, scaleY: Float) {
  points.forEach { point ->
    check(point is StablePoint)
    point.update(x = point.x * scaleX, y = point.y * scaleY)
  }
}