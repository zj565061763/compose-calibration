package com.sd.lib.compose.calibration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberCalibrationState(
  groups: List<CalibrationGroup>,
): CalibrationState {
  return remember(groups) { CalibrationState(groups = groups) }
}

class CalibrationState internal constructor(
  groups: List<CalibrationGroup>,
) {
  internal val stableGroups: List<CalibrationGroup> = groups.map { it.asStableCalibrationGroup() }

  /** 获取当前的标定组 (坐标为百分比) */
  fun getCurrentGroups(): List<CalibrationGroup> {
    return stableGroups
  }
}