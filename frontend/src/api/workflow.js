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

export function getAdminSignups() {
  return request({ url: "/activity/admin/signups" });
}
