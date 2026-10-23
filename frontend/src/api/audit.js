import { request } from "./http";

export function getAuditLogPage(params = {}) {
  return request({ url: "/admin/audit/page", params: { current: 1, size: 20, ...params } });
}
