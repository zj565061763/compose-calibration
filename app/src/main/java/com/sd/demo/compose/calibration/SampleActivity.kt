package com.sd.demo.compose.calibration

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sd.demo.compose.calibration.theme.AppTheme
import com.sd.lib.compose.calibration.Calibration
import com.sd.lib.compose.calibration.CalibrationConfig
import com.sd.lib.compose.calibration.CalibrationDrawer
import com.sd.lib.compose.calibration.CalibrationGroup
import com.sd.lib.compose.calibration.CalibrationPoint
import com.sd.lib.compose.calibration.CalibrationPointNamePosition
import com.sd.lib.compose.calibration.CalibrationState
import com.sd.lib.compose.calibration.CalibrationView
import com.sd.lib.compose.calibration.rememberCalibrationState
import com.sd.lib.compose.calibration.toComposeOffset

class SampleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme {
        SampleRoute()
      }
    }
  }
}

@Composable
private fun SampleRoute(
  modifier: Modifier = Modifier,
) {
  var selectedSlot by rememberSaveable { mutableStateOf(SampleSlot.SlotA) }
  var slotGroupsMap by remember {
    mutableStateOf(
      mapOf(
        SampleSlot.SlotA to createSampleGroups(totalCount = 8, columnCount = 4),
        SampleSlot.SlotB to createSampleGroups(totalCount = 4, columnCount = 2),
      ),
    )
  }

  val customDrawer = remember {
    CalibrationDrawer.create(
      lineDrawer = CalibrationDrawer.defaultLineDrawer(closeLines = false),
      pointDrawer = CustomPointDrawer,
      pointNameDrawer = CalibrationDrawer.defaultPointNameDrawer(CalibrationPointNamePosition.CenterEnd),
    )
  }

  val calibrationState = rememberCalibrationState(
    initialGroups = slotGroupsMap.getValue(selectedSlot),
    key = selectedSlot,
  )

  Content(
    modifier = modifier,
    selectedSlot = selectedSlot,
    state = calibrationState,
    onSelectSlot = { slot ->
      selectedSlot = slot
    },
    onClickReset = {
      val defaultGroups = when (selectedSlot) {
        SampleSlot.SlotA -> createSampleGroups(totalCount = 8, columnCount = 4)
        SampleSlot.SlotB -> createSampleGroups(totalCount = 4, columnCount = 2)
      }
      slotGroupsMap += selectedSlot to defaultGroups
      calibrationState.setGroups(defaultGroups)
    },
    onValueChange = { updatedGroups ->
      slotGroupsMap += selectedSlot to updatedGroups
    },
    getDrawer = { calibration ->
      if (calibration.id == CUSTOM_CALIBRATION_ID) customDrawer else CalibrationDrawer.Default
    },
  )
}

@Composable
private fun Content(
  modifier: Modifier = Modifier,
  selectedSlot: SampleSlot,
  state: CalibrationState,
  onSelectSlot: (SampleSlot) -> Unit,
  onClickReset: () -> Unit,
  onValueChange: (List<CalibrationGroup>) -> Unit,
  getDrawer: (Calibration) -> CalibrationDrawer,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SampleSlot.entries.forEach { slot ->
        FilterChip(
          selected = slot == selectedSlot,
          onClick = { onSelectSlot(slot) },
          label = { Text(text = slot.name) },
        )
      }
      Box(modifier = Modifier.weight(1f))
      Button(onClick = onClickReset) {
        Text(text = "Reset")
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      contentAlignment = Alignment.Center,
    ) {
      CalibrationView(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .background(Color.Black),
        state = state,
        onValueChange = onValueChange,
        getDrawer = getDrawer,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
  AppTheme {
    SampleRoute()
  }
}

private const val CUSTOM_CALIBRATION_ID = "custom"

private val listPointIndex = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N")

private enum class SampleSlot {
  SlotA,
  SlotB,
}

private data object CustomPointDrawer : CalibrationDrawer {
  override fun DrawScope.draw(
    calibration: Calibration,
    config: CalibrationConfig,
    textMeasurer: TextMeasurer,
  ) {
    calibration.points.forEach { point ->
      val pointSizePx = config.pointSize.toPx()
      drawRect(
        color = config.pointColor,
        topLeft = point.toComposeOffset(size) - Offset(pointSizePx / 2f, pointSizePx / 2f),
        size = Size(pointSizePx, pointSizePx),
      )
    }
  }
}

private fun createSampleGroups(
  totalCount: Int,
  columnCount: Int,
): List<CalibrationGroup> {
  val customCalibration = Calibration.create(
    id = CUSTOM_CALIBRATION_ID,
    points = listOf(
      CalibrationPoint.create("${listPointIndex[0]}1", 0.5f, 0.5f),
      CalibrationPoint.create("${listPointIndex[1]}1", 0.5f, 0.6f),
      CalibrationPoint.create("${listPointIndex[2]}1", 0.5f, 0.7f),
    ),
  )
  val rectGroups = getPercentRect(totalCount = totalCount, columnCount = columnCount).mapIndexed { groupIndex, groupItem ->
    val points = groupItem.mapIndexed { pointIndex, point ->
      CalibrationPoint.create("${listPointIndex[pointIndex]}${groupIndex + 1}", point.x, point.y)
    }
    CalibrationGroup.create(Calibration.create(groupIndex.toString(), points))
  }
  return rectGroups + CalibrationGroup.create(customCalibration)
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
