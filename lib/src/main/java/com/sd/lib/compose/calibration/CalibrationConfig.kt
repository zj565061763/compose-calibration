package com.sd.lib.compose.calibration

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 标定绘制样式配置
 *
 * @param lineColor 连线颜色
 * @param lineWidth 连线宽度
 * @param pointColor 标定点颜色
 * @param pointSize 标定点绘制直径
 * @param pointTouchedSize 标定点触摸热区直径
 * @param pointNameStyle 标定点名称文本样式
 */
@Immutable
data class CalibrationConfig(
  val lineColor: Color = Color.Red,
  val lineWidth: Dp = 2.dp,
  val pointColor: Color = lineColor,
  val pointSize: Dp = 8.dp,
  val pointTouchedSize: Dp = pointSize * 4f,
  val pointNameStyle: TextStyle = TextStyle(color = pointColor),
) {
  companion object {
    /** 默认未选中样式 */
    val Default = CalibrationConfig()

    /** 默认选中样式 */
    val DefaultSelected = CalibrationConfig(lineColor = Color.Green)
  }
}
