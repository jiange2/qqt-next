# 全 App 锁定竖屏，不再跟随系统自动旋转

应用此前未声明 `screenOrientation`，默认跟随系统自动旋转设置横竖屏切换；但 UI 与交互（播放器双页 Pager、下拉返回手势、底部导航、功能图标行）均按竖屏设计，横屏形态从未设计过。故决定在 MainActivity 声明 `android:screenOrientation="portrait"`：即使系统开启自动旋转，App 全部页面也不再旋转。

## Considered Options

- **`userPortrait`**：竖屏但尊重 180° 倒置，手机音乐场景无收益。否决。
- **`nosensor`**：完全无视传感器，在本场景与 `portrait` 无实际差别，取更常规的 `portrait`。否决。
- **`portrait`（采纳）**：单 Activity 一行声明覆盖全部页面，标准做法。

## Consequences

- targetSdk 35 下分屏/自由窗口模式中系统会忽略 `screenOrientation`，窗口可被拖成横向比例，由系统 letterbox 黑边兜底——接受此行为，不额外声明 `resizeableActivity="false"` 禁用多窗口（「边听边用」的分屏场景价值大于强制竖屏）。
- 锁定是「不作为」式决策：代码中没有任何旋转相关实现可查。未来维护者请勿将 manifest 中这一属性当作遗漏而移除。
