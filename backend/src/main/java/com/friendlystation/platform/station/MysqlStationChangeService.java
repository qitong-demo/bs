package com.friendlystation.platform.station;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.auth.DataScopeService;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.persistence.entity.Station;
import com.friendlystation.platform.persistence.entity.StationChangeRequest;
import com.friendlystation.platform.persistence.entity.SysRole;
import com.friendlystation.platform.persistence.entity.SysUserRole;
import com.friendlystation.platform.persistence.mapper.StationChangeRequestMapper;
import com.friendlystation.platform.persistence.mapper.StationMapper;
import com.friendlystation.platform.persistence.mapper.SysRoleMapper;
import com.friendlystation.platform.persistence.mapper.SysUserRoleMapper;
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
    private final com.friendlystation.platform.notification.NotificationService notificationService;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public MysqlStationChangeService(StationChangeRequestMapper requestMapper, StationMapper stationMapper,
                                     DataScopeService dataScopeService,
                                     com.friendlystation.platform.notification.NotificationService notificationService,
                                     SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper) {
        this.requestMapper = requestMapper;
        this.stationMapper = stationMapper;
        this.dataScopeService = dataScopeService;
        this.notificationService = notificationService;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    @Transactional
    public Map<String, Object> submit(CurrentUser applicant, Map<String, Object> payload) {
        long applicantId = applicant.id();
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
        // 只能给自己负责的驿站提申请；平台级账号不受限（便于代提）。
        // 注意这里必须用真实 CurrentUser：此前伪造了一个 STATION_ADMIN 角色，
        // 结果平台管理员反而被当成「没有负责驿站」而拒绝。
        if (!dataScopeService.isPlatformLevel(applicant)) {
            List<Long> scoped = dataScopeService.stationIds(applicant);
            if (scoped != null && !scoped.contains(stationId)) {
                throw new BusinessException("只能为自己负责的驿站提交变更申请");
            }
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
        notifyReviewers(request, station.getStationName());
        return view(request, station.getStationName(), true);
    }

    @Override
    public List<Map<String, Object>> listByStation(CurrentUser viewer, long stationId) {
        if (!dataScopeService.isPlatformLevel(viewer)) {
            List<Long> scoped = dataScopeService.stationIds(viewer);
            if (scoped == null || !scoped.contains(stationId)) {
                throw new com.friendlystation.platform.common.ForbiddenException("无权查看该驿站的变更申请");
            }
        }
        Station station = stationMapper.selectById(stationId);
        String name = station == null ? "" : station.getStationName();
        return requestMapper.selectList(new LambdaQueryWrapper<StationChangeRequest>()
                        .eq(StationChangeRequest::getStationId, stationId)
                        .orderByDesc(StationChangeRequest::getId))
                .stream().map(item -> view(item, name, true)).toList();
    }

    @Override
    public List<Map<String, Object>> mine(CurrentUser viewer) {
        LambdaQueryWrapper<StationChangeRequest> query = new LambdaQueryWrapper<>();
        if (!dataScopeService.isPlatformLevel(viewer)) {
            List<Long> scoped = dataScopeService.stationIds(viewer);
            if (scoped == null || scoped.isEmpty()) {
                return List.of();
            }
            query.in(StationChangeRequest::getStationId, scoped);
        }
        query.orderByDesc(StationChangeRequest::getId);
        return requestMapper.selectList(query).stream()
                .map(item -> view(item, stationNameOf(item.getStationId()), true))
                .toList();
    }

    @Override
    public List<Map<String, Object>> pendingList(CurrentUser viewer) {
        LambdaQueryWrapper<StationChangeRequest> query = new LambdaQueryWrapper<StationChangeRequest>()
                .eq(StationChangeRequest::getStatus, "PENDING");
        if (!dataScopeService.isPlatformLevel(viewer)) {
            List<Long> scoped = dataScopeService.stationIds(viewer);
            if (scoped == null || scoped.isEmpty()) {
                return List.of();
            }
            query.in(StationChangeRequest::getStationId, scoped);
        }
        query.orderByAsc(StationChangeRequest::getId);
        return requestMapper.selectList(query).stream()
                .map(item -> view(item, stationNameOf(item.getStationId()), true))
                .toList();
    }

    @Override
    @Transactional
    public Map<String, Object> approve(CurrentUser reviewer, long requestId, String reviewNote) {
        long reviewerId = reviewer.id();
        StationChangeRequest request = require(requestId);
        requireReviewerScope(reviewer, request, "审批");
        // 条件更新：并发/重复审批时只有一个能成功，避免 applyChange 被执行两次、
        // 或「已通过」被再次改成「已驳回」。
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<StationChangeRequest>()
                .eq(StationChangeRequest::getId, requestId)
                .eq(StationChangeRequest::getStatus, "PENDING")
                .set(StationChangeRequest::getStatus, "APPROVED")
                .set(StationChangeRequest::getReviewerId, reviewerId)
                .set(StationChangeRequest::getReviewNote, text(reviewNote))
                .set(StationChangeRequest::getReviewTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("该申请已处理，不能重复审批");
        }
        applyChange(request);
        notifyApplicant(request, "变更申请已通过",
                labelOf(request.getChangeType()) + "：" + request.getNewValue() + " 已核准");
        return view(require(requestId), stationNameOf(request.getStationId()), true);
    }

    @Override
    @Transactional
    public Map<String, Object> reject(CurrentUser reviewer, long requestId, String reviewNote) {
        long reviewerId = reviewer.id();
        StationChangeRequest request = require(requestId);
        requireReviewerScope(reviewer, request, "驳回");
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException("该申请已处理，不能重复审批");
        }
        String note = text(reviewNote);
        // 驳回必须说明原因：驿站有权知道为什么改不了，否则会反复提交
        if (note.isEmpty()) {
            throw new BusinessException("驳回时请填写原因，驿站会看到这段说明");
        }
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<StationChangeRequest>()
                .eq(StationChangeRequest::getId, requestId)
                .eq(StationChangeRequest::getStatus, "PENDING")
                .set(StationChangeRequest::getStatus, "REJECTED")
                .set(StationChangeRequest::getReviewerId, reviewerId)
                .set(StationChangeRequest::getReviewNote, note)
                .set(StationChangeRequest::getReviewTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("该申请已处理，不能重复审批");
        }
        notifyApplicant(request, "变更申请已驳回",
                labelOf(request.getChangeType()) + "：" + note);
        return view(require(requestId), stationNameOf(request.getStationId()), true);
    }

    // ------------------------------------------------------------ 内部

    @Override
    @Transactional
    public Map<String, Object> cancel(CurrentUser applicant, long requestId) {
        StationChangeRequest request = require(requestId);
        if (request.getApplicantId() == null || !request.getApplicantId().equals(applicant.id())) {
            throw new com.friendlystation.platform.common.ForbiddenException("只能撤回自己提交的申请");
        }
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<StationChangeRequest>()
                .eq(StationChangeRequest::getId, requestId)
                .eq(StationChangeRequest::getApplicantId, applicant.id())
                .eq(StationChangeRequest::getStatus, "PENDING")
                .set(StationChangeRequest::getStatus, "CANCELLED")
                .set(StationChangeRequest::getReviewTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("该申请已处理，不能撤回");
        }
        return view(require(requestId), stationNameOf(request.getStationId()), true);
    }

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

    /** 新申请提醒平台管理员处理；申请人本人不重复收通知。 */
    private void notifyReviewers(StationChangeRequest request, String stationName) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "PLATFORM_ADMIN"));
        if (role == null) {
            return;
        }
        List<Long> adminIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId()))
                .stream().map(SysUserRole::getUserId).filter(java.util.Objects::nonNull).distinct().toList();
        String content = stationName + " · " + labelOf(request.getChangeType()) + " · " + request.getNewValue();
        for (Long adminId : adminIds) {
            if (adminId.equals(request.getApplicantId())) {
                continue;
            }
            notificationService.push(adminId, "SYSTEM", "新的驿站变更申请", content,
                    "", "STATION_CHANGE", request.getId());
        }
    }

    /** 审批结果写回申请人：驿站端不必反复刷新列表才知道结果。 */
    private void notifyApplicant(StationChangeRequest request, String title, String content) {
        if (request.getApplicantId() == null) {
            return;
        }
        notificationService.push(request.getApplicantId(), "SYSTEM", title, content,
                "", "STATION_CHANGE", request.getId());
    }

    private StationChangeRequest require(long requestId) {        StationChangeRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException("变更申请不存在");
        }
        return request;
    }

    /**
     * 审批人校验：平台级不受限；驿站管理员只能审批自己负责驿站、且不能审批自己提交的申请。
     */
    private void requireReviewerScope(CurrentUser reviewer, StationChangeRequest request, String action) {
        if (dataScopeService.isPlatformLevel(reviewer)) {
            return;
        }
        if (request.getApplicantId() != null && request.getApplicantId().equals(reviewer.id())) {
            throw new com.friendlystation.platform.common.ForbiddenException("不能" + action + "自己提交的申请");
        }
        List<Long> scoped = dataScopeService.stationIds(reviewer);
        if (scoped == null || !scoped.contains(request.getStationId())) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权" + action + "该驿站的申请");
        }
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
        out.put("applicantId", request.getApplicantId() == null ? 0L : request.getApplicantId());
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
