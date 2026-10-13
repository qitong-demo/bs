# Prototype Instructions

Run the local server yourself and open the preview in the in-app browser. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

## Login Design Direction

- Public-service entry screens use a clean, restrained, trustworthy deep teal direction with neutral surfaces and clear semantic status colors.
- Prioritize role selection, credential entry, and the next action path; avoid large empty areas, generic gradients, and marketing-style copy in operational flows.
- Keep demo access discoverable but subordinate to the primary login action, and make service availability or authentication failures specific and actionable.

## Dashboard Design Direction（首页工作台）

沿用上面同一套方向，落到指标区与列表区：

- **语义色，不要装饰色。** 状态一律取 `--success / --warning / --danger / --info` 四个令牌。曾用紫色（`#7c3aed`）表示「诉求办结率」，与含义无关，已改用品牌色；指标卡左侧 3px 色条表示类别，不用整块彩色图标。
- **数值是主体。** 指标卡不再用 48px 圆形图标占位：图标降为 22px 方形标记，数值 30px 并用 `font-variant-numeric: tabular-nums`，保证多张卡纵向对齐。
- **不出现孤行。** 首页固定 6 张指标卡，网格用 `repeat(auto-fit, minmax(158px, 1fr))`；写死 5 列会留下第 6 张单独换行 + 一大片空白。
- **空状态要紧凑且有用。** 列表区 `el-empty` 统一 `:image-size="64"`；趋势图在数据全为 0 时不画坐标轴（会退化成 0~0 看起来像坏了），改为 `.chart-empty` 明确说明。
- **状态必须诚实。** 营业状态按当前时间实时判断，分三态：`营业中`（绿）/`已打烊`（橙）/`营业时间待核验`（灰）。**没有营业时间时绝不显示「营业中」**。筛选框文案要与它实际过滤的字段一致（「只显示已启用」而非「只看营业中」）。
- **未读角标与消息列表必须同源。** 两者都读 `stores/message.js`，不允许各算一套。

