package com.sd.lib.compose.calibration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberCalibrationState(
  groups: List<CalibrationGroup>,
): CalibrationState {
  return remember { CalibrationState() }.also { it.Init(groups) }
}

class CalibrationState internal constructor() {
  internal var stableGroups by mutableStateOf<List<CalibrationGroup>>(emptyList())
    private set

  @Composable
  internal fun Init(groups: List<CalibrationGroup>) {
    LaunchedEffect(groups) {
      withContext(Dispatchers.Default) {
        stableGroups = groups.map { it.toStableCalibrationGroup() }
      }
    }
  }

  /** 获取当前的标定组 (坐标为百分比) */
  fun getCurrentGroups(): List<CalibrationGroup> {
    return stableGroups
  }
}