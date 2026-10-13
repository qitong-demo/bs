package com.friendlystation.platform.city;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.friendlystation.platform.common.GeoUtils;
import com.friendlystation.platform.persistence.entity.City;
import com.friendlystation.platform.persistence.entity.Station;
import com.friendlystation.platform.persistence.mapper.CityMapper;
import com.friendlystation.platform.persistence.mapper.StationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 数据库版城市服务（mysql profile）。
 */
@Service
@Profile("mysql")
public class MysqlCityService implements CityService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATION_STATUS_DELETED = "DELETED";

    private final CityMapper cityMapper;
    private final StationMapper stationMapper;

    public MysqlCityService(CityMapper cityMapper, StationMapper stationMapper) {
        this.cityMapper = cityMapper;
        this.stationMapper = stationMapper;
    }

    @Override
    public List<CityDto> list() {
        return cityMapper.selectList(new LambdaQueryWrapper<City>()
                        .eq(City::getStatus, STATUS_ACTIVE)
                        .orderByAsc(City::getSortNo)
                        .orderByAsc(City::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public CityDto nearest(Double lng, Double lat) {
        if (lng == null || lat == null) {
            return null;
        }
        return list().stream()
                .min(Comparator.comparingDouble(city -> GeoUtils.distanceKm(lng, lat, city.centerLng(), city.centerLat())))
                .orElse(null);
    }

    private CityDto toDto(City city) {
        long stationCount = stationMapper.selectCount(new LambdaQueryWrapper<Station>()
                .eq(Station::getCityCode, city.getCityCode())
                .ne(Station::getStatus, STATION_STATUS_DELETED));
        return new CityDto(
                city.getCityCode(),
                city.getCityName(),
                city.getProvince(),
                toDouble(city.getCenterLng()),
                toDouble(city.getCenterLat()),
                stationCount);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
