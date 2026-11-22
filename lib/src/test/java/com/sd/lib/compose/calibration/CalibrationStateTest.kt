package com.sd.lib.compose.calibration

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
          CalibrationPoint(name = "P2", x = 0.3f, y = 2.5f),
        ),
      ),
    )

    val state = CalibrationState(initialGroups = listOf(rawGroup))
    val points = state.groups.findPoints("roi")

    assertEquals(
      listOf(
        CalibrationPoint(name = "P1", x = 0f, y = 1f),
        CalibrationPoint(name = "P2", x = 0.3f, y = 1f),
      ),
      points,
    )
  }

  @Test
  fun dragUpdatesSnapshotOnlyOnCommitAndKeepsPreviousSnapshotImmutable() {
    val initial = createRectGroups(id = "roi", x = 0.2f, y = 0.3f)
    val state = CalibrationState(initialGroups = initial)
    val snapshotBeforeDrag = state.groups

    val renderGroups = state.renderGroups
    renderGroups.first().calibrations.first().points.first().update(x = 0.6f, y = 0.7f)

    // 提交前外部快照保持不变
    assertEquals(0.2f, state.groups.findPoints("roi")!!.first().x, 0f)

    val committed = state.commitDrag(expectedRenderGroups = renderGroups)
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

    val renderGroups = state.renderGroups
    renderGroups.first().calibrations.first().points.first().update(x = -0.3f, y = -0.2f)

    val committed = state.commitDrag(expectedRenderGroups = renderGroups)
    assertNull(committed)
  }

  @Test
  fun setGroupsDuringActiveDragInvalidatesStaleDragCommit() {
    val slotAGroups = createRectGroups(id = "slot_a", x = 0.1f, y = 0.1f)
    val slotBGroups = createRectGroups(id = "slot_b", x = 0.5f, y = 0.5f)
    val state = CalibrationState(initialGroups = slotAGroups)

    val staleRenderGroups = state.renderGroups
    staleRenderGroups.first().calibrations.first().points.first().update(x = 0.8f, y = 0.8f)

    // 拖拽途中外部切换到新机位
    state.setGroups(slotBGroups)

    // 旧手势在 finally 中提交时应被忽略，不覆盖新机位数据
    val staleCommit = state.commitDrag(expectedRenderGroups = staleRenderGroups)
    assertNull(staleCommit)
    assertEquals(slotBGroups, state.groups)
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

    // 非法尺寸（Size.Unspecified / Size.Zero）不会误修改坐标
    point.updateFromStart(startX = 0.9f, startY = 0.5f, totalOffset = Offset(100f, 100f), containerSize = Size.Unspecified)
    assertEquals(0.9f, point.x, 0f)
    assertEquals(0.5f, point.y, 0f)
  }

  @Test
  fun toComposeOffsetHandlesValidAndInvalidSizes() {
    val point = CalibrationPoint.create(name = "A", x = 0.25f, y = 0.5f)
    assertEquals(
      Offset(260f, 395f),
      point.toComposeOffset(
        containerSize = Size(1000f, 800f),
        appendX = 10f,
        appendY = -5f,
      ),
    )
    assertEquals(Offset.Zero, point.toComposeOffset(Size.Zero))
    assertEquals(Offset.Zero, point.toComposeOffset(Size.Unspecified))
    assertEquals(Offset.Zero, point.toComposeOffset(Size(-100f, 200f)))
  }

  @Test
  fun resolvePointNameTopLeftAvoidsPointAndStaysInsideCanvasBounds() {
    val canvasSize = Size(200f, 100f)
    val textSize = IntSize(40, 20)
    val pointRadius = 8f

    // TopEnd 贴近顶部与右边缘：翻到点下方（y = 0 + 8 = 8），x 夹紧到 200 - 40 = 160，不压住圆点
    assertEquals(
      Offset(160f, 8f),
      resolvePointNameTopLeft(
        pointCenter = Offset(200f, 0f),
        pointRadius = pointRadius,
        textSize = textSize,
        canvasSize = canvasSize,
        position = CalibrationPointNamePosition.TopEnd,
      ),
    )

    // BottomEnd 贴近底部边缘：翻到点上方（y = 100 - 8 - 20 = 72），不压住圆点
    assertEquals(
      Offset(100f, 72f),
      resolvePointNameTopLeft(
        pointCenter = Offset(100f, 100f),
        pointRadius = pointRadius,
        textSize = textSize,
        canvasSize = canvasSize,
        position = CalibrationPointNamePosition.BottomEnd,
      ),
    )

    // CenterEnd 贴近右侧边缘：翻到点左侧（gapX = 8 + 5 = 13，x = 200 - 13 - 40 = 147），不压住圆点
    assertEquals(
      Offset(147f, 40f),
      resolvePointNameTopLeft(
        pointCenter = Offset(200f, 50f),
        pointRadius = pointRadius,
        textSize = textSize,
        canvasSize = canvasSize,
        position = CalibrationPointNamePosition.CenterEnd,
      ),
    )
  }

  @Test
  fun findCalibrationAndContainsCalibrationResolveExpectedItems() {
    val c1 = Calibration.create("c1", listOf(CalibrationPoint.create("A", 0.1f, 0.2f)))
    val c2First = Calibration.create("c2", listOf(CalibrationPoint.create("B", 0.3f, 0.4f)))
    val c2Second = Calibration.create("c2", listOf(CalibrationPoint.create("C", 0.5f, 0.6f)))
    val group0 = CalibrationGroup.create(c1, c2First)
    val group1 = CalibrationGroup.create(c2Second)
    val groups = listOf(group0, group1)

    assertTrue(group0.containsCalibration("c1"))
    assertFalse(group0.containsCalibration("missing"))
    assertEquals(c2First, groups.findCalibration("c2"))
    assertEquals(c2First.points, groups.findPoints("c2"))
    assertNull(groups.findCalibration("missing"))
    assertNull(groups.findPoints("missing"))
  }

  @Test
  fun findNearestTouchedPointPrefersClosestPointAndBreaksTiesByTopmostOrder() {
    val containerSize = Size(1000f, 1000f)
    val density = Density(1f)

    // 1. 热区重叠时优先命中距离手指更近的下层顶点
    val closePointsCalibration = RenderCalibration(
      id = "roi",
      points = listOf(
        RenderPoint(name = "Bottom", x = 0.50f, y = 0.50f),
        RenderPoint(name = "Top", x = 0.52f, y = 0.50f),
      ),
    )
    val nearBottom = closePointsCalibration.findNearestTouchedPoint(
      groupIndex = 0,
      touched = Offset(502f, 500f),
      containerSize = containerSize,
      touchedSize = 64f,
    )
    assertEquals("Bottom", nearBottom?.point?.name)

    // 2. 坐标完全重合（距离相等）时优先命中上层后绘制的顶点
    val coincidedCalibration = RenderCalibration(
      id = "roi",
      points = listOf(
        RenderPoint(name = "Bottom", x = 0.50f, y = 0.50f),
        RenderPoint(name = "Top", x = 0.50f, y = 0.50f),
      ),
    )
    val onTie = coincidedCalibration.findNearestTouchedPoint(
      groupIndex = 0,
      touched = Offset(500f, 500f),
      containerSize = containerSize,
      touchedSize = 32f,
    )
    assertEquals("Top", onTie?.point?.name)

    // 3. 圆形热区剔除方形四角超出半径的触摸点（半径 20px，四角偏移 (18, 18) 距离约 25.45px）
    assertNull(
      coincidedCalibration.findNearestTouchedPoint(
        groupIndex = 0,
        touched = Offset(518f, 518f),
        containerSize = containerSize,
        touchedSize = 40f,
      ),
    )

    // 4. 跨标定组：未选中组顶点明显更近时优先命中未选中组，完全重合时保留当前选中组
    val group0 = RenderGroup(listOf(RenderCalibration("c0", listOf(RenderPoint("SelectedFar", 0.52f, 0.50f)))))
    val group1 = RenderGroup(listOf(RenderCalibration("c1", listOf(RenderPoint("UnselectedNear", 0.50f, 0.50f)))))
    val crossGroupHit = with(density) {
      findNearestTouchedPoint(
        groups = listOf(group0, group1),
        selectedGroupIndex = 0,
        touched = Offset(501f, 500f),
        containerSize = containerSize,
        getConfig = { CalibrationConfig(pointTouchedSize = androidx.compose.ui.unit.Dp(64f)) },
      )
    }
    assertEquals(1, crossGroupHit?.groupIndex)
    assertEquals("UnselectedNear", crossGroupHit?.point?.name)

    val overlapGroup0 = RenderGroup(listOf(RenderCalibration("c0", listOf(RenderPoint("SelectedOverlap", 0.50f, 0.50f)))))
    val overlapGroup1 = RenderGroup(listOf(RenderCalibration("c1", listOf(RenderPoint("UnselectedOverlap", 0.50f, 0.50f)))))
    val overlapSelectedHit = with(density) {
      findNearestTouchedPoint(
        groups = listOf(overlapGroup0, overlapGroup1),
        selectedGroupIndex = 0,
        touched = Offset(500f, 500f),
        containerSize = containerSize,
        getConfig = { CalibrationConfig(pointTouchedSize = androidx.compose.ui.unit.Dp(64f)) },
      )
    }
    assertEquals(0, overlapSelectedHit?.groupIndex)
    assertEquals("SelectedOverlap", overlapSelectedHit?.point?.name)
  }

  @Test
  fun saverRestoresDraggedGroupsAndSelectedIndex() {
    val defaultGroups = createRectGroups(id = "roi", x = 0.2f, y = 0.3f)
    val state = CalibrationState(initialGroups = defaultGroups)
    state.selectGroup(0)
    val renderGroups = state.renderGroups
    renderGroups.first().calibrations.first().points.first().update(x = 0.75f, y = 0.85f)
    state.commitDrag(renderGroups)

    val saved = with(CalibrationState.Saver) {
      BundleCompatibleSaverScope.save(state)
    }
    assertNotNull(saved)
    assertTrue(BundleCompatibleSaverScope.canBeSaved(saved!!))

    val restored = CalibrationState.Saver.restore(saved)
    assertNotNull(restored)
    assertEquals(0.75f, restored!!.groups.findPoints("roi")!!.first().x, 0f)
    assertEquals(0.85f, restored.groups.findPoints("roi")!!.first().y, 0f)
    assertEquals(0, restored.selectedGroupIndex)
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

  @Test
  fun resolvePointNameColorFallsBackToPointColorWhenStyleColorUnspecified() {
    // 默认配置使用 pointColor
    assertEquals(
      Color.Green,
      resolvePointNameColor(CalibrationConfig(lineColor = Color.Green)),
    )

    // 自定义字号但未指定颜色时回退为 pointColor
    assertEquals(
      Color.Green,
      resolvePointNameColor(
        CalibrationConfig(
          lineColor = Color.Green,
          pointNameStyle = TextStyle(fontSize = 14.sp),
        ),
      ),
    )

    // 显式指定文本颜色时优先使用文本颜色
    assertEquals(
      Color.White,
      resolvePointNameColor(
        CalibrationConfig(
          lineColor = Color.Green,
          pointNameStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        ),
      ),
    )
  }

  @Test
  fun singlePointerDragStopsWhenPrimaryPointerLiftsWithoutHandingOffToSecondaryPointer() {
    val primaryId = PointerId(1L)
    val secondaryId = PointerId(2L)
    val downPos = Offset(800f, 200f)

    // 1. 主指针未过阈值即抬起时，即使副指针按住移动也不接管
    val cancelledScope = FakeAwaitPointerEventScope(
      initialEvent = PointerEvent(
        listOf(
          pointerChange(id = primaryId, pos = downPos, prevPos = downPos, pressed = true, prevPressed = false),
          pointerChange(id = secondaryId, pos = Offset(50f, 800f), prevPos = Offset(50f, 800f), pressed = true, prevPressed = false),
        ),
      ),
      upcomingEvents = listOf(
        PointerEvent(
          listOf(
            pointerChange(id = primaryId, pos = downPos, prevPos = downPos, pressed = false, prevPressed = true),
            pointerChange(id = secondaryId, pos = Offset(120f, 800f), prevPos = Offset(50f, 800f), pressed = true, prevPressed = true),
          ),
        ),
      ),
    )
    var slopResult: PointerInputChange? = null
    cancelledScope.runBlock {
      slopResult = awaitTouchSlopForPointer(pointerId = primaryId, downPosition = downPos)
    }
    assertNull(slopResult)

    // 2. 主指针跨越阈值并拖动后松手，副指针仍按住移动时立即停止且不跳位
    val dragScope = FakeAwaitPointerEventScope(
      initialEvent = PointerEvent(
        listOf(
          pointerChange(id = primaryId, pos = downPos, prevPos = downPos, pressed = true, prevPressed = false),
          pointerChange(id = secondaryId, pos = Offset(50f, 800f), prevPos = Offset(50f, 800f), pressed = true, prevPressed = false),
        ),
      ),
      upcomingEvents = listOf(
        PointerEvent(
          listOf(
            pointerChange(id = primaryId, pos = Offset(820f, 200f), prevPos = downPos, pressed = true, prevPressed = true),
            pointerChange(id = secondaryId, pos = Offset(50f, 800f), prevPos = Offset(50f, 800f), pressed = true, prevPressed = true),
          ),
        ),
        PointerEvent(
          listOf(
            pointerChange(id = primaryId, pos = Offset(850f, 210f), prevPos = Offset(820f, 200f), pressed = true, prevPressed = true),
            pointerChange(id = secondaryId, pos = Offset(55f, 805f), prevPos = Offset(50f, 800f), pressed = true, prevPressed = true),
          ),
        ),
        PointerEvent(
          listOf(
            pointerChange(id = primaryId, pos = Offset(850f, 210f), prevPos = Offset(850f, 210f), pressed = false, prevPressed = true),
            pointerChange(id = secondaryId, pos = Offset(60f, 810f), prevPos = Offset(55f, 805f), pressed = true, prevPressed = true),
          ),
        ),
        PointerEvent(
          listOf(
            pointerChange(id = secondaryId, pos = Offset(100f, 850f), prevPos = Offset(60f, 810f), pressed = true, prevPressed = true),
          ),
        ),
      ),
    )

    val recordedPositions = mutableListOf<Offset>()
    dragScope.runBlock {
      val slopChange = awaitTouchSlopForPointer(pointerId = primaryId, downPosition = downPos)
      assertNotNull(slopChange)
      recordedPositions.add(slopChange!!.position)
      dragPointer(primaryId) { change ->
        change.consume()
        recordedPositions.add(change.position)
      }
    }

    assertEquals(
      listOf(Offset(820f, 200f), Offset(850f, 210f)),
      recordedPositions,
    )
  }
}

private object BundleCompatibleSaverScope : SaverScope {
  override fun canBeSaved(value: Any): Boolean {
    return isBundleCompatible(value)
  }
}

private class FakeAwaitPointerEventScope(
  initialEvent: PointerEvent,
  upcomingEvents: List<PointerEvent>,
) : AwaitPointerEventScope {
  private val _events = ArrayDeque(upcomingEvents)
  private var _currentEvent: PointerEvent = initialEvent

  override val density: Float
    get() {
      return 1f
    }

  override val fontScale: Float
    get() {
      return 1f
    }

  override val size: IntSize
    get() {
      return IntSize(1000, 1000)
    }

  override val currentEvent: PointerEvent
    get() {
      return _currentEvent
    }

  override val viewConfiguration: ViewConfiguration = object : ViewConfiguration {
    override val longPressTimeoutMillis: Long
      get() {
        return 500L
      }

    override val doubleTapTimeoutMillis: Long
      get() {
        return 300L
      }

    override val doubleTapMinTimeMillis: Long
      get() {
        return 40L
      }

    override val touchSlop: Float
      get() {
        return 10f
      }
  }

  override suspend fun awaitPointerEvent(pass: PointerEventPass): PointerEvent {
    if (pass == PointerEventPass.Main && _events.isNotEmpty()) {
      _currentEvent = _events.removeFirst()
    }
    return _currentEvent
  }

  fun runBlock(block: suspend AwaitPointerEventScope.() -> Unit) {
    var failure: Throwable? = null
    block.startCoroutine(
      receiver = this,
      completion = object : Continuation<Unit> {
        override val context: kotlin.coroutines.CoroutineContext
          get() {
            return EmptyCoroutineContext
          }

        override fun resumeWith(result: Result<Unit>) {
          failure = result.exceptionOrNull()
        }
      },
    )
    failure?.also { throw it }
  }
}

private fun pointerChange(
  id: PointerId,
  pos: Offset,
  prevPos: Offset,
  pressed: Boolean,
  prevPressed: Boolean,
): PointerInputChange {
  return PointerInputChange(
    id = id,
    uptimeMillis = 100L,
    position = pos,
    pressed = pressed,
    previousUptimeMillis = 50L,
    previousPosition = prevPos,
    previousPressed = prevPressed,
    isInitiallyConsumed = false,
  )
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
