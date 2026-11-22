package com.sd.lib.compose.calibration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText

/** 标定绘制器 */
fun interface CalibrationDrawer {
  /** 绘制单个[calibration] */
  fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  )

  companion object {
    /** 默认标定绘制器 */
    val Default: CalibrationDrawer = create()

    /** 组合连线、标定点与点名称绘制器 */
    fun create(
      lineDrawer: CalibrationDrawer = defaultLineDrawer(),
      pointDrawer: CalibrationDrawer = DefaultPointDrawer,
      pointNameDrawer: CalibrationDrawer = defaultPointNameDrawer(),
    ): CalibrationDrawer {
      return DefaultDrawer(
        lineDrawer = lineDrawer,
        pointDrawer = pointDrawer,
        pointNameDrawer = pointNameDrawer,
      )
    }

    /** 创建默认连线绘制器，[closeLines]控制点数超过 2 个时是否首尾闭合 */
    fun defaultLineDrawer(
      closeLines: Boolean = true,
    ): CalibrationDrawer {
      return DefaultLineDrawer(closeLines = closeLines)
    }

    /** 创建默认标定点名称绘制器，[position]为 `null` 时按索引上下分布 */
    fun defaultPointNameDrawer(
      position: CalibrationPointNamePosition? = null,
    ): CalibrationDrawer {
      return DefaultPointNameDrawer(position = position)
    }
  }
}

/** 标定点名称相对标定点的绘制方位 */
enum class CalibrationPointNamePosition {
  TopEnd,
  BottomEnd,
  CenterEnd,
}

/** 将归一化标定点转换为画布像素坐标 */
fun CalibrationPoint.toComposeOffset(
  containerSize: Size,
  appendX: Float = 0f,
  appendY: Float = 0f,
): Offset {
  if (containerSize.width <= 0f || containerSize.height <= 0f) return Offset.Zero
  return Offset(
    x = x * containerSize.width + appendX,
    y = y * containerSize.height + appendY,
  )
}

private data class DefaultDrawer(
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

private data class DefaultLineDrawer(
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
          start = points.first().toComposeOffset(size),
          end = points.last().toComposeOffset(size),
          strokeWidth = config.lineWidth.toPx(),
        )
      }
      else -> {
        points.forEachIndexed { index, point ->
          val end = points.getOrNull(index + 1)
          if (end != null) {
            drawLine(
              color = config.lineColor,
              start = point.toComposeOffset(size),
              end = end.toComposeOffset(size),
              strokeWidth = config.lineWidth.toPx(),
            )
          } else if (closeLines) {
            drawLine(
              color = config.lineColor,
              start = point.toComposeOffset(size),
              end = points.first().toComposeOffset(size),
              strokeWidth = config.lineWidth.toPx(),
            )
          }
        }
      }
    }
  }
}

private data object DefaultPointDrawer : CalibrationDrawer {
  override fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  ) {
    calibration.points.forEach { point ->
      drawCircle(
        color = config.pointColor,
        radius = config.pointSize.toPx() / 2f,
        center = point.toComposeOffset(size),
      )
    }
  }
}

private data class DefaultPointNameDrawer(
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
        drawPointName(
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
            drawPointName(
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
              drawPointNameTopEnd(point = point, config = config, textLayoutResult = textLayoutResult)
            } else {
              drawPointNameBottomEnd(point = point, config = config, textLayoutResult = textLayoutResult)
            }
          }
        }
      }
    }
  }
}

private fun DrawScope.drawPointName(
  point: CalibrationPoint,
  config: CalibrationConfig,
  textLayoutResult: TextLayoutResult,
  position: CalibrationPointNamePosition,
) {
  when (position) {
    CalibrationPointNamePosition.TopEnd -> drawPointNameTopEnd(point = point, config = config, textLayoutResult = textLayoutResult)
    CalibrationPointNamePosition.BottomEnd -> drawPointNameBottomEnd(point = point, config = config, textLayoutResult = textLayoutResult)
    CalibrationPointNamePosition.CenterEnd -> drawPointNameCenterEnd(point = point, config = config, textLayoutResult = textLayoutResult)
  }
}

private fun DrawScope.drawPointNameTopEnd(
  point: CalibrationPoint,
  config: CalibrationConfig,
  textLayoutResult: TextLayoutResult,
) {
  drawText(
    textLayoutResult = textLayoutResult,
    topLeft = point.toComposeOffset(size, appendY = -textLayoutResult.size.height.toFloat() - (config.pointSize.toPx() / 2f)),
  )
}

private fun DrawScope.drawPointNameBottomEnd(
  point: CalibrationPoint,
  config: CalibrationConfig,
  textLayoutResult: TextLayoutResult,
) {
  drawText(
    textLayoutResult = textLayoutResult,
    topLeft = point.toComposeOffset(size, appendY = config.pointSize.toPx() / 2f),
  )
}

private fun DrawScope.drawPointNameCenterEnd(
  point: CalibrationPoint,
  config: CalibrationConfig,
  textLayoutResult: TextLayoutResult,
) {
  val appendX = config.pointSize.toPx() / 2f + textLayoutResult.size.height.toFloat() / 4f
  val appendY = -textLayoutResult.size.height.toFloat() / 2f
  drawText(
    textLayoutResult = textLayoutResult,
    topLeft = point.toComposeOffset(size, appendX = appendX, appendY = appendY),
  )
}
