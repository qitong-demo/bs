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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库版驿站服务（mysql profile），与 MockStationService 对外行为保持一致。
 * 驿站记录带有来源与核对信息，后台手工新增的记录标记为 MANUAL。
 */
@Service
@Profile("mysql")
public class MysqlStationService implements StationService {
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String DEFAULT_TYPE = "未分类";
    private static final String DEFAULT_CITY_CODE = "330100";
    private static final String MANUAL_STATUS = "MANUAL";
    private static final String MANUAL_NOTE = "后台录入，未标注公开来源";

    private final StationMapper stationMapper;
    private final StationServiceItemMapper serviceItemMapper;

    public MysqlStationService(StationMapper stationMapper, StationServiceItemMapper serviceItemMapper) {
        this.stationMapper = stationMapper;
        this.serviceItemMapper = serviceItemMapper;
    }

    @Override
    public PageResult<StationDto> page(long current, long size, String cityCode, String area, String keyword,
                                      String service, String status) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        List<Long> serviceStationIds = null;
        if (service != null && !service.isBlank()) {
            serviceStationIds = serviceItemIds(service);
            if (serviceStationIds.isEmpty()) {
                return new PageResult<>(safeCurrent, safeSize, 0, List.of());
            }
        }
        LambdaQueryWrapper<Station> query = filterQuery(cityCode, area, keyword, serviceStationIds)
                .ne(Station::getStatus, STATUS_DELETED)
                .orderByAsc(Station::getId);
        if (status != null && !status.isBlank()) {
            query.eq(Station::getStatus, status.trim().toUpperCase());
        }
        Page<Station> result = stationMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        return toPageResult(result);
    }

    @Override
    public PageResult<StationDto> adminPage(long current, long size, String cityCode, String area, String keyword,
                                            String status) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<Station> query = filterQuery(cityCode, area, keyword, null);
        if (status != null && !status.isBlank()) {
            query.eq(Station::getStatus, status.trim());
        }
        query.orderByAsc(Station::getId);
        Page<Station> result = stationMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        return toPageResult(result);
    }

    @Override
    public List<Map<String, Object>> areaSummary(String cityCode) {
        LambdaQueryWrapper<Station> query = new LambdaQueryWrapper<Station>()
                .select(Station::getArea)
                .ne(Station::getStatus, STATUS_DELETED);
        if (cityCode != null && !cityCode.isBlank()) {
            query.eq(Station::getCityCode, cityCode.trim());
        }
        Map<String, Long> counts = stationMapper.selectList(query).stream()
                .map(Station::getArea)
                .filter(area -> area != null && !area.isBlank())
                .collect(Collectors.groupingBy(area -> area, Collectors.counting()));
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
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new BusinessException("驿站不存在");
        }
        return toDto(station);
    }

    @Override
    @Transactional
    public StationDto create(StationCreateRequest request) {
        Station station = new Station();
        station.setStationName(request.stationName());
        station.setCityCode(request.cityCode() == null || request.cityCode().isBlank()
                ? DEFAULT_CITY_CODE : request.cityCode().trim());
        station.setArea(request.area());
        station.setAddress(request.address());
        station.setContactPhone(request.contactPhone());
        station.setOpenTime(request.openTime());
        station.setStatus(request.status() == null || request.status().isBlank() ? STATUS_OPEN : request.status().trim());
        station.setStationType(request.stationType() == null || request.stationType().isBlank()
                ? DEFAULT_TYPE : request.stationType().trim());
        // 后台录入的数据没有公开来源，标记为 MANUAL，前端据此与公开来源数据区分展示。
        station.setDataStatus(MANUAL_STATUS);
        station.setDataNote(MANUAL_NOTE);
        station.setVerifiedAt(java.time.LocalDate.now());
        stationMapper.insert(station);
        return toDto(stationMapper.selectById(station.getId()));
    }

    @Override
    @Transactional
    public StationDto update(Long id, StationUpdateRequest request) {
        if (!existsActive(id)) {
            throw new BusinessException("驿站不存在");
        }
        Station station = new Station();
        station.setId(id);
        station.setStationName(request.stationName());
        station.setArea(request.area());
        station.setAddress(request.address());
        // 可空字段为 null 视为保留原值，与内存实现保持一致
        if (request.contactPhone() != null) {
            station.setContactPhone(request.contactPhone());
        }
        if (request.openTime() != null) {
            station.setOpenTime(request.openTime());
        }
        if (request.status() != null && !request.status().isBlank()) {
            station.setStatus(request.status().trim());
        }
        if (request.stationType() != null && !request.stationType().isBlank()) {
            station.setStationType(request.stationType().trim());
        }
        if (request.cityCode() != null && !request.cityCode().isBlank()) {
            station.setCityCode(request.cityCode().trim());
        }
        stationMapper.updateById(station);
        return toDto(stationMapper.selectById(id));
    }

    @Override
    @Transactional
    public StationDto updateStatus(Long id, String status) {
        String normalizedStatus = requireOpenOrClosed(status);
        if (!existsActive(id)) {
            throw new BusinessException("驿站不存在");
        }
        Station station = new Station();
        station.setId(id);
        station.setStatus(normalizedStatus);
        stationMapper.updateById(station);
        return toDto(stationMapper.selectById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!existsActive(id)) {
            throw new BusinessException("驿站不存在");
        }
        Station station = new Station();
        station.setId(id);
        station.setStatus(STATUS_DELETED);
        stationMapper.updateById(station);
    }

    @Override
    public List<StationDto> listByManager(Long managerId) {
        if (managerId == null) {
            return List.of();
        }
        return stationMapper.selectList(new LambdaQueryWrapper<Station>()
                        .eq(Station::getManagerId, managerId)
                        .ne(Station::getStatus, STATUS_DELETED)
                        .orderByAsc(Station::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 组装区域、关键词与服务项目过滤条件。
     *
     * @param serviceStationIds 已解析出的「提供指定服务」的驿站编号；为 null 表示不做服务过滤
     */
    private LambdaQueryWrapper<Station> filterQuery(String cityCode, String area, String keyword,
                                                    List<Long> serviceStationIds) {
        LambdaQueryWrapper<Station> query = new LambdaQueryWrapper<>();
        if (cityCode != null && !cityCode.isBlank()) {
            query.eq(Station::getCityCode, cityCode.trim());
        }
        if (area != null && !area.isBlank()) {
            query.like(Station::getArea, area.trim());
        }
        if (serviceStationIds != null) {
            query.in(Station::getId, serviceStationIds);
        }
        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim();
            List<Long> stationIdsByService = serviceItemIds(normalizedKeyword);
            query.and(wrapper -> {
                wrapper.like(Station::getStationName, normalizedKeyword)
                        .or()
                        .like(Station::getAddress, normalizedKeyword);
                if (!stationIdsByService.isEmpty()) {
                    wrapper.or().in(Station::getId, stationIdsByService);
                }
            });
        }
        return query;
    }

    /** 按服务项目名称反查驿站编号。 */
    private List<Long> serviceItemIds(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return List.of();
        }
        return serviceItemMapper.selectList(new LambdaQueryWrapper<StationServiceItem>()
                        .select(StationServiceItem::getStationId)
                        .like(StationServiceItem::getServiceName, serviceName.trim()))
                .stream()
                .map(StationServiceItem::getStationId)
                .distinct()
                .toList();
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
     * 判断驿站是否存在且未被逻辑删除；已删除的驿站视为不存在。
     */
    private boolean existsActive(Long id) {
        Station station = stationMapper.selectById(id);
        return station != null && !STATUS_DELETED.equals(station.getStatus());
    }

    private PageResult<StationDto> toPageResult(Page<Station> result) {
        List<StationDto> records = result.getRecords().stream().map(this::toDto).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    private StationDto toDto(Station station) {
        List<String> services = serviceItemMapper.selectList(new LambdaQueryWrapper<StationServiceItem>()
                        .eq(StationServiceItem::getStationId, station.getId())
                        .eq(StationServiceItem::getStatus, 1)
                        .orderByAsc(StationServiceItem::getId))
                .stream()
                .map(StationServiceItem::getServiceName)
                .toList();
        return new StationDto(
                station.getId(),
                text(station.getCityCode()),
                station.getStationName(),
                text(station.getStationType()),
                station.getArea(),
                station.getAddress(),
                toDouble(station.getLongitude()),
                toDouble(station.getLatitude()),
                station.getContactPhone(),
                station.getOpenTime(),
                "—",
                services,
                station.getStatus(),
                station.getManagerId(),
                new StationSource(
                        text(station.getSourceName()),
                        text(station.getSourceUrl()),
                        FormatUtils.date(station.getSourcePublishedAt()),
                        FormatUtils.date(station.getVerifiedAt()),
                        station.getDataStatus() == null || station.getDataStatus().isBlank()
                                ? "UNKNOWN" : station.getDataStatus(),
                        text(station.getDataNote()),
                        text(station.getGeocodeSource()),
                        FormatUtils.date(station.getGeocodeAt()),
                        text(station.getBusinessSource()),
                        FormatUtils.date(station.getBusinessSyncedAt())),
                FormatUtils.dateTime(station.getCreateTime())
        );
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static Double toDouble(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
