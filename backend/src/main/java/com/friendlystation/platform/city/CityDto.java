package com.friendlystation.platform.city;

/**
 * 平台已覆盖的城市。
 *
 * @param cityCode     行政区划代码，例如杭州市 330100
 * @param cityName     城市名称，例如「杭州市」
 * @param province     省级行政区，例如「浙江省」
 * @param centerLng    城市中心点经度，用于地图初始定位
 * @param centerLat    城市中心点纬度
 * @param stationCount 该城市已收录的驿站数量
 */
public record CityDto(
        String cityCode,
        String cityName,
        String province,
        Double centerLng,
        Double centerLat,
        Long stationCount
) {
}
