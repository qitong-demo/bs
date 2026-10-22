import { request } from "./http";

/** 当前登录用户的站内通知分页。 */
export function getNotifications(params = {}) {
  return request({ url: "/notification/page", params: { current: 1, size: 20, ...params } });
}

export function getUnreadNotificationCount() {
  return request({ url: "/notification/unread-count" });
}

export function markNotificationRead(id) {
  return request({ url: `/notification/${id}/read`, method: "put" });
}

export function markAllNotificationsRead() {
  return request({ url: "/notification/read-all", method: "put" });
}
