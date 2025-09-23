package com.sd.lib.compose.calibration

fun Calibration.withDrawer(drawer: CalibrationDrawer): Calibration {
  return when (this) {
    is DrawableCalibrationImpl -> this.copy(drawer = drawer)
    else -> DrawableCalibrationImpl(
      id = this.id,
      points = this.points,
      drawer = drawer,
    )
  }
}

private data class DrawableCalibrationImpl(
  override val id: String,
  override val points: List<CalibrationPoint>,
  val drawer: CalibrationDrawer,
) : Calibration, CalibrationDrawer by drawer {
  override fun overridePoints(points: List<CalibrationPoint>?): Calibration {
    return copy(points = points ?: this.points)
  }
}