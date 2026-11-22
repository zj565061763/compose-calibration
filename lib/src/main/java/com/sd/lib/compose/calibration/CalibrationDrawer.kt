package com.sd.lib.compose.calibration

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntSize

/** 标定绘制器，自定义实现应保持无状态或不可变 */
@Stable
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
  /** 位于标定点上方，文本左边缘与点中心对齐 */
  TopEnd,

  /** 位于标定点下方，文本左边缘与点中心对齐 */
  BottomEnd,

  /** 位于标定点右侧，文本垂直居中 */
  CenterEnd,
}

/** 将归一化标定点转换为画布像素坐标 */
fun CalibrationPoint.toComposeOffset(
  containerSize: Size,
  appendX: Float = 0f,
  appendY: Float = 0f,
): Offset {
  if (!containerSize.isValidContainerSize()) return Offset.Zero
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
    if (points.size < 2) return
    val strokeWidth = config.lineWidth.toPx()
    if (strokeWidth <= 0f) return
    if (points.size == 2) {
      drawLine(
        color = config.lineColor,
        start = points.first().toComposeOffset(size),
        end = points.last().toComposeOffset(size),
        strokeWidth = strokeWidth,
      )
      return
    }
    points.forEachIndexed { index, point ->
      val end = points.getOrNull(index + 1)
      if (end != null) {
        drawLine(
          color = config.lineColor,
          start = point.toComposeOffset(size),
          end = end.toComposeOffset(size),
          strokeWidth = strokeWidth,
        )
      } else if (closeLines) {
        drawLine(
          color = config.lineColor,
          start = point.toComposeOffset(size),
          end = points.first().toComposeOffset(size),
          strokeWidth = strokeWidth,
        )
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
    val radius = config.pointSize.toPx() / 2f
    if (radius <= 0f) return
    calibration.points.forEach { point ->
      drawCircle(
        color = config.pointColor,
        radius = radius,
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
        if (point.name.isEmpty()) return
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
            if (point.name.isEmpty()) return@forEach
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
            if (point.name.isEmpty()) return@forEachIndexed
            val textLayoutResult = textMeasurer.measure(text = point.name, style = config.pointNameStyle)
            drawPointName(
              point = point,
              config = config,
              textLayoutResult = textLayoutResult,
              position = if (index < half) CalibrationPointNamePosition.TopEnd else CalibrationPointNamePosition.BottomEnd,
            )
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
  val topLeft = resolvePointNameTopLeft(
    pointCenter = point.toComposeOffset(size),
    pointRadius = (config.pointSize.toPx() / 2f).coerceAtLeast(0f),
    textSize = textLayoutResult.size,
    canvasSize = size,
    position = position,
  )
  drawText(
    textLayoutResult = textLayoutResult,
    color = resolvePointNameColor(config),
    topLeft = topLeft,
  )
}

/** 解析点名称绘制颜色，样式未指定颜色且无画刷时回退为[CalibrationConfig.pointColor] */
internal fun resolvePointNameColor(config: CalibrationConfig): Color {
  return config.pointNameStyle.color.takeOrElse {
    if (config.pointNameStyle.brush != null) Color.Unspecified else config.pointColor
  }
}

/** 计算避开标定点并限制在画布可视范围内的文本左上角坐标 */
internal fun resolvePointNameTopLeft(
  pointCenter: Offset,
  pointRadius: Float,
  textSize: IntSize,
  canvasSize: Size,
  position: CalibrationPointNamePosition,
): Offset {
  if (!canvasSize.isValidContainerSize()) return pointCenter
  val textWidth = textSize.width.toFloat()
  val textHeight = textSize.height.toFloat()
  val rawX: Float
  val rawY: Float
  when (position) {
    CalibrationPointNamePosition.TopEnd -> {
      rawX = pointCenter.x
      val topY = pointCenter.y - pointRadius - textHeight
      rawY = if (topY < 0f) pointCenter.y + pointRadius else topY
    }
    CalibrationPointNamePosition.BottomEnd -> {
      rawX = pointCenter.x
      val bottomY = pointCenter.y + pointRadius
      rawY = if (bottomY + textHeight > canvasSize.height) pointCenter.y - pointRadius - textHeight else bottomY
    }
    CalibrationPointNamePosition.CenterEnd -> {
      val gapX = pointRadius + textHeight / 4f
      val rightX = pointCenter.x + gapX
      rawX = if (rightX + textWidth > canvasSize.width) pointCenter.x - gapX - textWidth else rightX
      rawY = pointCenter.y - textHeight / 2f
    }
  }
  val maxX = (canvasSize.width - textWidth).coerceAtLeast(0f)
  val maxY = (canvasSize.height - textHeight).coerceAtLeast(0f)
  return Offset(
    x = rawX.coerceIn(0f, maxX),
    y = rawY.coerceIn(0f, maxY),
  )
}

internal fun Size.isValidContainerSize(): Boolean {
  return isSpecified && width.isFinite() && height.isFinite() && width > 0f && height > 0f
}
