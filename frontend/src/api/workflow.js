import { request } from "./http";

// ------------------------------------------------------------------ 预约
export function createAppointment(data) {
  return request({ url: "/appointment", method: "post", data });
}

export function getMyAppointments() {
  return request({ url: "/appointment/my" });
}

export function cancelAppointment(id) {
  return request({ url: `/appointment/${id}/cancel`, method: "put" });
}

export function getAdminAppointments(params = {}) {
  return request({ url: "/appointment/admin/page", params: { current: 1, size: 10, ...params } });
}

export function auditAppointment(data) {
  return request({ url: "/appointment/audit", method: "put", data });
}

/** 服务完成后把已确认的预约标记为已完成。 */
export function finishAppointment(id) {
  return request({ url: `/appointment/${id}/finish`, method: "put" });
}

// ------------------------------------------------------------------ 诉求
export function createAppeal(data) {
  return request({ url: "/appeal", method: "post", data });
}

export function getMyAppeals() {
  return request({ url: "/appeal/my" });
}

export function getAppealDetail(id) {
  return request({ url: `/appeal/${id}` });
}

export function getAppealRecords(id) {
  return request({ url: `/appeal/${id}/records` });
}

export function getAdminAppeals(params = {}) {
  return request({ url: "/appeal/admin/page", params: { current: 1, size: 10, ...params } });
}

export function assignAppeal(data) {
  return request({ url: "/appeal/assign", method: "put", data });
}

export function handleAppeal(data) {
  return request({ url: "/appeal/handle", method: "put", data });
}

export function evaluateAppeal(data) {
  return request({ url: "/appeal/evaluate", method: "post", data });
}

// -------------------------------------------------------------- 活动报名
export function signupActivity(data) {
  return request({ url: "/activity/signup", method: "post", data });
}

export function getMySignups() {
  return request({ url: "/activity/my" });
}

/**
 * 承办方处理报名：联系 / 审核通过 / 审核未通过，并给出给报名人的反馈。
 * 审核未通过时后端要求填写原因。
 */
export function reviewSignup(data) {
  return request({ url: "/activity/signup/review", method: "put", data });
}

export function getAdminSignups() {
  return request({ url: "/activity/admin/signups" });
}

/** 待处理报名数：平台端标签角标。 */
export function getSignupPendingCount() {
  return request({ url: "/activity/admin/signups/pending-count" });
}

/** 报名人取消自己的报名：审核通过/未通过后不可取消。 */
export function cancelSignup(id) {
  return request({ url: `/activity/signup/${id}/cancel`, method: "put" });
}
