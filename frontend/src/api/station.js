import { request } from "./http";

// ------------------------------------------------------------------ 驿站
export function getStationPage(params = {}) {
  return request({ url: "/station/page", params: { current: 1, size: 20, ...params } });
}

export function getStationDetail(id) {
  return request({ url: `/station/${id}` });
}

/** 区域分布统计：返回 [{ area, count }]，按驿站数量倒序；传 cityCode 时只统计该城市的区县。 */
export function getStationAreas(params = {}) {
  return request({ url: "/station/areas", params: { ...params } });
}

export function getAdminStationPage(params = {}) {
  return request({ url: "/station/admin/page", params: { current: 1, size: 10, ...params } });
}

export function createStation(data) {
  return request({ url: "/station", method: "post", data });
}

export function updateStation(id, data) {
  return request({ url: `/station/${id}`, method: "put", data });
}

export function updateStationStatus(id, status) {
  return request({ url: `/station/${id}/status`, method: "put", params: { status } });
}

export function deleteStation(id) {
  return request({ url: `/station/${id}`, method: "delete" });
}

// -------------------------------------------------------------- 服务项目
export function getStationServicePage(params = {}) {
  return request({ url: "/station/service/page", params: { current: 1, size: 10, ...params } });
}

export function listStationServices(stationId) {
  return request({ url: "/station/service/list", params: { stationId } });
}

export function createStationService(data) {
  return request({ url: "/station/service", method: "post", data });
}

export function updateStationService(id, data) {
  return request({ url: `/station/service/${id}`, method: "put", data });
}

export function updateStationServiceStatus(id, status) {
  return request({ url: `/station/service/${id}/status`, method: "put", params: { status } });
}

export function deleteStationService(id) {
  return request({ url: `/station/service/${id}`, method: "delete" });
}
