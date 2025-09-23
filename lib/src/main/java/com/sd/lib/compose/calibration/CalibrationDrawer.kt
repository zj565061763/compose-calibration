package com.sd.lib.compose.calibration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText

fun interface CalibrationDrawer {
  fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  )

  companion object {
    internal val DefaultDrawer: CalibrationDrawer = create()

    fun create(
      lineDrawer: CalibrationDrawer = defaultLineDrawer(),
      pointDrawer: CalibrationDrawer = DefaultPointDrawer(),
      pointNameDrawer: CalibrationDrawer = defaultPointNameDrawer(),
    ): CalibrationDrawer {
      return DefaultDrawer(
        lineDrawer = lineDrawer,
        pointDrawer = pointDrawer,
        pointNameDrawer = pointNameDrawer,
      )
    }

    fun defaultLineDrawer(
      closeLines: Boolean = true,
    ): CalibrationDrawer {
      return DefaultLineDrawer(closeLines = closeLines)
    }

    fun defaultPointNameDrawer(
      position: CalibrationPointNamePosition? = null,
    ): CalibrationDrawer {
      return DefaultPointNameDrawer(position = position)
    }
  }
}

enum class CalibrationPointNamePosition {
  TopEnd,
  BottomEnd,
  CenterEnd,
}

private class DefaultDrawer(
  val lineDrawer: CalibrationDrawer,
  val pointDrawer: CalibrationDrawer,
  val pointNameDrawer: CalibrationDrawer,
) : CalibrationDrawer {
  override fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  ) {
    lineDrawer.run { draw(calibration, config, textMeasurer) }
    pointDrawer.run { draw(calibration, config, textMeasurer) }
    pointNameDrawer.run { draw(calibration, config, textMeasurer) }
  }
}

private class DefaultLineDrawer(
  private val closeLines: Boolean,
) : CalibrationDrawer {
  override fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  ) {
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
          val end = points.getOrNull(index + 1)
          if (end != null) {
            drawLine(
              color = config.lineColor,
              start = point.toComposeOffset(),
              end = end.toComposeOffset(),
              strokeWidth = config.lineWidth.toPx(),
            )
          } else {
            if (closeLines) {
              drawLine(
                color = config.lineColor,
                start = point.toComposeOffset(),
                end = points.first().toComposeOffset(),
                strokeWidth = config.lineWidth.toPx(),
              )
            }
          }
        }
      }
    }
  }
}

private class DefaultPointDrawer : CalibrationDrawer {
  override fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  ) {
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
  private val position: CalibrationPointNamePosition? = null,
) : CalibrationDrawer {
  override fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  ) {
    val points = calibration.points
    when {
      points.isEmpty() -> return
      points.size == 1 -> {
        val point = points.first()
        val textLayoutResult = textMeasurer.measure(text = point.name, style = config.pointNameStyle)
        drawPoint(
          point = point,
          config = config,
          textLayoutResult = textLayoutResult,
          position = position ?: CalibrationPointNamePosition.TopEnd,
        )
      }
      else -> {
        if (position != null) {
          points.forEach { point ->
            val textLayoutResult = textMeasurer.measure(text = point.name, style = config.pointNameStyle)
            drawPoint(
              point = point,
              config = config,
              textLayoutResult = textLayoutResult,
              position = position,
            )
          }
        } else {
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
  }

  private fun DrawScope.drawPoint(
    point: CalibrationPoint,
    config: CalibrationConfig,
    textLayoutResult: TextLayoutResult,
    position: CalibrationPointNamePosition,
  ) {
    when (position) {
      CalibrationPointNamePosition.TopEnd -> drawPointTopEnd(point = point, config = config, textLayoutResult = textLayoutResult)
      CalibrationPointNamePosition.BottomEnd -> drawPointBottomEnd(point = point, config = config, textLayoutResult = textLayoutResult)
      CalibrationPointNamePosition.CenterEnd -> drawPointCenterEnd(point = point, config = config, textLayoutResult = textLayoutResult)
    }
  }

  private fun DrawScope.drawPointTopEnd(
    point: CalibrationPoint,
    config: CalibrationConfig,
    textLayoutResult: TextLayoutResult,
  ) {
    drawText(
      textLayoutResult = textLayoutResult,
      topLeft = point.toComposeOffset(appendY = -textLayoutResult.size.height.toFloat() - (config.pointSize.toPx() / 2f)),
    )
  }

  private fun DrawScope.drawPointBottomEnd(
    point: CalibrationPoint,
    config: CalibrationConfig,
    textLayoutResult: TextLayoutResult,
  ) {
    drawText(
      textLayoutResult = textLayoutResult,
      topLeft = point.toComposeOffset(appendY = config.pointSize.toPx() / 2f),
    )
  }

  private fun DrawScope.drawPointCenterEnd(
    point: CalibrationPoint,
    config: CalibrationConfig,
    textLayoutResult: TextLayoutResult,
  ) {
    val appendX = config.pointSize.toPx() / 2f
    val appendY = -textLayoutResult.size.height.toFloat() / 2f
    drawText(
      textLayoutResult = textLayoutResult,
      topLeft = point.toComposeOffset(appendX = appendX, appendY = appendY),
    )
  }
}

fun CalibrationPoint.toComposeOffset(
  appendX: Float = 0f,
  appendY: Float = 0f,
): Offset {
  return Offset(x = x + appendX, y = y + appendY)
}