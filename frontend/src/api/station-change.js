/**
 * 驿站信息变更申请与审批的接口。
 *
 * 注意路径在 /api/station/change 而不是 /api/admin 下：
 * 驿站侧提交与查看、管理员侧审批共用同一组端点，
 * 权限由 @RequirePermission 区分（审批需要 CHANGE_APPROVE）。
 */
import { request } from "./http";

/** 待审批列表（需 CHANGE_APPROVE 权限）。 */
export function getPendingChanges() {
  return request({ url: "/station/change/pending" });
}

/** 某驿站的申请记录。 */
export function listChanges(stationId) {
  return request({ url: "/station/change/list", params: { stationId } });
}

/** 提交变更申请（驿站侧）。 */
export function submitChange(data) {
  return request({ url: "/station/change", method: "post", data });
}

/** 审批通过。 */
export function approveChange(id, reviewNote) {
  return request({ url: `/station/change/${id}/approve`, method: "put", data: { reviewNote } });
}

/** 审批驳回；后端要求必须填写原因。 */
export function rejectChange(id, reviewNote) {
  return request({ url: `/station/change/${id}/reject`, method: "put", data: { reviewNote } });
}
