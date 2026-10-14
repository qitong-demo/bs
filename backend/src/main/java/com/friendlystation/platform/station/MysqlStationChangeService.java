package com.friendlystation.platform.station;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.auth.DataScopeService;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.persistence.entity.Station;
import com.friendlystation.platform.persistence.entity.StationChangeRequest;
import com.friendlystation.platform.persistence.mapper.StationChangeRequestMapper;
import com.friendlystation.platform.persistence.mapper.StationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** mysql profile 下的驿站变更申请与审批。 */
@Service
@Profile("mysql")
public class MysqlStationChangeService implements StationChangeService {

    private final StationChangeRequestMapper requestMapper;
    private final StationMapper stationMapper;
    private final DataScopeService dataScopeService;

    public MysqlStationChangeService(StationChangeRequestMapper requestMapper, StationMapper stationMapper,
                                     DataScopeService dataScopeService) {
        this.requestMapper = requestMapper;
        this.stationMapper = stationMapper;
        this.dataScopeService = dataScopeService;
    }

    @Override
    @Transactional
    public Map<String, Object> submit(long applicantId, Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        long stationId = number(data.get("stationId"));
        String type = StationChangeService.requireType(String.valueOf(data.getOrDefault("type", "")));
        String newValue = text(data.get("newValue"));
        String reason = text(data.get("reason"));
        if (newValue.isEmpty()) {
            throw new BusinessException("请填写变更后的值");
        }
        if (reason.isEmpty()) {
            throw new BusinessException("请填写申请理由，审批人需要据此判断");
        }
        Station station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException("驿站不存在");
        }
        // 只能给自己负责的驿站提申请；平台级账号不受限（便于代提）
        CurrentUser operator = new CurrentUser(applicantId, null, "STATION_ADMIN", 0);
        List<Long> scoped = dataScopeService.stationIds(operator);
        if (scoped != null && !scoped.contains(stationId)) {
            throw new BusinessException("只能为自己负责的驿站提交变更申请");
        }

        StationChangeRequest request = new StationChangeRequest();
        request.setStationId(stationId);
        request.setApplicantId(applicantId);
        request.setChangeType(type);
        request.setOldValue(currentValueOf(station, type));
        request.setNewValue(newValue);
        request.setReason(reason);
        request.setStatus("PENDING");
        request.setCreateTime(LocalDateTime.now());
        requestMapper.insert(request);
        return view(request, station.getStationName(), true);
    }

    @Override
    public List<Map<String, Object>> listByStation(long stationId) {
        Station station = stationMapper.selectById(stationId);
        String name = station == null ? "" : station.getStationName();
        return requestMapper.selectList(new LambdaQueryWrapper<StationChangeRequest>()
                        .eq(StationChangeRequest::getStationId, stationId)
                        .orderByDesc(StationChangeRequest::getId))
                .stream().map(item -> view(item, name, true)).toList();
    }

    @Override
    public List<Map<String, Object>> pendingList() {
        return requestMapper.selectList(new LambdaQueryWrapper<StationChangeRequest>()
                        .eq(StationChangeRequest::getStatus, "PENDING")
                        .orderByAsc(StationChangeRequest::getId))
                .stream().map(item -> view(item, stationNameOf(item.getStationId()), true)).toList();
    }

    @Override
    @Transactional
    public Map<String, Object> approve(long reviewerId, long requestId, String reviewNote) {
        StationChangeRequest request = require(requestId);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException("该申请已处理，不能重复审批");
        }
        applyChange(request);
        request.setStatus("APPROVED");
        request.setReviewerId(reviewerId);
        request.setReviewNote(text(reviewNote));
        request.setReviewTime(LocalDateTime.now());
        requestMapper.updateById(request);
        return view(request, stationNameOf(request.getStationId()), true);
    }

    @Override
    @Transactional
    public Map<String, Object> reject(long reviewerId, long requestId, String reviewNote) {
        StationChangeRequest request = require(requestId);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException("该申请已处理，不能重复审批");
        }
        String note = text(reviewNote);
        // 驳回必须说明原因：驿站有权知道为什么改不了，否则会反复提交
        if (note.isEmpty()) {
            throw new BusinessException("驳回时请填写原因，驿站会看到这段说明");
        }
        request.setStatus("REJECTED");
        request.setReviewerId(reviewerId);
        request.setReviewNote(note);
        request.setReviewTime(LocalDateTime.now());
        requestMapper.updateById(request);
        return view(request, stationNameOf(request.getStationId()), true);
    }

    // ------------------------------------------------------------ 内部

    /** 通过后把新值写进驿站，并对位置变更标注来源。 */
    private void applyChange(StationChangeRequest request) {
        LambdaUpdateWrapper<Station> update = new LambdaUpdateWrapper<Station>()
                .eq(Station::getId, request.getStationId());
        switch (request.getChangeType()) {
            case "LOCATION" -> {
                String[] parts = request.getNewValue().split("[,，\\s]+");
                if (parts.length < 2) {
                    throw new BusinessException("位置格式应为「经度,纬度」");
                }
                BigDecimal lng;
                BigDecimal lat;
                try {
                    lng = new BigDecimal(parts[0].trim());
                    lat = new BigDecimal(parts[1].trim());
                } catch (NumberFormatException exception) {
                    throw new BusinessException("位置的经纬度必须是数字");
                }
                // 范围校验：写进去一个错误坐标比不写更糟，地图上会指向别处
                if (lng.doubleValue() < 73 || lng.doubleValue() > 136
                        || lat.doubleValue() < 3 || lat.doubleValue() > 54) {
                    throw new BusinessException("坐标不在中国境内合理范围内");
                }
                update.set(Station::getLongitude, lng).set(Station::getLatitude, lat)
                        // 来源留痕：现有坐标是高德 POI 派生并三重校验的，人工改动必须区分开
                        .set(Station::getGeocodeSource, "MANUAL_APPROVED")
                        .set(Station::getGeocodeAt, LocalDate.now());
            }
            case "BUSINESS_HOURS" -> update.set(Station::getOpenTime, request.getNewValue());
            case "PHONE" -> update.set(Station::getContactPhone, request.getNewValue());
            case "ADDRESS" -> update.set(Station::getAddress, request.getNewValue());
            default -> throw new BusinessException("暂不支持该变更类型");
        }
        // 人工核准过的记录，在 data_note 里追加说明，保持来源口径一致
        Station station = stationMapper.selectById(request.getStationId());
        if (station != null) {
            String note = station.getDataNote() == null ? "" : station.getDataNote();
            String addition = "「" + labelOf(request.getChangeType()) + "」由驿站申请、管理员于 "
                    + LocalDate.now() + " 核准变更";
            update.set(Station::getDataNote, note.isEmpty() ? addition : note + "；" + addition);
        }
        update.set(Station::getUpdateTime, LocalDateTime.now());
        stationMapper.update(null, update);
    }

    private StationChangeRequest require(long requestId) {
        StationChangeRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException("变更申请不存在");
        }
        return request;
    }

    private String stationNameOf(Long stationId) {
        if (stationId == null) {
            return "";
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? "" : station.getStationName();
    }

    private static String currentValueOf(Station station, String type) {
        return switch (type) {
            case "LOCATION" -> station.getLongitude() == null || station.getLatitude() == null
                    ? "" : station.getLongitude() + "," + station.getLatitude();
            case "BUSINESS_HOURS" -> station.getOpenTime() == null ? "" : station.getOpenTime();
            case "PHONE" -> station.getContactPhone() == null ? "" : station.getContactPhone();
            case "ADDRESS" -> station.getAddress() == null ? "" : station.getAddress();
            default -> "";
        };
    }

    static String labelOf(String type) {
        return switch (type) {
            case "LOCATION" -> "位置";
            case "BUSINESS_HOURS" -> "营业时间";
            case "PHONE" -> "联系电话";
            case "ADDRESS" -> "地址";
            default -> "其他";
        };
    }

    static Map<String, Object> view(StationChangeRequest request, String stationName, boolean includeStationName) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", request.getId());
        out.put("stationId", request.getStationId());
        if (includeStationName) {
            out.put("stationName", stationName == null ? "" : stationName);
        }
        out.put("type", request.getChangeType());
        out.put("typeLabel", labelOf(request.getChangeType()));
        out.put("oldValue", request.getOldValue() == null ? "" : request.getOldValue());
        out.put("newValue", request.getNewValue() == null ? "" : request.getNewValue());
        out.put("reason", request.getReason() == null ? "" : request.getReason());
        out.put("status", request.getStatus());
        out.put("statusLabel", statusLabelOf(request.getStatus()));
        out.put("reviewNote", request.getReviewNote() == null ? "" : request.getReviewNote());
        out.put("reviewTime", FormatUtils.dateTime(request.getReviewTime()));
        out.put("createTime", FormatUtils.dateTime(request.getCreateTime()));
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    static String statusLabelOf(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "待审批";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "CANCELLED" -> "已撤回";
            default -> status == null ? "" : status;
        };
    }

    private static long number(Object value) {
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception exception) {
            throw new BusinessException("驿站编号不合法");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
