package com.friendlystation.platform.city;

import com.friendlystation.platform.common.GeoUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 内存版城市服务（非 mysql profile）。
 * 城市数据来自 {@link com.friendlystation.platform.station.MockStationData}（classpath:db/stations.json），
 * 与 MySQL 初始化脚本同源。
 */
@Service
@Profile("!mysql")
public class MockCityService implements CityService {
    private final List<CityDto> cities;

    public MockCityService(MockCityData cityData) {
        this.cities = cityData.cities();
    }

    @Override
    public List<CityDto> list() {
        return cities;
    }

    @Override
    public CityDto nearest(Double lng, Double lat) {
        if (lng == null || lat == null) {
            return null;
        }
        return cities.stream()
                .min(Comparator.comparingDouble(city -> GeoUtils.distanceKm(lng, lat, city.centerLng(), city.centerLat())))
                .orElse(null);
    }
}
