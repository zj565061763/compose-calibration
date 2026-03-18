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
    val calibration1 = Calibration.create(id = "1", points = getDefaultPoints("1", startX = 0.1f, startY = 0.1f))

    val calibration2 = run {
      val points = listOf(
        CalibrationPoint.create("${listPointIndex[0]}3", 0.5f, 0.5f),
        CalibrationPoint.create("${listPointIndex[1]}3", 0.5f, 0.6f),
        CalibrationPoint.create("${listPointIndex[2]}3", 0.5f, 0.7f),
      )
      Calibration.create(id = "3", points = points).withDrawer(
        CalibrationDrawer.create(
          lineDrawer = CalibrationDrawer.defaultLineDrawer(closeLines = false),
          pointDrawer = CustomPointDrawer(),
          pointNameDrawer = CalibrationDrawer.defaultPointNameDrawer(CalibrationPointNamePosition.CenterEnd),
        )
      )
    }

    listOf(
      CalibrationGroup.create(calibration1),
      CalibrationGroup.create(calibration2),
    )
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

private fun getDefaultPoints(
  group: String,
  startX: Float = 0f,
  startY: Float = 0f,
  deltaX: Float = 0.1f,
  deltaY: Float = 0.1f,
): List<CalibrationPoint> {
  return listOf(
    CalibrationPoint.create("${listPointIndex[0]}${group}", startX, startY),
    CalibrationPoint.create("${listPointIndex[1]}${group}", startX + deltaX, startY),
    CalibrationPoint.create("${listPointIndex[2]}${group}", startX + deltaX, startY + deltaY),
    CalibrationPoint.create("${listPointIndex[3]}${group}", startX, startY + deltaY),
  )
}

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

@Preview
@Composable
private fun Preview() {
  Content()
}