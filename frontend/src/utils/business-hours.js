/**
 * 营业时间解析与实时营业状态（前端展示用）。
 *
 * 说明：预约时段的**校验以后端为准**（`common/BusinessHours.java`），
 * 这里只负责界面上的「营业中 / 已打烊 / 时间待核验」显示，
 * 好处是可以按当前时间实时变化，不必等接口刷新。
 */

/** 匹配 HH:mm 或 H:mm，允许 - — ~ 至 作为分隔符。 */
const RANGE_PATTERN = /(\d{1,2}[:：]\d{2})\s*[-—~至]\s*(\d{1,2}[:：]\d{2})/g;
const ALL_DAY_PATTERN = /24\s*小时|全天/i;

/** 把 "8:30" / "08：30" 解析成当天的分钟数；解析失败返回 null。 */
function toMinutes(value) {
  if (!value) return null;
  const match = String(value).replace("：", ":").trim().match(/^(\d{1,2}):(\d{2})$/);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (hour > 23 || minute > 59) return null;
  return hour * 60 + minute;
}

/** 营业时间是否表示全天开放。 */
export function isAllDay(openTime) {
  return Boolean(openTime) && ALL_DAY_PATTERN.test(String(openTime));
}

/** 解析出所有营业区间，元素形如 { start, end }（单位：分钟）。 */
export function parseBusinessHours(openTime) {
  const ranges = [];
  if (!openTime) return ranges;
  const text = String(openTime);
  RANGE_PATTERN.lastIndex = 0;
  let match = RANGE_PATTERN.exec(text);
  while (match) {
    const start = toMinutes(match[1]);
    const end = toMinutes(match[2]);
    if (start !== null && end !== null && start < end) {
      ranges.push({ start, end });
    }
    match = RANGE_PATTERN.exec(text);
  }
  return ranges;
}

/**
 * 按驿站营业时间生成可选的预约时段。
 *
 * 为什么需要它：预约表单原先写死「09:00-11:00 / 14:00-16:00 / 18:00-20:00」，
 * 而驿站 10:00 才开门时，用户选 09:00 会被后端营业时间校验直接拒绝——先让人选再报错。
 * 这里只在营业窗口内切分时段，从源头上避免无效选项。
 *
 * 营业时间缺失或无法解析时返回一组通用时段（后端此时也不拦截）。
 */
export function buildSlots(openTime, { stepMinutes = 120, max = 4 } = {}) {
  const fallback = ["09:00-11:00", "14:00-16:00", "18:00-20:00"];
  if (!openTime || isAllDay(openTime)) return fallback;
  const ranges = parseBusinessHours(openTime);
  if (!ranges.length) return fallback;

  const slots = [];
  for (const range of ranges) {
    for (let start = range.start; start + stepMinutes <= range.end; start += stepMinutes) {
      slots.push(`${formatMinutes(start)}-${formatMinutes(start + stepMinutes)}`);
      if (slots.length >= max) return slots;
    }
  }
  // 营业窗口比一个步长还短时，直接给出整个窗口
  if (!slots.length) {
    const range = ranges[0];
    slots.push(`${formatMinutes(range.start)}-${formatMinutes(range.end)}`);
  }
  return slots;
}

/** 分钟数转 HH:mm。 */
function formatMinutes(minutes) {
  const hour = String(Math.floor(minutes / 60)).padStart(2, "0");
  const minute = String(minutes % 60).padStart(2, "0");
  return `${hour}:${minute}`;
}

/**
 * 判断某个时刻是否在营业时间内。
 *
 * @returns {"OPEN"|"CLOSED"|"UNKNOWN"}
 *   OPEN    —— 在营业时间内
 *   CLOSED  —— 有可解析的营业时间，但当前不在其中
 *   UNKNOWN —— 营业时间缺失或无法解析，**不能判断**（界面应显示「营业时间待核验」而不是「营业中」）
 */
export function openState(openTime, now = new Date()) {
  if (!openTime || !String(openTime).trim()) return "UNKNOWN";
  if (isAllDay(openTime)) return "OPEN";
  const ranges = parseBusinessHours(openTime);
  if (!ranges.length) return "UNKNOWN";
  const minutes = now.getHours() * 60 + now.getMinutes();
  const hit = ranges.some((range) => minutes >= range.start && minutes <= range.end);
  return hit ? "OPEN" : "CLOSED";
}

/**
 * 营业状态的中文文案与标签色，供各页面统一使用。
 * 注意：UNKNOWN 显示为「营业时间待核验」——数据缺失时不能谎称「营业中」。
 */
export function openStateMeta(openTime, now = new Date(), stationStatus = "OPEN") {
  if (stationStatus && stationStatus !== "OPEN") {
    return { key: "CLOSED_STATUS", label: "已停用", tag: "info" };
  }
  const state = openState(openTime, now);
  if (state === "OPEN") return { key: "OPEN", label: "营业中", tag: "success" };
  if (state === "CLOSED") return { key: "CLOSED", label: "已打烊", tag: "warning" };
  return { key: "UNKNOWN", label: "营业时间待核验", tag: "info" };
}
