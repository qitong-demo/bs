import { request } from "./http";

export function getAdminOverview() {
  return request({ url: "/admin/overview" });
}

export function getAdminUsers(params = {}) {
  return request({ url: "/admin/users", params: { current: 1, size: 10, ...params } });
}

export function getAdminUserDetail(id) {
  return request({ url: `/admin/users/${id}` });
}

export function updateAdminUser(id, data) {
  return request({ url: `/admin/users/${id}`, method: "put", data });
}

export function updateAdminUserStatus(id, status) {
  return request({ url: `/admin/users/${id}/status`, method: "put", data: { status } });
}

export function updateAdminUserRoles(id, roleCodes) {
  return request({ url: `/admin/users/${id}/roles`, method: "put", data: { roleCodes } });
}

export function getRoles() {
  return request({ url: "/admin/roles" });
}
