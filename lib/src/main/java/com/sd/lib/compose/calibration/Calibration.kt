package com.sd.lib.compose.calibration

import androidx.compose.runtime.Immutable

/**
 * 标定组，同一组内的标定项在选中时联动高亮
 *
 * @param calibrations 组内标定列表
 */
@Immutable
data class CalibrationGroup(
  val calibrations: List<Calibration>,
) {
  /** 是否包含指定[id]的标定 */
  fun containsCalibration(id: String): Boolean {
    return calibrations.any { it.id == id }
  }

  companion object {
    /** 创建包含指定[calibrations]的标定组 */
    fun create(vararg calibrations: Calibration): CalibrationGroup {
      return CalibrationGroup(calibrations = calibrations.toList())
    }
  }
}

/**
 * 标定项
 *
 * @param id 同一画布内唯一的标定标识，重复标识在默认样式下会联动高亮
 * @param points 标定点列表
 */
@Immutable
data class Calibration(
  val id: String,
  val points: List<CalibrationPoint>,
) {
  companion object {
    /** 创建坐标限制在 `[0f, 1f]` 范围内的标定项 */
    fun create(
      id: String,
      points: List<CalibrationPoint>,
    ): Calibration {
      return Calibration(
        id = id,
        points = points.map { it.normalized() },
      )
    }
  }
}

/** 将标定组列表中的所有标定点坐标归一化到 `[0f, 1f]` */
internal fun List<CalibrationGroup>.normalized(): List<CalibrationGroup> {
  return map { group ->
    CalibrationGroup(
      calibrations = group.calibrations.map { calibration ->
        Calibration(
          id = calibration.id,
          points = calibration.points.map { point -> point.normalized() },
        )
      },
    )
  }
}
