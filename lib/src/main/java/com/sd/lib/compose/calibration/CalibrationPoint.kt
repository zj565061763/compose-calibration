package com.sd.lib.compose.calibration

import androidx.compose.runtime.Immutable

/**
 * 标定点
 *
 * @param name 点名称
 * @param x 归一化横坐标，范围 `[0f, 1f]`
 * @param y 归一化纵坐标，范围 `[0f, 1f]`
 */
@Immutable
data class CalibrationPoint(
  val name: String,
  val x: Float,
  val y: Float,
) {
  companion object {
    /** 创建坐标限制在 `[0f, 1f]` 范围内的标定点 */
    fun create(
      name: String,
      x: Float,
      y: Float,
    ): CalibrationPoint {
      return CalibrationPoint(
        name = name,
        x = x.coerceNormalized(),
        y = y.coerceNormalized(),
      )
    }
  }
}

/** 返回坐标限制在 `[0f, 1f]` 范围内的标定点 */
internal fun CalibrationPoint.normalized(): CalibrationPoint {
  val normalizedX = x.coerceNormalized()
  val normalizedY = y.coerceNormalized()
  if (x.toRawBits() == normalizedX.toRawBits() && y.toRawBits() == normalizedY.toRawBits()) {
    return this
  }
  return copy(
    x = normalizedX,
    y = normalizedY,
  )
}

internal fun Float.coerceNormalized(): Float {
  if (isNaN()) return 0f
  val clamped = coerceIn(0f, 1f)
  return if (clamped == 0f) 0f else clamped
}
