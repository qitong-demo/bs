import { request } from "./http";

export function getRectificationPage(params = {}) {
  return request({ url: "/station/rectification/page", params: { current: 1, size: 10, ...params } });
}

export function getRectificationPendingCount() {
  return request({ url: "/station/rectification/pending-count" });
}

export function createRectification(data) {
  return request({ url: "/station/rectification", method: "post", data });
}

export function replyRectification(id, reply) {
  return request({ url: `/station/rectification/${id}/reply`, method: "put", data: { reply } });
}

export function reviewRectification(id, data) {
  return request({ url: `/station/rectification/${id}/review`, method: "put", data });
}
