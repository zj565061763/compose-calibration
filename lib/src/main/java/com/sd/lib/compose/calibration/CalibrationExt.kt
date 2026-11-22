package com.sd.lib.compose.calibration

/** 在标定组列表中查找首个匹配指定[id]的标定项 */
fun List<CalibrationGroup>.findCalibration(id: String): Calibration? {
  for (group in this) {
    for (calibration in group.calibrations) {
      if (calibration.id == id) {
        return calibration
      }
    }
  }
  return null
}

/** 在标定组列表中查找首个匹配指定[id]的标定项顶点列表 */
fun List<CalibrationGroup>.findPoints(id: String): List<CalibrationPoint>? {
  return findCalibration(id)?.points
}
