package com.friendlystation.platform.city;

import com.friendlystation.platform.common.GeoUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内存版城市服务测试：多城市平台的默认城市与「定位后判断所在城市」。
 */
class MockCityServiceTest {
    private final MockCityService cityService = new MockCityService(new MockCityData());

    @Test
    void listsCoveredCitiesWithStationCounts() {
        List<CityDto> cities = cityService.list();

        assertTrue(cities.size() >= 7, "平台应覆盖多个城市，实际：" + cities.size());
        assertEquals("330100", cities.get(0).cityCode(), "列表首个城市是平台默认城市");
        assertTrue(cities.stream().allMatch(city -> city.stationCount() != null && city.stationCount() > 0));
        assertTrue(cities.stream().allMatch(city -> city.centerLng() != null && city.centerLat() != null),
                "每个城市都要有中心点，否则无法做定位判断");
    }

    @Test
    void nearestReturnsTheCityAroundTheGivenCoordinate() {
        CityDto hangzhou = cityService.nearest(120.1551, 30.2741);
        CityDto beijing = cityService.nearest(116.4074, 39.9042);
        CityDto guangzhou = cityService.nearest(113.2644, 23.1291);

        assertNotNull(hangzhou);
        assertNotNull(beijing);
        assertNotNull(guangzhou);
        assertEquals("330100", hangzhou.cityCode());
        assertEquals("110100", beijing.cityCode());
        assertEquals("440100", guangzhou.cityCode());
    }

    @Test
    void nearestPicksTheCityWithTheShortestDistance() {
        // 上海人民广场附近，应命中上海而不是杭州
        CityDto nearest = cityService.nearest(121.4737, 31.2304);

        assertNotNull(nearest);
        assertEquals("310100", nearest.cityCode());
        for (CityDto city : cityService.list()) {
            if (!city.cityCode().equals(nearest.cityCode())) {
                assertTrue(GeoUtils.distanceKm(121.4737, 31.2304, nearest.centerLng(), nearest.centerLat())
                                <= GeoUtils.distanceKm(121.4737, 31.2304, city.centerLng(), city.centerLat()),
                        "返回的城市应当是最近的：" + city.cityName());
            }
        }
    }

    @Test
    void nearestReturnsNullWhenCoordinateIsMissing() {
        assertNull(cityService.nearest(null, null));
        assertNull(cityService.nearest(120.1551, null));
    }

    @Test
    void distanceIsZeroForTheSamePointAndGrowsWithDistance() {
        assertEquals(0.0, GeoUtils.distanceKm(120.1551, 30.2741, 120.1551, 30.2741), 0.0001);
        double hangzhouToBeijing = GeoUtils.distanceKm(120.1551, 30.2741, 116.4074, 39.9042);
        assertTrue(hangzhouToBeijing > 1000 && hangzhouToBeijing < 1300,
                "杭州到北京的距离量级应约为 1100 公里，实际：" + hangzhouToBeijing);
        assertEquals(Double.MAX_VALUE, GeoUtils.distanceKm(null, 30.0, 120.0, 30.0));
    }
}
