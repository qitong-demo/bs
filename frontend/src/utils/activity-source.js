/**
 * 活动来源口径：区分「驿站自办活动」与「采集来的第三方活动」。
 *
 * 采集活动（organizerType=EXTERNAL）只在站内展示：报名与审核都在主办方那边，
 * 后端报名接口对这类活动直接返回 400。所以「能不能站内报名」「按钮点了去哪」
 * 统一在这里判断，列表卡片与详情弹窗不允许各写一套 if。
 */

/** 第三方/外部主办：信息采集自公开来源，不接受站内报名。 */
export const ORGANIZER_EXTERNAL = "EXTERNAL";

/** 是否第三方（采集）活动。字段缺失时按站内活动处理，走既有的报名路径。 */
export function isExternalActivity(activity) {
  return String(activity?.organizerType || "").trim().toUpperCase() === ORGANIZER_EXTERNAL;
}

/**
 * 来源标签文案：驿站自办显示「驿站」，采集活动显示后端给的 organizerLabel（第三方机构）。
 * 后端保证 label 非空，这里只做兜底，避免标签位置出现空白。
 */
export function activitySourceLabel(activity) {
  const label = String(activity?.organizerLabel || "").trim();
  if (label) return label;
  return isExternalActivity(activity) ? "第三方机构" : "驿站";
}

/** 原文链接（已去空白）。返回空串表示这条采集活动拿不到原文，跳转按钮必须禁用并说明原因。 */
export function externalUrlText(activity) {
  return String(activity?.externalUrl || "").trim();
}

/**
 * 卡片与详情里「报名去哪」的那句话。
 * 采集活动必须说清报名与审核在主办方，否则用户会以为站内能报上；站内活动返回空串。
 */
export function activitySignupHint(activity) {
  if (!isExternalActivity(activity)) return "";
  return externalUrlText(activity)
    ? "报名与审核由主办方负责，本站仅提供信息"
    : "该活动未提供原文链接，暂无法跳转，可联系平台管理员核对";
}
