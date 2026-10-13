package com.friendlystation.platform.city;

/**
 * 「用户在哪个城市」的定位结果。
 *
 * @param cityName  逆地理编码得到的城市名（例如「苏州市」）；未配置高德 Key 或调用失败时为空
 * @param province  省级行政区
 * @param district  区县
 * @param covered   该城市是否在平台已覆盖城市内
 * @param city      命中已覆盖城市时的城市对象（含中心点与驿站数）；未覆盖时为 null
 * @param nearest   未覆盖时给出的最近已覆盖城市，供前端提示与降级展示；无城市数据时为 null
 */
public record CityLocation(
        String cityName,
        String province,
        String district,
        boolean covered,
        CityDto city,
        CityDto nearest
) {
}
