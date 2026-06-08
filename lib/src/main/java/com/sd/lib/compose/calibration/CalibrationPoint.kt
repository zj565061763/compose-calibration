package com.sd.lib.compose.calibration

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot

@Stable
interface CalibrationPoint {
  val name: String
  /** x坐标百分比[0-1] */
  val x: Float
  /** y坐标百分比[0-1] */
  val y: Float

  companion object {
    fun create(
      name: String,
      /** x坐标百分比[0-1] */
      x: Float,
      /** y坐标百分比[0-1] */
      y: Float,
    ): CalibrationPoint {
      return ImmutablePointImpl(
        name = name,
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
      )
    }
  }
}

internal interface StablePoint : CalibrationPoint {
  fun update(x: Float, y: Float)
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
    _xState.floatValue = x.coerceIn(0f, 1f)
    _yState.floatValue = y.coerceIn(0f, 1f)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CalibrationPoint) return false
    if (name != other.name) return false
    return Snapshot.withoutReadObservation {
      x == other.x && y == other.y
    }
  }

  override fun hashCode(): Int {
    return Snapshot.withoutReadObservation {
      var result = name.hashCode()
      result = 31 * result + x.hashCode()
      result = 31 * result + y.hashCode()
      result
    }
  }
}

private data class ImmutablePointImpl(
  override val name: String,
  override val x: Float,
  override val y: Float,
) : CalibrationPoint

internal fun CalibrationGroup.asStableCalibrationGroup(): CalibrationGroup {
  return copy(calibrations = calibrations.map { it.asStableCalibration() })
}

private fun Calibration.asStableCalibration(): Calibration {
  return overridePoints(points = points.map { it.asStablePoint() })
}

private fun CalibrationPoint.asStablePoint(): CalibrationPoint {
  if (this is StablePoint) return this
  return StablePointImpl(
    name = name,
    x = x.coerceIn(0f, 1f),
    y = y.coerceIn(0f, 1f),
  )
}