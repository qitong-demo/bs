import { request } from "./http";

// ------------------------------------------------------------------ 资讯
export function getNewsPage(params = {}) {
  return request({ url: "/news/page", params: { current: 1, size: 10, ...params } });
}

export function getAdminNewsPage(params = {}) {
  return request({ url: "/news/admin/page", params: { current: 1, size: 10, ...params } });
}

export function getNewsDetail(id) {
  return request({ url: `/news/${id}` });
}

export function createNews(data) {
  return request({ url: "/news", method: "post", data });
}

export function updateNews(id, data) {
  return request({ url: `/news/${id}`, method: "put", data });
}

export function deleteNews(id) {
  return request({ url: `/news/${id}`, method: "delete" });
}

// ------------------------------------------------------------------ 公告
export function getNoticePage(params = {}) {
  return request({ url: "/notice/page", params: { current: 1, size: 10, ...params } });
}

export function getAdminNoticePage(params = {}) {
  return request({ url: "/notice/admin/page", params: { current: 1, size: 10, ...params } });
}

export function getNoticeDetail(id) {
  return request({ url: `/notice/${id}` });
}

export function createNotice(data) {
  return request({ url: "/notice", method: "post", data });
}

export function updateNotice(id, data) {
  return request({ url: `/notice/${id}`, method: "put", data });
}

export function deleteNotice(id) {
  return request({ url: `/notice/${id}`, method: "delete" });
}

// ------------------------------------------------------------------ 活动
export function getActivityPage(params = {}) {
  return request({ url: "/activity/page", params: { current: 1, size: 12, ...params } });
}

export function getAdminActivityPage(params = {}) {
  return request({ url: "/activity/admin/page", params: { current: 1, size: 10, ...params } });
}

export function createActivity(data) {
  return request({ url: "/activity/admin", method: "post", data });
}

export function updateActivity(id, data) {
  return request({ url: `/activity/admin/${id}`, method: "put", data });
}

export function deleteActivity(id) {
  return request({ url: `/activity/admin/${id}`, method: "delete" });
}

export function reviewActivity(id, data) {
  return request({ url: `/activity/admin/${id}/review`, method: "put", data });
}

export function getActivityPendingCount() {
  return request({ url: "/activity/admin/pending-review-count" });
}
