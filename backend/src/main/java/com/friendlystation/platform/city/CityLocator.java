package com.friendlystation.platform.city;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 用户城市定位：用高德逆地理编码得到真实城市名，再判断该城市是否在平台已覆盖范围内。
 *
 * <p>与「就近选一个已覆盖城市」相比，这里能区分「用户就在已覆盖城市」与
 * 「用户所在城市平台尚未覆盖」，从而给前端明确提示，而不是静默把用户当成别的城市的人。</p>
 *
 * <p>定位逻辑与运行 Profile 无关，因此独立成组件，内存实现与 MySQL 实现共用。</p>
 */
@Component
public class CityLocator {
    private final CityService cityService;
    private final AmapClient amapClient;

    public CityLocator(CityService cityService, AmapClient amapClient) {
        this.cityService = cityService;
        this.amapClient = amapClient;
    }

    /**
     * 按经纬度定位用户城市。
     *
     * @return 定位结果；经纬度缺失或没有城市数据时返回 null，调用方回退默认城市
     */
    public CityLocation locate(Double lng, Double lat) {
        if (lng == null || lat == null) {
            return null;
        }
        var cities = cityService.list();
        if (cities.isEmpty()) {
            return null;
        }

        Optional<AmapClient.AmapRegeo> regeo = amapClient.reverseGeocode(lng, lat);
        CityDto nearest = cityService.nearest(lng, lat);

        if (regeo.isEmpty()) {
            // 没有 Key 或调用失败：退化为「就近城市」，但明确告知前端这是降级结果。
            return new CityLocation("", "", "", false, null, nearest);
        }

        AmapClient.AmapRegeo address = regeo.get();
        String locatedCity = normalize(address.city());
        CityDto matched = cities.stream()
                .filter(city -> normalize(city.cityName()).equals(locatedCity))
                .findFirst()
                .orElse(null);

        if (matched != null) {
            return new CityLocation(address.city(), address.province(), address.district(), true, matched, matched);
        }
        return new CityLocation(address.city(), address.province(), address.district(), false, null, nearest);
    }

    /** 归一化城市名：去掉「市」后缀，避免「北京市」与「北京」匹配不上。 */
    private static String normalize(String cityName) {
        return cityName == null ? "" : cityName.trim().replaceAll("市$", "");
    }
}
