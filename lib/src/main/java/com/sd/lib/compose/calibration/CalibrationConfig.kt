package com.sd.lib.compose.calibration

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CalibrationConfig(
  val lineColor: Color = Color.Red,
  val lineWidth: Dp = 2.dp,
  val pointColor: Color = lineColor,
  val pointSize: Dp = 8.dp,
  val pointTouchedSize: Dp = pointSize * 3f,
  val pointNameStyle: TextStyle = TextStyle(color = pointColor),
) {
  companion object {
    val Default = CalibrationConfig()
    val DefaultSelected = CalibrationConfig(lineColor = Color.Green)
  }
}