package com.friendlystation.platform.station;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版驿站服务项目管理（非 mysql profile）。
 * 种子数据来自 {@link MockStationData}（classpath:db/stations.json），与 MySQL 初始化脚本同源；
 * 内存实现没有数据库创建时间，创建时间使用当前时间字符串。
 */
@Service
@Profile("!mysql")
public class MockStationServiceItemService implements StationServiceItemService {
    private static final String DEFAULT_SERVICE_TYPE = "GENERAL";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final MockStationService stationService;
    private final AtomicLong idGenerator;
    private final List<StationServiceItemDto> serviceItems;

    public MockStationServiceItemService(MockStationService stationService, MockStationData data) {
        this.stationService = stationService;
        String seedTime = FormatUtils.dateTime(LocalDateTime.now());
        List<StationServiceItemDto> seeds = new ArrayList<>();
        for (MockStationData.ServiceSeed seed : data.serviceSeeds()) {
            seeds.add(new StationServiceItemDto(
                    seed.id(),
                    seed.stationId(),
                    stationService.detail(seed.stationId()).stationName(),
                    seed.serviceName(),
                    seed.serviceType(),
                    Boolean.FALSE,
                    STATUS_ENABLED,
                    seedTime));
        }
        this.serviceItems = new CopyOnWriteArrayList<>(seeds);
        this.idGenerator = new AtomicLong(data.serviceSeeds().stream()
                .mapToLong(MockStationData.ServiceSeed::id)
                .max()
                .orElse(0L));
    }

    @Override
    public PageResult<StationServiceItemDto> page(long current, long size, Long stationId, String keyword,
                                                  java.util.List<Long> stationIds) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        if (stationIds != null && stationIds.isEmpty()) {
            return new PageResult<>(safeCurrent, safeSize, 0, new ArrayList<>());
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<StationServiceItemDto> filtered = serviceItems.stream()
                .filter(item -> stationIds == null || stationIds.contains(item.stationId()))
                .filter(item -> stationId == null || item.stationId().equals(stationId))
                .filter(item -> normalizedKeyword.isBlank() || item.serviceName().contains(normalizedKeyword))
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        return new PageResult<>(safeCurrent, safeSize, filtered.size(), new ArrayList<>(filtered.subList(from, to)));
    }

    @Override
    public List<StationServiceItemDto> listByStation(Long stationId) {
        if (stationId == null) {
            return List.of();
        }
        return serviceItems.stream()
                .filter(item -> item.stationId().equals(stationId))
                .toList();
    }

    @Override
    public StationServiceItemDto detail(Long id) {
        return serviceItems.get(indexOf(id));
    }

    @Override
    public StationServiceItemDto create(StationServiceItemRequest request) {
        StationDto station = requireStation(request.stationId());
        long id = idGenerator.incrementAndGet();
        StationServiceItemDto item = new StationServiceItemDto(
                id,
                station.id(),
                station.stationName(),
                request.serviceName().trim(),
                normalizeServiceType(request.serviceType()),
                request.needAppoint() == null ? Boolean.FALSE : request.needAppoint(),
                request.status() == null ? STATUS_ENABLED : requireStatus(request.status()),
                FormatUtils.dateTime(LocalDateTime.now())
        );
        serviceItems.add(item);
        return item;
    }

    @Override
    public StationServiceItemDto update(Long id, StationServiceItemRequest request) {
        int index = indexOf(id);
        StationDto station = requireStation(request.stationId());
        StationServiceItemDto exists = serviceItems.get(index);
        StationServiceItemDto updated = new StationServiceItemDto(
                exists.id(),
                station.id(),
                station.stationName(),
                request.serviceName().trim(),
                normalizeServiceType(request.serviceType()),
                request.needAppoint() == null ? Boolean.FALSE : request.needAppoint(),
                request.status() == null ? STATUS_ENABLED : requireStatus(request.status()),
                exists.createTime()
        );
        serviceItems.set(index, updated);
        return updated;
    }

    @Override
    public StationServiceItemDto updateStatus(Long id, Integer status) {
        int normalizedStatus = requireStatus(status);
        int index = indexOf(id);
        StationServiceItemDto exists = serviceItems.get(index);
        StationServiceItemDto updated = new StationServiceItemDto(
                exists.id(),
                exists.stationId(),
                exists.stationName(),
                exists.serviceName(),
                exists.serviceType(),
                exists.needAppoint(),
                normalizedStatus,
                exists.createTime()
        );
        serviceItems.set(index, updated);
        return updated;
    }

    @Override
    public void delete(Long id) {
        serviceItems.remove(indexOf(id));
    }

    /**
     * 校验收件是否存在，不存在时抛「驿站不存在」。
     */
    private StationDto requireStation(Long stationId) {
        return stationService.detail(stationId);
    }

    /**
     * 校验服务项目状态，只允许 1（启用）或 0（停用）。
     */
    private int requireStatus(Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BusinessException("服务项目状态只能是 1 或 0");
        }
        return status;
    }

    private String normalizeServiceType(String serviceType) {
        return serviceType == null || serviceType.isBlank() ? DEFAULT_SERVICE_TYPE : serviceType.trim();
    }

    private int indexOf(Long id) {
        for (int index = 0; index < serviceItems.size(); index++) {
            if (serviceItems.get(index).id().equals(id)) {
                return index;
            }
        }
        throw new BusinessException("服务项目不存在");
    }
}
