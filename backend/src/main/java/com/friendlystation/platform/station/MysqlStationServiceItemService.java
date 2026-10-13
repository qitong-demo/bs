package com.friendlystation.platform.station;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.Station;
import com.friendlystation.platform.persistence.entity.StationServiceItem;
import com.friendlystation.platform.persistence.mapper.StationMapper;
import com.friendlystation.platform.persistence.mapper.StationServiceItemMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库版驿站服务项目管理（mysql profile），与 MockStationServiceItemService 对外行为保持一致。
 */
@Service
@Profile("mysql")
public class MysqlStationServiceItemService implements StationServiceItemService {
    private static final String DEFAULT_SERVICE_TYPE = "GENERAL";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final StationServiceItemMapper serviceItemMapper;
    private final StationMapper stationMapper;

    public MysqlStationServiceItemService(StationServiceItemMapper serviceItemMapper, StationMapper stationMapper) {
        this.serviceItemMapper = serviceItemMapper;
        this.stationMapper = stationMapper;
    }

    @Override
    public PageResult<StationServiceItemDto> page(long current, long size, Long stationId, String keyword) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<StationServiceItem> query = new LambdaQueryWrapper<>();
        if (stationId != null) {
            query.eq(StationServiceItem::getStationId, stationId);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.like(StationServiceItem::getServiceName, keyword.trim());
        }
        query.orderByAsc(StationServiceItem::getId);
        Page<StationServiceItem> result = serviceItemMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toDtoList(result.getRecords()));
    }

    @Override
    public List<StationServiceItemDto> listByStation(Long stationId) {
        if (stationId == null) {
            return List.of();
        }
        List<StationServiceItem> items = serviceItemMapper.selectList(new LambdaQueryWrapper<StationServiceItem>()
                .eq(StationServiceItem::getStationId, stationId)
                .orderByAsc(StationServiceItem::getId));
        return toDtoList(items);
    }

    @Override
    @Transactional
    public StationServiceItemDto create(StationServiceItemRequest request) {
        Station station = requireStation(request.stationId());
        StationServiceItem item = new StationServiceItem();
        item.setStationId(station.getId());
        item.setServiceName(request.serviceName().trim());
        item.setServiceType(normalizeServiceType(request.serviceType()));
        item.setNeedAppoint(request.needAppoint() == null ? Boolean.FALSE : request.needAppoint());
        item.setStatus(request.status() == null ? STATUS_ENABLED : requireStatus(request.status()));
        item.setCreateTime(LocalDateTime.now());
        serviceItemMapper.insert(item);
        return toDto(item, station.getStationName());
    }

    @Override
    @Transactional
    public StationServiceItemDto update(Long id, StationServiceItemRequest request) {
        StationServiceItem exists = requireItem(id);
        Station station = requireStation(request.stationId());
        StationServiceItem item = new StationServiceItem();
        item.setId(exists.getId());
        item.setStationId(station.getId());
        item.setServiceName(request.serviceName().trim());
        item.setServiceType(normalizeServiceType(request.serviceType()));
        item.setNeedAppoint(request.needAppoint() == null ? Boolean.FALSE : request.needAppoint());
        item.setStatus(request.status() == null ? STATUS_ENABLED : requireStatus(request.status()));
        serviceItemMapper.updateById(item);
        // createTime 不允许被编辑，重新查询保证返回值与库内一致
        return toDto(requireItem(id), station.getStationName());
    }

    @Override
    @Transactional
    public StationServiceItemDto updateStatus(Long id, Integer status) {
        int normalizedStatus = requireStatus(status);
        requireItem(id);
        StationServiceItem item = new StationServiceItem();
        item.setId(id);
        item.setStatus(normalizedStatus);
        serviceItemMapper.updateById(item);
        return toDto(requireItem(id), null);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireItem(id);
        serviceItemMapper.deleteById(id);
    }

    /**
     * 校验收件是否存在，不存在时抛「驿站不存在」。
     */
    private Station requireStation(Long stationId) {
        Station station = stationId == null ? null : stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException("驿站不存在");
        }
        return station;
    }

    /**
     * 校验服务项目是否存在，不存在时抛「服务项目不存在」。
     */
    private StationServiceItem requireItem(Long id) {
        StationServiceItem item = id == null ? null : serviceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("服务项目不存在");
        }
        return item;
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

    /**
     * 批量查询驿站名称，避免逐条查询。
     */
    private List<StationServiceItemDto> toDtoList(List<StationServiceItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Long> stationIds = items.stream()
                .map(StationServiceItem::getStationId)
                .distinct()
                .toList();
        Map<Long, String> stationNames = stationMapper.selectBatchIds(stationIds).stream()
                .collect(Collectors.toMap(Station::getId, Station::getStationName, (first, second) -> first));
        return items.stream()
                .map(item -> toDto(item, stationNames.get(item.getStationId())))
                .toList();
    }

    /**
     * @param stationName 已知的驿站名称，为 null 时按 stationId 回查
     */
    private StationServiceItemDto toDto(StationServiceItem item, String stationName) {
        String resolvedStationName = stationName;
        if (resolvedStationName == null && item.getStationId() != null) {
            Station station = stationMapper.selectById(item.getStationId());
            resolvedStationName = station == null ? "" : station.getStationName();
        }
        return new StationServiceItemDto(
                item.getId(),
                item.getStationId(),
                resolvedStationName == null ? "" : resolvedStationName,
                item.getServiceName(),
                item.getServiceType(),
                item.getNeedAppoint(),
                item.getStatus(),
                FormatUtils.dateTime(item.getCreateTime())
        );
    }
}
