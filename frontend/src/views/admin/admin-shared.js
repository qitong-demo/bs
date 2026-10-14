/**
 * 管理端工作台公共常量与展示工具。
 * 只做状态文案 / 标签色的映射，不参与任何业务数据的计算或编造。
 */

/**
 * 平台级角色。标签可见性已改由后端 /admin/my-permissions 驱动，
 * 这里只作为"权限接口取不到时"的兜底判断。
 */
export const PLATFORM_ROLES = ["PLATFORM_ADMIN"];

/** 系统内置的三个角色编码，与后端 RoleCatalog 一致。 */
export const ROLE_OPTIONS = [
  { value: "USER", label: "普通用户" },
  { value: "STATION_ADMIN", label: "驿站管理员" },
  { value: "PLATFORM_ADMIN", label: "管理员" }
];

/** 每个角色的数据范围说明，用于角色权限页。 */
export const ROLE_SCOPE = {
  USER: "仅本人数据：本人预约、本人诉求、本人报名与本人评价，不能进入管理工作台。",
  STATION_ADMIN: "本驿站范围：所属驿站的服务项目、预约审核、诉求处理、活动管理与报表导出。",
  PLATFORM_ADMIN: "平台全域：全部驿站、服务项目、预约、诉求、资讯公告、活动、账号与角色权限；"
    + "可创建子账号并分发功能权限。"
};

const APPOINTMENT_STATUS_LABELS = {
  PENDING_STATION_CONFIRMATION: "待驿站确认",
  CONFIRMED: "已确认",
  REJECTED: "已拒绝",
  CANCELLED: "已取消",
  FINISHED: "已完成"
};

const APPEAL_STATUS_LABELS = {
  PENDING_ACCEPTANCE: "待受理",
  ASSIGNED: "已分派",
  PROCESSING: "处理中",
  RESOLVED: "已办结",
  CLOSED: "已关闭"
};

const STATION_STATUS_LABELS = {
  OPEN: "营业中",
  CLOSED: "已停用",
  DELETED: "已删除"
};

const CONTENT_STATUS_LABELS = {
  PUBLISHED: "已发布",
  DRAFT: "草稿"
};

/** 空值统一显示为 "—"，避免出现 null / undefined。 */
export function textOf(value) {
  if (value === null || value === undefined) return "—";
  const text = String(value).trim();
  return text === "" ? "—" : text;
}

/** 数值型计数展示，非数字时显示 "—"，不做任何推算。 */
export function numberText(value) {
  if (value === null || value === undefined || value === "") return "—";
  const num = Number(value);
  return Number.isFinite(num) ? String(num) : String(value);
}

/** 预约状态文案：优先使用后端 statusLabel。 */
export function appointmentStatusLabel(row) {
  if (!row) return "未知";
  return row.statusLabel || APPOINTMENT_STATUS_LABELS[row.status] || row.status || "未知";
}

/**
 * 诉求状态文案（管理端）。
 *
 * 优先用后端的**标准术语** `statusLabelStandard`（待受理/已分派/处理中/已办结）。
 * 用户端的 `statusLabel` 是按分类改写的温暖措辞（如「心意已收到」），
 * 管理端需要保留开题与中期报告要求的闭环术语，因此这里不采用它。
 */
export function appealStatusLabel(row) {
  if (!row) return "未知";
  return row.statusLabelStandard || APPEAL_STATUS_LABELS[row.status] || row.status || "未知";
}

/** 驿站状态文案。 */
export function stationStatusLabel(status) {
  if (!status) return "未知";
  return STATION_STATUS_LABELS[status] || status;
}

/** 资讯 / 公告状态文案：优先使用后端 statusLabel。 */
export function contentStatusLabel(row) {
  if (!row) return "未知";
  return row.statusLabel || CONTENT_STATUS_LABELS[row.status] || row.status || "未知";
}

/** 预约状态标签色。 */
export function appointmentStatusTag(status) {
  if (status === "CONFIRMED" || status === "FINISHED") return "success";
  if (status === "REJECTED") return "danger";
  if (status === "PENDING_STATION_CONFIRMATION") return "warning";
  return "info";
}

/** 诉求状态标签色。 */
export function appealStatusTag(status) {
  if (status === "RESOLVED") return "success";
  if (status === "CLOSED") return "info";
  if (status === "PROCESSING") return "primary";
  if (status === "ASSIGNED") return "warning";
  return "danger";
}

/** 报名状态标签色：通过为成功、未通过为危险、已联系为进行中。 */
export function signupStatusTag(status) {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  if (status === "CONTACTED") return "warning";
  return "info";
}

/** 驿站状态标签色。 */
export function stationStatusTag(status) {
  if (status === "OPEN") return "success";
  if (status === "CLOSED") return "warning";
  if (status === "DELETED") return "danger";
  return "info";
}

/** 资讯 / 公告状态标签色。 */
export function contentStatusTag(status) {
  return status === "PUBLISHED" ? "success" : "info";
}

/** 账号启用状态标签色（后端 status 为 1/0）。 */
export function userStatusTag(status) {
  return Number(status) === 1 ? "success" : "danger";
}

/** 账号启用状态文案（后端同时返回 statusLabel）。 */
export function userStatusLabel(row) {
  if (!row) return "未知";
  if (row.statusLabel) return row.statusLabel;
  return Number(row.status) === 1 ? "正常" : "停用";
}

/** 资讯分类选项。 */
export const NEWS_CATEGORIES = ["政策", "通知", "动态", "服务"];

/** 公告类型选项。 */
export const NOTICE_TYPES = ["通知", "公告", "政策", "提醒"];

/** 公告投放对象选项。 */
export const NOTICE_TARGETS = [
  { value: "ALL", label: "全部用户" },
  { value: "USER", label: "新就业群体用户" },
  { value: "STATION_ADMIN", label: "驿站管理员" },
  { value: "PLATFORM_ADMIN", label: "平台管理员" }
];
