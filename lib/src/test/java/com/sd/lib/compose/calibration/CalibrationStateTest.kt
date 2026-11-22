package com.sd.lib.compose.calibration

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationStateTest {
  @Test
  fun normalizeCoordinatesOnCreateAndSetGroups() {
    val rawGroup = CalibrationGroup.create(
      Calibration(
        id = "roi",
        points = listOf(
          CalibrationPoint(name = "P1", x = -0.5f, y = 1.8f),
          CalibrationPoint(name = "P2", x = Float.NaN, y = -0.0f),
        ),
      ),
    )

    val state = CalibrationState(initialGroups = listOf(rawGroup))
    val points = state.groups.findPoints("roi")

    assertEquals(
      listOf(
        CalibrationPoint(name = "P1", x = 0f, y = 1f),
        CalibrationPoint(name = "P2", x = 0f, y = 0f),
      ),
      points,
    )
  }

  @Test
  fun dragUpdatesSnapshotOnlyOnCommitAndKeepsPreviousSnapshotImmutable() {
    val initial = createRectGroups(id = "roi", x = 0.2f, y = 0.3f)
    val state = CalibrationState(initialGroups = initial)
    val snapshotBeforeDrag = state.groups

    val gen = state.generation
    state.markDirty()
    state.renderGroups.first().calibrations.first().points.first().update(x = 0.6f, y = 0.7f)

    // 提交前外部快照保持不变
    assertEquals(0.2f, state.groups.findPoints("roi")!!.first().x, 0f)

    val committed = state.commitDrag(expectedGeneration = gen)
    assertNotNull(committed)
    assertEquals(0.6f, committed!!.findPoints("roi")!!.first().x, 0f)
    assertEquals(0.7f, committed.findPoints("roi")!!.first().y, 0f)
    assertEquals(committed, state.groups)

    // 之前持有的快照不会被后续拖拽篡改
    assertEquals(0.2f, snapshotBeforeDrag.findPoints("roi")!!.first().x, 0f)
  }

  @Test
  fun commitDragReturnsNullWhenCoordinatesUnchanged() {
    val initial = createRectGroups(id = "roi", x = 0f, y = 0f)
    val state = CalibrationState(initialGroups = initial)

    val gen = state.generation
    state.markDirty()
    state.renderGroups.first().calibrations.first().points.first().update(x = -0.3f, y = -0.2f)

    val committed = state.commitDrag(expectedGeneration = gen)
    assertNull(committed)
  }

  @Test
  fun setGroupsDuringActiveDragInvalidatesStaleDragCommit() {
    val slotAGroups = createRectGroups(id = "slot_a", x = 0.1f, y = 0.1f)
    val slotBGroups = createRectGroups(id = "slot_b", x = 0.5f, y = 0.5f)
    val state = CalibrationState(initialGroups = slotAGroups)

    val staleGen = state.generation
    state.markDirty()
    state.renderGroups.first().calibrations.first().points.first().update(x = 0.8f, y = 0.8f)

    // 拖拽途中外部切换到新机位
    state.setGroups(slotBGroups)

    // 旧手势在 finally 中提交时应被忽略，不覆盖新机位数据
    val staleCommit = state.commitDrag(expectedGeneration = staleGen)
    assertNull(staleCommit)
    assertEquals(slotBGroups, state.groups)
  }

  @Test
  fun setGroupsResetsUncommittedDragEvenWhenTargetEqualsInitial() {
    val defaultGroups = createRectGroups(id = "roi", x = 0.2f, y = 0.2f)
    val state = CalibrationState(initialGroups = defaultGroups)

    state.markDirty()
    state.renderGroups.first().calibrations.first().points.first().update(x = 0.9f, y = 0.9f)

    // 拖拽尚未提交时外部重置回默认点位
    state.setGroups(defaultGroups)

    assertEquals(0.2f, state.renderGroups.first().calibrations.first().points.first().x, 0f)
    assertEquals(defaultGroups, state.groups)
  }

  @Test
  fun setGroupsPreservesSelectionForMatchingIdsAndClearsOnSlotChange() {
    val g1 = CalibrationGroup.create(Calibration.create("c1", listOf(CalibrationPoint.create("A", 0.1f, 0.1f))))
    val g2 = CalibrationGroup.create(Calibration.create("c2", listOf(CalibrationPoint.create("B", 0.2f, 0.2f))))
    val state = CalibrationState(initialGroups = listOf(g1, g2))
    state.selectGroup(1)
    assertEquals("c2", state.selectedGroup?.calibrations?.single()?.id)

    // 恢复默认（相同标定 ID 结构，坐标变化）时保留选中态
    val resetG2 = CalibrationGroup.create(Calibration.create("c2", listOf(CalibrationPoint.create("B", 0.5f, 0.5f))))
    state.setGroups(listOf(g1, resetG2))
    assertEquals(1, state.selectedGroupIndex)
    assertEquals(0.5f, state.selectedGroup?.calibrations?.single()?.points?.single()?.x ?: -1f, 0f)

    // 切换到不包含 c2 的新机位时清除选中态
    val otherSlot = CalibrationGroup.create(Calibration.create("c3", listOf(CalibrationPoint.create("C", 0.3f, 0.3f))))
    state.setGroups(listOf(otherSlot))
    assertEquals(-1, state.selectedGroupIndex)
    assertNull(state.selectedGroup)
  }

  @Test
  fun setGroupsKeepsExactSelectedIndexWhenDuplicateCalibrationIdsExist() {
    val dup0 = CalibrationGroup.create(Calibration.create("roi", listOf(CalibrationPoint.create("A", 0.1f, 0.1f))))
    val dup1 = CalibrationGroup.create(Calibration.create("roi", listOf(CalibrationPoint.create("B", 0.2f, 0.2f))))
    val state = CalibrationState(initialGroups = listOf(dup0, dup1))
    state.selectGroup(1)

    val updatedDup1 = CalibrationGroup.create(Calibration.create("roi", listOf(CalibrationPoint.create("B", 0.6f, 0.6f))))
    state.setGroups(listOf(dup0, updatedDup1))

    assertEquals(1, state.selectedGroupIndex)
    assertEquals(0.6f, state.selectedGroup?.calibrations?.single()?.points?.single()?.x ?: -1f, 0f)
  }

  @Test
  fun updateFromStartHasZeroDriftAfterCrossingCanvasBoundaryAndReturning() {
    val point = RenderPoint(name = "A", x = 0.9f, y = 0.5f)
    val containerSize = Size(width = 1000f, height = 1000f)

    // 手指从 900px 拖到 1100px（超出右边界 100px），坐标限制在 1f
    point.updateFromStart(startX = 0.9f, startY = 0.5f, totalOffset = Offset(200f, 0f), containerSize = containerSize)
    assertEquals(1f, point.x, 0f)

    // 手指往回拉 50px 到 1050px（仍在右边界外），顶点保持在 1f 不提前回退
    point.updateFromStart(startX = 0.9f, startY = 0.5f, totalOffset = Offset(150f, 0f), containerSize = containerSize)
    assertEquals(1f, point.x, 0f)

    // 手指回到画布内 950px（相对按下点 +50px），顶点精确对齐到 0.95f
    point.updateFromStart(startX = 0.9f, startY = 0.5f, totalOffset = Offset(50f, 0f), containerSize = containerSize)
    assertEquals(0.95f, point.x, 0f)

    // 手指回到按下点 900px，顶点无误差恢复到 0.9f
    point.updateFromStart(startX = 0.9f, startY = 0.5f, totalOffset = Offset.Zero, containerSize = containerSize)
    assertEquals(0.9f, point.x, 0f)
  }

  @Test
  fun findTouchedPointPrefersTopmostOverlappingPoint() {
    val calibration = RenderCalibration(
      id = "roi",
      points = listOf(
        RenderPoint(name = "Bottom", x = 0.5f, y = 0.5f),
        RenderPoint(name = "Top", x = 0.5f, y = 0.5f),
      ),
    )

    val touched = calibration.findTouchedPoint(
      touched = Offset(500f, 500f),
      containerSize = Size(1000f, 1000f),
      touchedSize = 32f,
    )

    assertEquals("Top", touched?.name)
  }

  @Test
  fun contextualSaverRestoresWhenInitialAndKeyMatchAndDiscardsOnChange() {
    val defaultGroups = createRectGroups(id = "roi", x = 0.2f, y = 0.3f)
    val state = CalibrationState(initialGroups = defaultGroups)
    state.selectGroup(0)
    val gen = state.generation
    state.markDirty()
    state.renderGroups.first().calibrations.first().points.first().update(x = 0.75f, y = 0.85f)
    state.commitDrag(gen)

    val preFinishSaver = createContextualSaver(
      expectedInitialGroups = defaultGroups,
      expectedKey = TestSlot.PreFinish,
    )
    val saved = with(preFinishSaver) {
      BundleCompatibleSaverScope.save(state)
    }
    assertNotNull(saved)
    assertTrue(BundleCompatibleSaverScope.canBeSaved(saved!!))

    // 通用 Saver 同样可保存恢复且仅含 Bundle 兼容类型
    val standaloneSaved = with(CalibrationState.Saver) {
      BundleCompatibleSaverScope.save(state)
    }
    assertNotNull(standaloneSaved)
    assertTrue(BundleCompatibleSaverScope.canBeSaved(standaloneSaved!!))
    val standaloneRestored = CalibrationState.Saver.restore(standaloneSaved)
    assertNotNull(standaloneRestored)
    assertEquals(state.groups, standaloneRestored!!.groups)
    assertEquals(0, standaloneRestored.selectedGroupIndex)

    // 1. 常规压栈返回 / 重建（initialGroups 与 key 均未变）：恢复拖拽后的点位与选中项
    val restoredSameSlot = preFinishSaver.restore(saved)
    assertNotNull(restoredSameSlot)
    assertEquals(0.75f, restoredSameSlot!!.groups.findPoints("roi")!!.first().x, 0f)
    assertEquals(0.85f, restoredSameSlot.groups.findPoints("roi")!!.first().y, 0f)
    assertEquals(0, restoredSameSlot.selectedGroupIndex)

    // 2. 跨页面导致机位从 PreFinish 切到 Finish（即使默认坐标与 id 完全相同）：丢弃旧存档
    val finishSaver = createContextualSaver(
      expectedInitialGroups = defaultGroups,
      expectedKey = TestSlot.Finish,
    )
    assertNull(finishSaver.restore(saved))

    // 3. 子页面修改了初始默认点位：丢弃旧存档
    val updatedDefaultSaver = createContextualSaver(
      expectedInitialGroups = createRectGroups(id = "roi", x = 0.4f, y = 0.4f),
      expectedKey = TestSlot.PreFinish,
    )
    assertNull(updatedDefaultSaver.restore(saved))
  }

  @Test
  fun builtinDrawersSupportStructuralEquality() {
    assertEquals(CalibrationDrawer.create(), CalibrationDrawer.Default)
    assertEquals(
      CalibrationDrawer.defaultLineDrawer(closeLines = false),
      CalibrationDrawer.defaultLineDrawer(closeLines = false),
    )
    assertEquals(
      CalibrationDrawer.defaultPointNameDrawer(CalibrationPointNamePosition.CenterEnd),
      CalibrationDrawer.defaultPointNameDrawer(CalibrationPointNamePosition.CenterEnd),
    )
  }
}

private enum class TestSlot {
  PreFinish,
  Finish,
}

private object BundleCompatibleSaverScope : SaverScope {
  override fun canBeSaved(value: Any): Boolean {
    return isBundleCompatible(value)
  }
}

private fun createRectGroups(
  id: String,
  x: Float,
  y: Float,
): List<CalibrationGroup> {
  return listOf(
    CalibrationGroup.create(
      Calibration.create(
        id = id,
        points = listOf(
          CalibrationPoint.create("A", x, y),
          CalibrationPoint.create("B", 0.8f, y),
          CalibrationPoint.create("C", 0.8f, 0.8f),
          CalibrationPoint.create("D", x, 0.8f),
        ),
      ),
    ),
  )
}

private fun isBundleCompatible(value: Any?): Boolean {
  return when (value) {
    null, is String, is Int, is Float, is Double, is Long, is Boolean -> true
    is ArrayList<*> -> value.all { isBundleCompatible(it) }
    else -> false
  }
}
