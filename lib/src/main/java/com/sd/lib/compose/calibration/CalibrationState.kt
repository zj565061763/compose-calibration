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
  private var _sourceSize = Size.Zero

  internal var stableGroups by mutableStateOf<List<CalibrationGroup>>(emptyList())
    private set

  fun getCurrentGroups(): List<CalibrationGroup> {
    val sourceSize = _sourceSize
    return stableGroups.map { group ->
      val calibrations = group.calibrations.map { calibration ->
        val points = calibration.points.map { point ->
          CalibrationPoint.create(
            name = point.name,
            x = point.x * sourceSize.width,
            y = point.y * sourceSize.height,
          )
        }
        calibration.overridePoints(points = points)
      }
      group.copy(calibrations = calibrations)
    }
  }

  internal fun setSize(viewSize: Size, sourceSize: Size) {
    if (_sourceSize != sourceSize) {
      _sourceSize = sourceSize
      stableGroups = groups.map { group ->
        val calibrations = group.calibrations.map { calibration ->
          val points = calibration.points.map { point ->
            val px = if (sourceSize.width > 0) point.x / sourceSize.width else 0f
            val py = if (sourceSize.height > 0) point.y / sourceSize.height else 0f
            point.toStablePoint().apply { (this as StablePoint).update(px, py) }
          }
          calibration.overridePoints(points = points)
        }
        group.copy(calibrations = calibrations)
      }
    }
  }
}
