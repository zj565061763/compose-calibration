package com.sd.demo.compose.calibration

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import com.sd.demo.compose.calibration.theme.AppTheme
import com.sd.lib.compose.calibration.Calibration
import com.sd.lib.compose.calibration.CalibrationConfig
import com.sd.lib.compose.calibration.CalibrationDrawer
import com.sd.lib.compose.calibration.CalibrationGroup
import com.sd.lib.compose.calibration.CalibrationPoint
import com.sd.lib.compose.calibration.CalibrationPointNamePosition
import com.sd.lib.compose.calibration.CalibrationView
import com.sd.lib.compose.calibration.rememberCalibrationState
import com.sd.lib.compose.calibration.toComposeOffset
import com.sd.lib.compose.calibration.withDrawer

class SampleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme {
        Content()
      }
    }
  }
}

@Composable
private fun Content(
  modifier: Modifier = Modifier,
) {
  val groups = remember {
    val customCalibration = run {
      val points = listOf(
        CalibrationPoint.create("${listPointIndex[0]}1", 0.5f, 0.5f),
        CalibrationPoint.create("${listPointIndex[1]}1", 0.5f, 0.6f),
        CalibrationPoint.create("${listPointIndex[2]}1", 0.5f, 0.7f),
      )
      Calibration.create(id = "custom", points = points).withDrawer(
        CalibrationDrawer.create(
          lineDrawer = CalibrationDrawer.defaultLineDrawer(closeLines = false),
          pointDrawer = CustomPointDrawer(),
          pointNameDrawer = CalibrationDrawer.defaultPointNameDrawer(CalibrationPointNamePosition.CenterEnd),
        )
      )
    }

    getDefaultCalibrationGroups() + CalibrationGroup.create(customCalibration)
  }

  val state = rememberCalibrationState(groups = groups)

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    CalibrationView(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .background(Color.Black),
      state = state,
    )
  }
}

private val listPointIndex = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N")

private class CustomPointDrawer : CalibrationDrawer {
  override fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  ) {
    calibration.points.forEach { point ->
      val pointSizePX = config.pointSize.toPx()
      drawRect(
        color = config.pointColor,
        topLeft = point.toComposeOffset(size) - Offset(pointSizePX / 2f, pointSizePX / 2f),
        size = Size(pointSizePX, pointSizePX),
      )
    }
  }
}

private fun getDefaultCalibrationGroups(): List<CalibrationGroup> {
  return getPercentRect(totalCount = 8, columnCount = 4).let { groups ->
    groups.mapIndexed { groupIndex, groupItem ->
      val points = groupItem.mapIndexed { pointIndex, point -> CalibrationPoint.create("${listPointIndex[pointIndex]}${groupIndex + 1}", point.x, point.y) }
      val calibration = Calibration.create(groupIndex.toString(), points)
      CalibrationGroup.create(calibration)
    }
  }
}

private fun getPercentRect(
  totalCount: Int,
  columnCount: Int,
): List<List<Offset>> {
  require(totalCount > 0 && columnCount > 0)
  val rowCount = (totalCount + columnCount - 1) / columnCount
  val cellWidth = 1f / (2 * columnCount + 1)
  val cellHeight = 1f / (2 * rowCount + 1)
  val result = mutableListOf<List<Offset>>()
  var index = 0
  for (row in 0 until rowCount) {
    val remaining = totalCount - index
    val currentRowCount = minOf(columnCount, remaining)
    for (col in 0 until currentRowCount) {
      val left = cellWidth + col * 2 * cellWidth
      val top = (2 * row + 1) * cellHeight
      val right = left + cellWidth
      val bottom = top + cellHeight
      val rect = listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(right, bottom),
        Offset(left, bottom),
      )
      result.add(rect)
      index++
      if (index >= totalCount) break
    }
  }
  return result
}

@Preview
@Composable
private fun Preview() {
  Content()
}