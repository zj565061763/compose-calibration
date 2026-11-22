package com.sd.lib.compose.calibration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot

/**
 * 创建并记住支持导航返回、配置变更与进程重建保活的[CalibrationState]
 *
 * @param initialGroups 初始标定组列表，变化时会重置状态
 * @param key 附加标识（例如当前机位标识），变化时会重置状态并隔离存档
 */
@Composable
fun rememberCalibrationState(
  initialGroups: List<CalibrationGroup> = emptyList(),
  key: Any? = null,
): CalibrationState {
  val normalizedInitial = remember(initialGroups) { initialGroups.normalized() }
  val saver = remember(normalizedInitial, key) {
    createContextualSaver(
      expectedInitialGroups = normalizedInitial,
      expectedKey = key,
    )
  }
  return rememberSaveable(normalizedInitial, key, saver = saver) {
    CalibrationState(initialGroups = normalizedInitial)
  }
}

/**
 * 标定画布状态持有者，对外提供坐标归一化后的不可变标定组快照
 *
 * @param initialGroups 初始标定组列表
 */
@Stable
class CalibrationState(
  initialGroups: List<CalibrationGroup> = emptyList(),
) {
  private val _groupsState = mutableStateOf<List<CalibrationGroup>>(emptyList())
  private val _renderGroupsState = mutableStateOf<List<RenderGroup>>(emptyList())
  private val _selectedGroupIndexState = mutableIntStateOf(-1)
  private val _generationState = mutableIntStateOf(0)
  private val _hasUncommittedDragState = mutableStateOf(false)

  /** 当前标定组不可变快照，坐标范围为 `[0f, 1f]` */
  val groups: List<CalibrationGroup>
    get() {
      return _groupsState.value
    }

  /** 当前选中的标定组不可变快照 */
  val selectedGroup: CalibrationGroup?
    get() {
      return _groupsState.value.getOrNull(_selectedGroupIndexState.intValue)
    }

  internal val renderGroups: List<RenderGroup>
    get() {
      return _renderGroupsState.value
    }

  internal val selectedGroupIndex: Int
    get() {
      return _selectedGroupIndexState.intValue
    }

  internal val generation: Int
    get() {
      return _generationState.intValue
    }

  internal val hasUncommittedDrag: Boolean
    get() {
      return _hasUncommittedDragState.value
    }

  /** 获取当前标定组不可变快照 */
  @Deprecated("请直接读取 groups 属性", ReplaceWith("groups"))
  fun getCurrentGroups(): List<CalibrationGroup> {
    return groups
  }

  /** 更新标定组列表，自动将坐标限制在 `[0f, 1f]` 范围内 */
  fun setGroups(groups: List<CalibrationGroup>) {
    val normalized = groups.normalized()
    val hasUncommittedDrag = Snapshot.withoutReadObservation { _hasUncommittedDragState.value }
    val currentGroups = Snapshot.withoutReadObservation { _groupsState.value }
    if (!hasUncommittedDrag && normalized == currentGroups) {
      return
    }
    val previousIndex = Snapshot.withoutReadObservation { _selectedGroupIndexState.intValue }
    val previousSelectedIds = currentGroups.getOrNull(previousIndex)?.calibrationIds()?.takeIf { it.isNotEmpty() }
    _hasUncommittedDragState.value = false
    _groupsState.value = normalized
    _renderGroupsState.value = normalized.map { it.toRenderGroup() }
    _selectedGroupIndexState.intValue = when {
      previousSelectedIds == null -> -1
      normalized.getOrNull(previousIndex)?.calibrationIds() == previousSelectedIds -> previousIndex
      else -> normalized.indexOfFirst { it.calibrationIds() == previousSelectedIds }
    }
    _generationState.intValue++
  }

  /** 清除当前选中的标定组 */
  fun clearSelection() {
    _selectedGroupIndexState.intValue = -1
  }

  internal fun selectGroup(index: Int) {
    _selectedGroupIndexState.intValue = if (index in _renderGroupsState.value.indices) index else -1
  }

  internal fun markDirty() {
    _hasUncommittedDragState.value = true
  }

  /** 提交指定[expectedGeneration]的手势修改，坐标有实际变化时更新[groups]并返回新快照 */
  internal fun commitDrag(expectedGeneration: Int): List<CalibrationGroup>? {
    if (expectedGeneration != _generationState.intValue) {
      return null
    }
    _hasUncommittedDragState.value = false
    val updated = Snapshot.withoutReadObservation {
      _renderGroupsState.value.map { it.toCalibrationGroup() }
    }
    if (updated == _groupsState.value) {
      return null
    }
    _groupsState.value = updated
    return updated
  }

  init {
    val normalized = initialGroups.normalized()
    _groupsState.value = normalized
    _renderGroupsState.value = normalized.map { it.toRenderGroup() }
  }

  companion object {
    /** 用于保存和恢复[CalibrationState]的通用[Saver] */
    val Saver: Saver<CalibrationState, Any> = Saver(
      save = { state ->
        val currentSnapshot = Snapshot.withoutReadObservation {
          state.renderGroups.map { it.toCalibrationGroup() }
        }
        arrayListOf<Any>(
          serializeGroups(currentSnapshot),
          Snapshot.withoutReadObservation { state.selectedGroupIndex },
        )
      },
      restore = { raw ->
        val list = raw as? List<*> ?: return@Saver null
        if (list.size != 2) return@Saver null
        val restoredGroups = deserializeGroups(list[0]) ?: return@Saver null
        val restoredSelectedIndex = list[1] as? Int ?: -1
        CalibrationState(initialGroups = restoredGroups).also { state ->
          state.selectGroup(restoredSelectedIndex)
        }
      },
    )
  }
}

internal class RenderGroup(
  val calibrations: List<RenderCalibration>,
) {
  fun toCalibrationGroup(): CalibrationGroup {
    return CalibrationGroup(
      calibrations = calibrations.map { it.toCalibration() },
    )
  }
}

internal class RenderCalibration(
  val id: String,
  val points: List<RenderPoint>,
) {
  fun toCalibration(): Calibration {
    return Calibration(
      id = id,
      points = points.map { it.toCalibrationPoint() },
    )
  }
}

internal class RenderPoint(
  val name: String,
  x: Float,
  y: Float,
) {
  private val _xState = mutableFloatStateOf(x.coerceNormalized())
  private val _yState = mutableFloatStateOf(y.coerceNormalized())

  val x: Float
    get() {
      return _xState.floatValue
    }

  val y: Float
    get() {
      return _yState.floatValue
    }

  fun update(x: Float, y: Float) {
    _xState.floatValue = x.coerceNormalized()
    _yState.floatValue = y.coerceNormalized()
  }

  fun toCalibrationPoint(): CalibrationPoint {
    return CalibrationPoint(
      name = name,
      x = x,
      y = y,
    )
  }
}

/** 创建校验初始基准与[expectedKey]的[Saver]，防止跨机位或默认值变更后误恢复旧存档 */
internal fun createContextualSaver(
  expectedInitialGroups: List<CalibrationGroup>,
  expectedKey: Any?,
): Saver<CalibrationState, Any> {
  val expectedKeyToken = expectedKey.toSaveableKeyToken()
  return Saver(
    save = { state ->
      val currentSnapshot = Snapshot.withoutReadObservation {
        state.renderGroups.map { it.toCalibrationGroup() }
      }
      arrayListOf<Any?>(
        serializeGroups(expectedInitialGroups),
        serializeGroups(currentSnapshot),
        Snapshot.withoutReadObservation { state.selectedGroupIndex },
        expectedKeyToken,
      )
    },
    restore = { raw ->
      val list = raw as? List<*> ?: return@Saver null
      if (list.size != 4) return@Saver null
      val savedInitialGroups = deserializeGroups(list[0]) ?: return@Saver null
      val savedCurrentGroups = deserializeGroups(list[1]) ?: return@Saver null
      val savedSelectedIndex = list[2] as? Int ?: -1
      val savedKeyToken = if (list[3] == null) {
        null
      } else {
        list[3] as? String ?: return@Saver null
      }
      if (savedInitialGroups != expectedInitialGroups || savedKeyToken != expectedKeyToken) {
        return@Saver null
      }
      CalibrationState(initialGroups = savedCurrentGroups).also { state ->
        state.selectGroup(savedSelectedIndex)
      }
    },
  )
}

private fun CalibrationGroup.calibrationIds(): List<String> {
  return calibrations.map { it.id }
}

private fun CalibrationGroup.toRenderGroup(): RenderGroup {
  return RenderGroup(
    calibrations = calibrations.map { it.toRenderCalibration() },
  )
}

private fun Calibration.toRenderCalibration(): RenderCalibration {
  return RenderCalibration(
    id = id,
    points = points.map { it.toRenderPoint() },
  )
}

private fun CalibrationPoint.toRenderPoint(): RenderPoint {
  return RenderPoint(
    name = name,
    x = x,
    y = y,
  )
}

private fun serializeGroups(groups: List<CalibrationGroup>): ArrayList<Any> {
  return ArrayList<Any>(groups.size).also { groupList ->
    for (group in groups) {
      val calibrationList = ArrayList<Any>(group.calibrations.size)
      for (calibration in group.calibrations) {
        val pointList = ArrayList<Any>(calibration.points.size)
        for (point in calibration.points) {
          pointList.add(arrayListOf<Any>(point.name, point.x, point.y))
        }
        calibrationList.add(arrayListOf<Any>(calibration.id, pointList))
      }
      groupList.add(calibrationList)
    }
  }
}

private fun deserializeGroups(raw: Any?): List<CalibrationGroup>? {
  val rawGroups = raw as? List<*> ?: return null
  val groups = ArrayList<CalibrationGroup>(rawGroups.size)
  for (rawGroup in rawGroups) {
    val rawCalibrations = rawGroup as? List<*> ?: return null
    val calibrations = ArrayList<Calibration>(rawCalibrations.size)
    for (rawCalibration in rawCalibrations) {
      val calibrationParts = rawCalibration as? List<*> ?: return null
      if (calibrationParts.size != 2) return null
      val id = calibrationParts[0] as? String ?: return null
      val rawPoints = calibrationParts[1] as? List<*> ?: return null
      val points = ArrayList<CalibrationPoint>(rawPoints.size)
      for (rawPoint in rawPoints) {
        val pointParts = rawPoint as? List<*> ?: return null
        if (pointParts.size != 3) return null
        val name = pointParts[0] as? String ?: return null
        val x = (pointParts[1] as? Number)?.toFloat() ?: return null
        val y = (pointParts[2] as? Number)?.toFloat() ?: return null
        points.add(CalibrationPoint.create(name = name, x = x, y = y))
      }
      calibrations.add(Calibration(id = id, points = points))
    }
    groups.add(CalibrationGroup(calibrations = calibrations))
  }
  return groups
}

private fun Any?.toSaveableKeyToken(): String? {
  if (this == null) return null
  return when (this) {
    is Enum<*> -> "${javaClass.name}:$name"
    else -> "${javaClass.name}:$this"
  }
}
