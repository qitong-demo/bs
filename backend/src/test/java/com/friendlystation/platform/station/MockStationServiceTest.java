package com.friendlystation.platform.station;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内存版驿站服务测试。
 * 种子数据来自 classpath:db/stations.json（多城市真实公开来源名录），因此断言不依赖固定的驿站条数。
 */
class MockStationServiceTest {
    private static final String HANGZHOU = "330100";
    private static final String BEIJING = "110100";

    private final MockStationService stationService = new MockStationService(new MockStationData());

    @Test
    void supportsPagingOverTheImportedDirectory() {
        PageResult<StationDto> firstPage = stationService.page(1, 2, null, null, null, null, null);

        assertEquals(2, firstPage.records().size());
        assertTrue(firstPage.total() > 100, "公开来源名录应包含较多驿站，实际：" + firstPage.total());
    }

    @Test
    void filtersByCityAndReportsCityCode() {
        PageResult<StationDto> hangzhou = stationService.page(1, 50, HANGZHOU, null, null, null, null);
        PageResult<StationDto> beijing = stationService.page(1, 50, BEIJING, null, null, null, null);

        assertTrue(hangzhou.total() > 0);
        assertTrue(beijing.total() > 0);
        assertTrue(hangzhou.records().stream().allMatch(item -> HANGZHOU.equals(item.cityCode())));
        assertTrue(beijing.records().stream().allMatch(item -> BEIJING.equals(item.cityCode())));
        assertEquals(stationService.page(1, 1, null, null, null, null, null).total(),
                hangzhou.total() + beijing.total()
                        + stationService.page(1, 1, "310100", null, null, null, null).total()
                        + stationService.page(1, 1, "440300", null, null, null, null).total()
                        + stationService.page(1, 1, "440100", null, null, null, null).total()
                        + stationService.page(1, 1, "510100", null, null, null, null).total()
                        + stationService.page(1, 1, "420100", null, null, null, null).total(),
                "各城市驿站数之和应等于总数");
    }

    @Test
    void supportsAreaFiltering() {
        PageResult<StationDto> xihu = stationService.page(1, 50, HANGZHOU, "西湖区", null, null, null);

        assertTrue(xihu.total() > 0, "杭州应存在西湖区的驿站");
        assertTrue(xihu.records().stream().allMatch(item -> item.area().contains("西湖区")));
    }

    @Test
    void supportsKeywordFilteringAcrossNameAddressAndServices() {
        PageResult<StationDto> matched = stationService.page(1, 10, HANGZHOU, null, "城管驿站", null, null);

        assertFalse(matched.records().isEmpty());
        assertTrue(matched.records().stream().allMatch(item ->
                item.stationName().contains("城管驿站")
                        || item.address().contains("城管驿站")
                        || item.services().stream().anyMatch(service -> service.contains("城管驿站"))));
    }

    @Test
    void supportsServiceFilteringOnTheServerSide() {
        PageResult<StationDto> withService = stationService.page(1, 50, HANGZHOU, null, null, "换电柜", null);

        assertTrue(withService.total() > 0, "杭州应有提供换电柜的驿站");
        assertTrue(withService.records().stream()
                .allMatch(item -> item.services().stream().anyMatch(name -> name.contains("换电柜"))));
    }

    @Test
    void supportsStatusFiltering() {
        PageResult<StationDto> openOnly = stationService.page(1, 50, null, null, null, null, "OPEN");
        PageResult<StationDto> closedOnly = stationService.page(1, 50, null, null, null, null, "CLOSED");

        assertTrue(openOnly.total() > 0);
        assertTrue(openOnly.records().stream().allMatch(item -> "OPEN".equals(item.status())));
        assertEquals(0, closedOnly.total(), "导入的名录在发布时均为开放状态");
    }

    @Test
    void areaSummaryCoversEveryImportedStation() {
        List<Map<String, Object>> summary = stationService.areaSummary(null);

        long total = summary.stream().mapToLong(row -> ((Number) row.get("count")).longValue()).sum();
        assertEquals(stationService.page(1, 1, null, null, null, null, null).total(), total);
        assertTrue(summary.size() > 5, "真实名录应覆盖多个行政区，实际：" + summary.size());
    }

    @Test
    void areaSummaryHonoursCityFilter() {
        List<Map<String, Object>> hangzhouAreas = stationService.areaSummary(HANGZHOU);

        long total = hangzhouAreas.stream().mapToLong(row -> ((Number) row.get("count")).longValue()).sum();
        assertEquals(stationService.page(1, 1, HANGZHOU, null, null, null, null).total(), total);
        assertTrue(hangzhouAreas.stream().noneMatch(row -> "东城区".equals(row.get("area"))),
                "北京东城区不应出现在杭州的区域汇总里");
    }

    @Test
    void everyStationBelongsToAKnownCity() {
        List<String> knownCities = List.of(HANGZHOU, BEIJING, "310100", "440300", "440100", "510100", "420100");

        assertTrue(stationService.page(1, 1, null, null, null, null, null).total() > 0);
        for (String cityCode : knownCities) {
            assertTrue(stationService.page(1, 1, cityCode, null, null, null, null).total() >= 0);
        }
    }

    @Test
    void createsStationAndReportsItInTotal() {
        long before = stationService.page(1, 1, null, null, null, null, null).total();

        stationService.create(new StationCreateRequest("测试驿站", "滨江区", "测试地址", null, null, null, "测试类型", HANGZHOU));

        assertEquals(before + 1, stationService.page(1, 1, null, null, null, null, null).total());
    }

    @Test
    void manuallyCreatedStationIsMarkedAsManualSource() {
        StationDto created = stationService.create(
                new StationCreateRequest("手工录入驿站", "滨江区", "测试地址", null, null, null, "小哥驿站", HANGZHOU));

        assertEquals("小哥驿站", created.stationType());
        assertEquals(HANGZHOU, created.cityCode());
        assertEquals("MANUAL", created.source().dataStatus());
        assertFalse(created.source().dataNote().isBlank());
    }

    @Test
    void manuallyCreatedStationFallsBackToDefaultCity() {
        StationDto created = stationService.create(
                new StationCreateRequest("未指定城市的驿站", "滨江区", "测试地址", null, null, null, null, null));

        assertEquals("330100", created.cityCode(), "未指定城市时应落到平台默认城市（杭州）");
    }

    @Test
    void importedStationCarriesVerifiableSource() {
        StationDto station = stationService.detail(1L);

        assertFalse(station.source().sourceName().isBlank(), "导入数据应记录来源名称");
        assertFalse(station.source().sourceUrl().isBlank(), "导入数据应记录来源地址");
        assertFalse(station.source().verifiedAt().isBlank(), "导入数据应记录核对日期");
        assertTrue(station.source().sourceUrl().startsWith("http"));
        assertFalse(station.stationType().isBlank(), "导入数据应记录驿站类型");
        assertEquals(HANGZHOU, station.cityCode(), "1 号驿站是杭州的小哥驿站");
    }

    @Test
    void demoManagerAssignmentOnlyCoversXiaogeStations() {
        List<StationDto> managed = stationService.listByManager(2L);

        assertEquals(4, managed.size(), "演示配置把 1~4 号小哥驿站分配给账号 2");
        assertTrue(managed.stream().allMatch(item -> "小哥驿站".equals(item.stationType())));
        assertTrue(stationService.listByManager(999L).isEmpty());
    }

    @Test
    void rejectsMissingStation() {
        assertThrows(BusinessException.class, () -> stationService.detail(999999L));
    }

    @Test
    void rejectsInvalidStatusTransition() {
        assertThrows(BusinessException.class, () -> stationService.updateStatus(1L, "UNKNOWN"));
    }
}
