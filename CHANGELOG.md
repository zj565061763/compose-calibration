# Changelog

## 1.2.0

### ⚠️ Breaking Changes

- `Calibration` 与 `CalibrationPoint` 由 `interface` 改为不可变 `data class`，移除 `Calibration.overridePoints`
- `CalibrationState` 对外提供的标定组改为不可变快照，仅在拖拽手势结束或中断时更新，此前读取的快照不会被后续拖拽修改
- 移除 `Calibration.withDrawer`，自定义绘制器改为通过 `CalibrationView` 的 `getDrawer` 参数提供
- `rememberCalibrationState` 的 `groups` 参数改名为 `initialGroups`（默认空列表）并新增 `key` 参数：`initialGroups` 仅在首次初始化或 `key` 变化时生效，后续更新需调用 `CalibrationState.setGroups`

### ✨ Improvements

- `rememberCalibrationState` 支持导航返回、配置变更与进程重建后的状态保存与恢复，并在恢复时校验 `key`（支持基本类型、`String`、`Enum`、`Parcelable` 及元素可保存的 `Pair`、`Triple`、`List`）一致性，避免离开组合树期间切换 `key` 后误恢复旧状态
- 公开 `CalibrationState(initialGroups)` 构造函数与 `CalibrationState.Saver`，支持在 Composable 外部创建和保存状态
- `CalibrationState` 新增 `groups`（替代已废弃的 `getCurrentGroups()`）与 `selectedGroup` 属性，以及 `setGroups`、`clearSelection` 方法
- `CalibrationState.setGroups` 在标定项 `id` 结构不变时保留选中态，结构变化时自动清除选中态
- `Calibration.create`、`CalibrationState` 初始化及 `setGroups` 自动将所有顶点坐标限制在 `[0f, 1f]` 范围内
- `CalibrationView` 新增 `enabled` 参数控制是否允许触摸拖拽标定点
- `CalibrationView` 新增 `onValueChange` 回调，在拖拽手势结束或中断且坐标发生实际变化时回调最新标定组列表
- 公开 `CalibrationDrawer.Default`，内置绘制器支持结构相等比较
- 新增 `List<CalibrationGroup>.findCalibration(id)` 与 `findPoints(id)` 扩展函数
- 标定点触摸热区由方形改为圆形，热区重叠时优先命中距离手指最近的顶点，距离相等时优先命中上层顶点

### 🐛 Bug Fixes

- 手指拖出画布边界后再往回拉时，顶点之前会提前向画布内偏移并与手指脱节；现在按按下时的起始坐标与总位移计算，回到画布内时保持与手指对齐
- 多指触摸期间主手指抬起切换到另一根手指继续拖拽时，顶点之前会发生坐标跳变；现在换指后按增量平滑移动
- 标定点靠近画布边缘时，默认绘制的点名称之前可能超出画布被裁切；现在会自动翻转避开标定点并限制在画布可视范围内
- 自定义 `CalibrationConfig.pointNameStyle` 未显式设置颜色（如仅设置字号）时，点名称之前不跟随 `pointColor`；现在默认回退使用 `pointColor`
- `CalibrationPoint.toComposeOffset` 传入 `Size.Unspecified` 时之前会返回 `NaN` 坐标；现在统一返回 `Offset.Zero`

### Migration

- `rememberCalibrationState(groups = groups)` 改为 `rememberCalibrationState(initialGroups = groups, key = slotKey)`，后续主动更新或重置标定组时调用 `state.setGroups(groups)`
- `calibration.withDrawer(customDrawer)` 改为在 `CalibrationView` 中传入 `getDrawer = { if (it.id == ...) customDrawer else CalibrationDrawer.Default }`
- `calibration.overridePoints(newPoints)` 改为 `calibration.copy(points = newPoints)`
- `state.getCurrentGroups()` 改为读取 `state.groups`，或通过 `CalibrationView` 的 `onValueChange` 接收更新后的标定组列表
