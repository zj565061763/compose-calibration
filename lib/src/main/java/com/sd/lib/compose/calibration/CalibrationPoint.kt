package com.sd.lib.compose.calibration

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf

@Stable
interface CalibrationPoint {
  val name: String
  val x: Float
  val y: Float

  companion object {
    fun create(name: String, x: Float, y: Float): CalibrationPoint {
      return ImmutablePointImpl(name = name, x = x, y = y)
    }
  }
}

internal interface StablePoint : CalibrationPoint {
  fun update(x: Float, y: Float)
}

internal fun CalibrationPoint.toStablePoint(): CalibrationPoint {
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
) : CalibrationPoint