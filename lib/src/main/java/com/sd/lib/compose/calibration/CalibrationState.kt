package com.sd.lib.compose.calibration

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 创建并记住支持导航返回、配置变更与进程重建保活的[CalibrationState]
 *
 * @param initialGroups 初始标定组列表，仅在首次初始化或[key]变化时生效，后续更新请调用[CalibrationState.setGroups]
 * @param key 附加标识（如机位标识），变化时会重置状态
 */
@Composable
fun rememberCalibrationState(
  initialGroups: List<CalibrationGroup> = emptyList(),
  key: Any? = null,
): CalibrationState {
  val saver = remember(key) { createKeyedSaver(key) }
  return rememberSaveable(key, saver = saver) {
    CalibrationState(initialGroups = initialGroups)
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

  /** 获取当前标定组不可变快照 */
  @Deprecated("请直接读取 groups 属性", ReplaceWith("groups"))
  fun getCurrentGroups(): List<CalibrationGroup> {
    return groups
  }

  /** 更新标定组列表，自动将坐标限制在 `[0f, 1f]` 范围内 */
  fun setGroups(groups: List<CalibrationGroup>) {
    val normalized = groups.normalized()
    val currentGroups = _groupsState.value
    if (normalized == currentGroups) {
      return
    }
    val previousSelectedIds = currentGroups
      .getOrNull(_selectedGroupIndexState.intValue)
      ?.calibrationIds()
      ?.takeIf { it.isNotEmpty() }
    _groupsState.value = normalized
    _renderGroupsState.value = normalized.map { it.toRenderGroup() }
    _selectedGroupIndexState.intValue = if (previousSelectedIds == null) {
      -1
    } else {
      normalized.indexOfFirst { it.calibrationIds() == previousSelectedIds }
    }
  }

  /** 清除当前选中的标定组 */
  fun clearSelection() {
    _selectedGroupIndexState.intValue = -1
  }

  internal fun selectGroup(index: Int) {
    _selectedGroupIndexState.intValue = if (index in _renderGroupsState.value.indices) index else -1
  }

  /** 提交指定[expectedRenderGroups]的手势修改，坐标有实际变化时更新[groups]并返回新快照 */
  internal fun commitDrag(expectedRenderGroups: List<RenderGroup>): List<CalibrationGroup>? {
    val currentRenderGroups = _renderGroupsState.value
    if (expectedRenderGroups !== currentRenderGroups) {
      return null
    }
    val updated = currentRenderGroups.map { it.toCalibrationGroup() }
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
        arrayListOf<Any>(
          serializeGroups(state.groups),
          state.selectedGroupIndex,
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

/** 创建校验[key]一致性的[Saver]，防止离开组合树期间[key]变化导致旧状态误恢复 */
internal fun createKeyedSaver(key: Any?): Saver<CalibrationState, Any> {
  val encodedKey = encodeSaveableKey(key)
  val hasSaveableKey = encodedKey !== UnsaveableKey
  return Saver(
    save = { state ->
      val savedState = with(CalibrationState.Saver) { save(state) } ?: return@Saver null
      arrayListOf(
        hasSaveableKey,
        if (hasSaveableKey) encodedKey else null,
        savedState,
      )
    },
    restore = { raw ->
      val list = raw as? List<*> ?: return@Saver null
      if (list.size != 3) return@Saver null
      val hasSavedKey = list[0] as? Boolean ?: return@Saver null
      if (hasSavedKey && (!hasSaveableKey || list[1] != encodedKey)) return@Saver null
      val savedState = list[2] ?: return@Saver null
      CalibrationState.Saver.restore(savedState)
    },
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

private data object UnsaveableKey

private fun encodeSaveableKey(key: Any?): Any? {
  return when (key) {
    null,
    is String,
    is Int,
    is Long,
    is Short,
    is Byte,
    is Float,
    is Double,
    is Boolean,
    is Char,
    is Enum<*>,
    is Parcelable,
    -> key
    is Pair<*, *> -> encodeSaveableList(listOf(key.first, key.second))
    is Triple<*, *, *> -> encodeSaveableList(listOf(key.first, key.second, key.third))
    is List<*> -> encodeSaveableList(key)
    else -> UnsaveableKey
  }
}

private fun encodeSaveableList(items: List<*>): Any {
  val result = ArrayList<Any?>(items.size)
  for (item in items) {
    val encoded = encodeSaveableKey(item)
    if (encoded === UnsaveableKey) return UnsaveableKey
    result.add(encoded)
  }
  return result
}
