package com.friendlystation.platform.station;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存版驿站服务（非 mysql profile），与 MysqlStationService 对外行为保持一致。
 *
 * <p>种子数据来自 {@link MockStationData}（classpath:db/stations.json），与 MySQL 初始化脚本同源；
 * 后台新增的驿站标记 dataStatus=MANUAL，内存实现没有数据库创建时间，createTime 固定为空字符串。</p>
 */
@Service
@Profile("!mysql")
public class MockStationService implements StationService {
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String DEFAULT_TYPE = "未分类";
    private static final String DEFAULT_CITY_CODE = "330100";
    private static final String MANUAL_STATUS = "MANUAL";
    private static final String MANUAL_NOTE = "后台录入，未标注公开来源";

    private final List<StationDto> stations = new CopyOnWriteArrayList<>();
    private final AtomicLong idGenerator;

    public MockStationService(MockStationData data) {
        this.stations.addAll(data.stations());
        long maxId = data.stations().stream().mapToLong(StationDto::id).max().orElse(0L);
        this.idGenerator = new AtomicLong(maxId);
    }

    /**
     * 公开分页：返回全部未被逻辑删除的驿站（OPEN 与 CLOSED），与 mysql 实现一致。
     */
    @Override
    public PageResult<StationDto> page(long current, long size, String cityCode, String area, String keyword,
                                       String service, String status) {
        return pageInternal(current, size, cityCode, area, keyword, service, status, true);
    }

    @Override
    public PageResult<StationDto> adminPage(long current, long size, String cityCode, String area, String keyword,
                                            String status) {
        String normalizedStatus = status == null ? "" : status.trim();
        return pageInternal(current, size, cityCode, area, keyword, "", normalizedStatus, false);
    }

    @Override
    public List<Map<String, Object>> areaSummary(String cityCode) {
        String normalizedCity = cityCode == null ? "" : cityCode.trim();
        Map<String, Long> counts = stations.stream()
                .filter(item -> !STATUS_DELETED.equals(item.status()))
                .filter(item -> normalizedCity.isBlank() || normalizedCity.equals(item.cityCode()))
                .collect(Collectors.groupingBy(StationDto::area, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("area", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .toList();
    }

    @Override
    public StationDto detail(Long id) {
        return stations.stream()
                .filter(station -> station.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("驿站不存在"));
    }

    @Override
    public List<StationDto> listByManager(Long managerId) {
        if (managerId == null) {
            return List.of();
        }
        return stations.stream()
                .filter(station -> !STATUS_DELETED.equals(station.status()))
                .filter(station -> managerId.equals(station.managerId()))
                .toList();
    }

    @Override
    public StationDto create(StationCreateRequest request) {
        long id = idGenerator.incrementAndGet();
        StationDto station = new StationDto(
                id,
                request.cityCode() == null || request.cityCode().isBlank() ? DEFAULT_CITY_CODE : request.cityCode().trim(),
                request.stationName(),
                request.stationType() == null || request.stationType().isBlank()
                        ? DEFAULT_TYPE : request.stationType().trim(),
                request.area(),
                request.address(),
                null,
                null,
                request.contactPhone(),
                request.openTime(),
                "—",
                List.of(),
                request.status() == null || request.status().isBlank() ? STATUS_OPEN : requireOpenOrClosed(request.status()),
                null,
                new StationSource("", "", "", LocalDate.now().toString(), MANUAL_STATUS, MANUAL_NOTE, "", "", "", ""),
                null,
                null,
                "",
                ""
        );
        stations.add(station);
        return station;
    }

    @Override
    public StationDto update(Long id, StationUpdateRequest request) {
        int index = indexOfActive(id);
        StationDto exists = stations.get(index);
        // 可空字段为 null 时保留原值，与 mysql 实现的 updateById 行为一致
        StationDto updated = new StationDto(
                exists.id(),
                request.cityCode() == null || request.cityCode().isBlank() ? exists.cityCode() : request.cityCode().trim(),
                request.stationName(),
                request.stationType() == null || request.stationType().isBlank()
                        ? exists.stationType() : request.stationType().trim(),
                request.area(),
                request.address(),
                exists.longitude(),
                exists.latitude(),
                request.contactPhone() == null ? exists.contactPhone() : request.contactPhone(),
                request.openTime() == null ? exists.openTime() : request.openTime(),
                exists.distance(),
                exists.services(),
                request.status() == null || request.status().isBlank() ? exists.status() : requireOpenOrClosed(request.status()),
                exists.managerId(),
                exists.source(),
                exists.suspendedFrom(),
                exists.suspendedTo(),
                exists.suspendReason(),
                exists.createTime()
        );
        stations.set(index, updated);
        return updated;
    }

    @Override
    public StationDto updateStatus(Long id, String status) {
        String normalizedStatus = requireOpenOrClosed(status);
        int index = indexOfActive(id);
        StationDto exists = stations.get(index);
        StationDto updated = new StationDto(
                exists.id(),
                exists.cityCode(),
                exists.stationName(),
                exists.stationType(),
                exists.area(),
                exists.address(),
                exists.longitude(),
                exists.latitude(),
                exists.contactPhone(),
                exists.openTime(),
                exists.distance(),
                exists.services(),
                normalizedStatus,
                exists.managerId(),
                exists.source(),
                exists.suspendedFrom(),
                exists.suspendedTo(),
                exists.suspendReason(),
                exists.createTime()
        );
        stations.set(index, updated);
        return updated;
    }

    @Override
    public StationDto suspend(Long id, LocalDateTime from, LocalDateTime to, String reason) {
        int index = indexOfActive(id);
        StationDto exists = stations.get(index);
        StationDto updated = new StationDto(
                exists.id(),
                exists.cityCode(),
                exists.stationName(),
                exists.stationType(),
                exists.area(),
                exists.address(),
                exists.longitude(),
                exists.latitude(),
                exists.contactPhone(),
                exists.openTime(),
                exists.distance(),
                exists.services(),
                exists.status(),
                exists.managerId(),
                exists.source(),
                FormatUtils.dateTime(from),
                FormatUtils.dateTime(to),
                reason == null ? "" : reason.trim(),
                exists.createTime()
        );
        stations.set(index, updated);
        return updated;
    }

    @Override
    public StationDto resume(Long id) {
        int index = indexOfActive(id);
        StationDto exists = stations.get(index);
        StationDto updated = new StationDto(
                exists.id(),
                exists.cityCode(),
                exists.stationName(),
                exists.stationType(),
                exists.area(),
                exists.address(),
                exists.longitude(),
                exists.latitude(),
                exists.contactPhone(),
                exists.openTime(),
                exists.distance(),
                exists.services(),
                exists.status(),
                exists.managerId(),
                exists.source(),
                null,
                null,
                "",
                exists.createTime()
        );
        stations.set(index, updated);
        return updated;
    }

    @Override
    public void delete(Long id) {
        int index = indexOfActive(id);
        StationDto exists = stations.get(index);
        stations.set(index, new StationDto(
                exists.id(),
                exists.cityCode(),
                exists.stationName(),
                exists.stationType(),
                exists.area(),
                exists.address(),
                exists.longitude(),
                exists.latitude(),
                exists.contactPhone(),
                exists.openTime(),
                exists.distance(),
                exists.services(),
                STATUS_DELETED,
                exists.managerId(),
                exists.source(),
                exists.suspendedFrom(),
                exists.suspendedTo(),
                exists.suspendReason(),
                exists.createTime()
        ));
    }

    /**
     * 校验收件状态，只允许 OPEN / CLOSED。
     */
    private String requireOpenOrClosed(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException("驿站状态只能是 OPEN 或 CLOSED");
        }
        String normalized = status.trim().toUpperCase();
        if (!STATUS_OPEN.equals(normalized) && !STATUS_CLOSED.equals(normalized)) {
            throw new BusinessException("驿站状态只能是 OPEN 或 CLOSED");
        }
        return normalized;
    }

    /**
     * 查找未逻辑删除的驿站下标，找不到（含已删除）统一抛「驿站不存在」。
     */
    private int indexOfActive(Long id) {
        for (int index = 0; index < stations.size(); index++) {
            StationDto station = stations.get(index);
            if (station.id().equals(id) && !STATUS_DELETED.equals(station.status())) {
                return index;
            }
        }
        throw new BusinessException("驿站不存在");
    }

    private PageResult<StationDto> pageInternal(long current, long size, String cityCode, String area, String keyword,
                                               String service, String status, boolean excludeDeleted) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedCity = cityCode == null ? "" : cityCode.trim();
        String normalizedArea = area == null ? "" : area.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedService = service == null ? "" : service.trim();
        String normalizedStatus = status == null ? "" : status.trim();
        List<StationDto> filtered = stations.stream()
                .filter(item -> !excludeDeleted || !STATUS_DELETED.equals(item.status()))
                .filter(item -> normalizedCity.isBlank() || normalizedCity.equals(item.cityCode()))
                .filter(item -> normalizedStatus.isBlank() || item.status().equalsIgnoreCase(normalizedStatus))
                .filter(item -> normalizedArea.isBlank() || item.area().contains(normalizedArea))
                .filter(item -> normalizedService.isBlank()
                        || item.services().stream().anyMatch(name -> name.contains(normalizedService)))
                .filter(item -> normalizedKeyword.isBlank()
                        || item.stationName().contains(normalizedKeyword)
                        || item.address().contains(normalizedKeyword)
                        || item.services().stream().anyMatch(name -> name.contains(normalizedKeyword)))
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        return new PageResult<>(safeCurrent, safeSize, filtered.size(), new ArrayList<>(filtered.subList(from, to)));
    }
}
