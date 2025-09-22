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
import com.sd.lib.compose.calibration.CalibrationDrawer
import com.sd.lib.compose.calibration.CalibrationGroup
import com.sd.lib.compose.calibration.CalibrationView
import com.sd.lib.compose.calibration.drawable
import com.sd.lib.compose.calibration.rememberCalibrationState

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
    val group1 = CalibrationGroup.create(Calibration.create(id = "1", points = getDefaultPoints("1", startX = 100f, startY = 100f)))
    val group2 = CalibrationGroup.create(
      Calibration
        .create(id = "2", points = getDefaultPoints("2", startX = 500f, startY = 500f))
        .drawable(CalibrationDrawer.create(pointDrawer = CustomPointDrawer()))
    )
    listOf(group1, group2)
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
      sourceSize = null,
//      sourceSize = Size(2000f, 2000f),
    )
  }
}

private val listPointIndex = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N")

private fun getDefaultPoints(
  group: String,
  startX: Float = 0f,
  startY: Float = 0f,
  delta: Float = 200f,
): List<Calibration.Point> {
  return listOf(
    Calibration.Point.create("${listPointIndex[0]}${group}", startX, startY),
    Calibration.Point.create("${listPointIndex[1]}${group}", startX + delta, startY),
    Calibration.Point.create("${listPointIndex[2]}${group}", startX + delta, startY + delta),
    Calibration.Point.create("${listPointIndex[3]}${group}", startX, startY + delta),
  )
}

private class CustomPointDrawer : CalibrationDrawer {
  override fun DrawScope.draw(
    calibration: Calibration,
    config: Calibration.Config,
    textMeasurer: TextMeasurer,
  ) {
    calibration.points.forEach { point ->
      val sizePX = config.pointSize.toPx()
      drawRect(
        color = config.pointColor,
        topLeft = Offset(point.x - sizePX / 2f, point.y - sizePX / 2f),
        size = Size(sizePX, sizePX),
      )
    }
  }
}

@Preview
@Composable
private fun Preview() {
  Content()
}