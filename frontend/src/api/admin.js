import { request } from "./http";

/**
 * 当前账号实际生效的功能权限。
 *
 * 管理端标签可见性由它决定，而不是前端硬编码——否则子账号会看到
 * 自己无权使用的标签，点进去只会拿到 403。
 */
export function getMyPermissions() {
  return request({ url: "/admin/my-permissions" });
}

/** 权限目录，创建子账号时渲染勾选项。 */
export function getPermissionCatalog() {
  return request({ url: "/admin/permissions" });
}
/** 创建管理员子账号并分发权限；需管理员的登录密码二次确认。 */
export function createSubAccount(data) {
  return request({ url: "/admin/users", method: "post", data });
}

/** 某账号被显式授予的权限。 */
export function getUserPermissions(id) {
  return request({ url: `/admin/users/${id}/permissions` });
}

/** 覆盖式分发权限。 */
export function grantPermissions(id, permissions) {
  return request({ url: `/admin/users/${id}/permissions`, method: "put", data: { permissions } });
}
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
