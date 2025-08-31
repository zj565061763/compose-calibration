package com.sd.lib.compose.calibration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText

fun interface CalibrationDrawer {
  fun DrawScope.draw(calibration: Calibration, config: Calibration.Config)

  companion object {
    internal val DefaultLineDrawer: CalibrationDrawer = DefaultLineDrawer()
    internal val DefaultPointDrawer: CalibrationDrawer = DefaultPointDrawer()

    internal fun pointNameDrawer(textMeasurer: TextMeasurer): CalibrationDrawer {
      return DefaultPointNameDrawer(textMeasurer = textMeasurer)
    }
  }
}

private class DefaultLineDrawer : CalibrationDrawer {
  override fun DrawScope.draw(calibration: Calibration, config: Calibration.Config) {
    val points = calibration.points
    when {
      points.size < 2 -> return
      points.size == 2 -> {
        drawLine(
          color = config.lineColor,
          start = points.first().toComposeOffset(),
          end = points.last().toComposeOffset(),
          strokeWidth = config.lineWidth.toPx(),
        )
      }
      else -> {
        points.forEachIndexed { index, point ->
          val end = points.getOrNull(index + 1) ?: points.first()
          drawLine(
            color = config.lineColor,
            start = point.toComposeOffset(),
            end = end.toComposeOffset(),
            strokeWidth = config.lineWidth.toPx(),
          )
        }
      }
    }
  }
}

private class DefaultPointDrawer : CalibrationDrawer {
  override fun DrawScope.draw(calibration: Calibration, config: Calibration.Config) {
    calibration.points.forEach { point ->
      drawCircle(
        color = config.pointColor,
        radius = config.pointSize.toPx() / 2f,
        center = point.toComposeOffset(),
      )
    }
  }
}

private class DefaultPointNameDrawer(
  private val textMeasurer: TextMeasurer,
) : CalibrationDrawer {
  override fun DrawScope.draw(calibration: Calibration, config: Calibration.Config) {
    val points = calibration.points
    when {
      points.isEmpty() -> return
      points.size == 1 -> {
        val point = points.first()
        val textLayoutResult = textMeasurer.measure(text = point.name, style = config.pointNameStyle)
        drawPointTopEnd(point = point, config = config, textLayoutResult = textLayoutResult)
      }
      else -> {
        val half = points.size / 2
        points.forEachIndexed { index, point ->
          val textLayoutResult = textMeasurer.measure(text = point.name, style = config.pointNameStyle)
          if (index < half) {
            drawPointTopEnd(point = point, config = config, textLayoutResult = textLayoutResult)
          } else {
            drawPointBottomEnd(point = point, config = config, textLayoutResult = textLayoutResult)
          }
        }
      }
    }
  }

  private fun DrawScope.drawPointTopEnd(
    point: Calibration.Point,
    config: Calibration.Config,
    textLayoutResult: TextLayoutResult,
  ) {
    drawText(
      textLayoutResult = textLayoutResult,
      topLeft = point.toComposeOffset(appendY = -textLayoutResult.size.height.toFloat() - (config.pointSize.toPx() / 2f)),
    )
  }

  private fun DrawScope.drawPointBottomEnd(
    point: Calibration.Point,
    config: Calibration.Config,
    textLayoutResult: TextLayoutResult,
  ) {
    drawText(
      textLayoutResult = textLayoutResult,
      topLeft = point.toComposeOffset(appendY = config.pointSize.toPx() / 2f),
    )
  }
}

private fun Calibration.Point.toComposeOffset(
  appendX: Float = 0f,
  appendY: Float = 0f,
): Offset {
  return Offset(x = x + appendX, y = y + appendY)
}