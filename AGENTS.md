# 项目约定

## 依赖管理

- `:lib` 模块中的 Compose 依赖统一使用 `implementation` 声明，不改为 `api`；接入方本身必然是 Compose 项目并自行管理 Compose 版本，本库不对外传递 Compose 依赖。

## 内部设计约束

- 公开模型（`CalibrationGroup`、`Calibration`、`CalibrationPoint`）保持纯 `@Immutable` 值类型，不挂载绘制闭包或可变状态引用。
- 拖拽过程中的高频坐标变更仅更新 `CalibrationState` 内部的 `RenderPoint` Snapshot 状态以驱动 `Canvas` 局部重绘，仅在手势结束或中断时提交不可变快照。
