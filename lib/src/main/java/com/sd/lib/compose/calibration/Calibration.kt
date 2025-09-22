package com.sd.lib.compose.calibration

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
  val points: List<Point>

  fun copyWith(
    id: String? = null,
    points: List<Point>? = null,
  ): Calibration

  @Immutable
  data class Config(
    val lineColor: Color = Color.Red,
    val lineWidth: Dp = 2.dp,
    val pointColor: Color = lineColor,
    val pointSize: Dp = 8.dp,
    val pointTouchedSize: Dp = pointSize * 3f,
    val pointNameStyle: TextStyle = TextStyle(color = pointColor),
  ) {
    companion object {
      val Default = Config()
      val DefaultSelected = Config(lineColor = Color.Green)
    }
  }

  @Stable
  interface Point {
    val name: String
    val x: Float
    val y: Float

    companion object {
      fun create(name: String, x: Float, y: Float): Point {
        return ImmutablePointImpl(name = name, x = x, y = y)
      }
    }
  }

  companion object {
    fun create(id: String, points: List<Point>): Calibration {
      return ImmutableCalibrationImpl(id = id, points = points)
    }
  }
}

private data class ImmutableCalibrationImpl(
  override val id: String,
  override val points: List<Calibration.Point>,
) : Calibration {
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

internal interface StablePoint : Calibration.Point {
  fun update(x: Float, y: Float)
}

internal fun CalibrationGroup.toStableCalibrationGroup(): CalibrationGroup {
  return copy(calibrations = calibrations.map { it.toStableCalibration() })
}

private fun Calibration.toStableCalibration(): Calibration {
  return copyWith(points = points.map { it.toStablePoint() })
}

private fun Calibration.Point.toStablePoint(): Calibration.Point {
  if (this is StablePoint) return this
  return StablePointImpl(name = name, x = x, y = y)
}

private class StablePointImpl(
  override val name: String,
  x: Float,
  y: Float,
) : StablePoint {
  private val _xState = mutableFloatStateOf(x)
  private val _yState = mutableFloatStateOf(y)

  override val x: Float get() = _xState.floatValue
  override val y: Float get() = _yState.floatValue

  override fun update(x: Float, y: Float) {
    _xState.floatValue = x
    _yState.floatValue = y
  }
}

private data class ImmutablePointImpl(
  override val name: String,
  override val x: Float,
  override val y: Float,
) : Calibration.Point