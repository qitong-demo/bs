import { request } from "./http";

// ------------------------------------------------------------------ 城市
/**
 * 平台已覆盖的城市列表，元素形如：
 * { cityCode, cityName, province, centerLng, centerLat, stationCount }。
 * 后端按 sortNo 升序返回，第一项为平台默认城市。接口公开，无需 token。
 */
export function getCityList() {
  return request({ url: "/city/list" });
}

/**
 * 按经纬度返回**距离最近**的已覆盖城市（不判断用户真实所在城市）。
 * 城市列表为空时后端返回 null，调用方需自行兜底。
 */
export function getNearestCity(lng, lat) {
  return request({ url: "/city/nearest", params: { lng, lat } });
}

/**
 * 按经纬度定位「用户在哪个城市」，后端用高德逆地理编码得到真实城市名。
 * 返回 { cityName, province, district, covered, city, nearest }：
 *   - covered=true  → 用户所在城市平台已覆盖，用 city；
 *   - covered=false → 该城市尚未覆盖，用 nearest 降级展示，并把 cityName 告诉用户。
 * 未配置高德 Key 时后端返回 cityName 为空、covered=false 的结果，调用方回退默认城市。
 */
export function locateCity(lng, lat) {
  return request({ url: "/city/locate", params: { lng, lat } });
}
