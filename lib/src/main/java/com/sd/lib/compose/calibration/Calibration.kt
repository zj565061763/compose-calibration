package com.sd.lib.compose.calibration

import androidx.compose.runtime.Stable

/** 标定组 */
@Stable
data class CalibrationGroup(
  val calibrations: List<Calibration>,
) {
  /** 是否包含指定[id]的标定 */
  fun containsCalibration(id: String): Boolean {
    return calibrations.any { it.id == id }
  }

  companion object {
    fun create(vararg calibrations: Calibration): CalibrationGroup {
      return CalibrationGroup(calibrations = calibrations.toList())
    }
  }
}

/** 标定 */
@Stable
interface Calibration {
  val id: String
  val points: List<CalibrationPoint>

  fun overridePoints(points: List<CalibrationPoint>? = null): Calibration

  companion object {
    fun create(id: String, points: List<CalibrationPoint>): Calibration {
      return ImmutableCalibrationImpl(id = id, points = points)
    }
  }
}

private data class ImmutableCalibrationImpl(
  override val id: String,
  override val points: List<CalibrationPoint>,
) : Calibration {
  override fun overridePoints(points: List<CalibrationPoint>?): Calibration {
    return copy(points = points ?: this.points)
  }
}

internal fun CalibrationGroup.toStableCalibrationGroup(): CalibrationGroup {
  return copy(calibrations = calibrations.map { it.toStableCalibration() })
}

private fun Calibration.toStableCalibration(): Calibration {
  return overridePoints(points = points.map { it.toStablePoint() })
}