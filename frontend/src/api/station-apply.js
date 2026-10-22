import { request } from "./http";

/** 提交驿站入驻申请。 */
export function submitStationApplication(data) {
  return request({ url: "/station/apply", method: "post", data });
}

/** 我的入驻申请记录。 */
export function getMyStationApplications() {
  return request({ url: "/station/apply/mine" });
}

/** 撤回待审核的入驻申请。 */
export function cancelStationApplication(id) {
  return request({ url: `/station/apply/${id}/cancel`, method: "put" });
}

/** 平台端入驻申请分页。 */
export function getStationApplicationPage(params = {}) {
  return request({ url: "/station/apply/admin/page", params: { current: 1, size: 10, ...params } });
}

export function getStationApplicationPendingCount() {
  return request({ url: "/station/apply/admin/pending-count" });
}

export function approveStationApplication(id, reviewNote = "") {
  return request({ url: `/station/apply/${id}/approve`, method: "put", data: { reviewNote } });
}

export function rejectStationApplication(id, reviewNote) {
  return request({ url: `/station/apply/${id}/reject`, method: "put", data: { reviewNote } });
}
