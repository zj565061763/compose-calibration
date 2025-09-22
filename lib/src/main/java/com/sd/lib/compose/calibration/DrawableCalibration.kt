package com.sd.lib.compose.calibration

fun Calibration.drawable(drawer: CalibrationDrawer): Calibration {
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
  override val points: List<Calibration.Point>,
  val drawer: CalibrationDrawer,
) : Calibration, CalibrationDrawer by drawer {
  override fun copyWith(
    id: String?,
    points: List<Calibration.Point>?,
  ): Calibration {
    return copy(
      id = id ?: this.id,
      points = points ?: this.points,
    )
  }
}