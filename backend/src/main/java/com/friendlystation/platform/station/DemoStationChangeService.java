package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.common.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存 profile 下的驿站变更申请。
 *
 * <p><b>已知能力边界（如实说明）</b>：演示模式没有数据库，且现有的
 * {@link StationUpdateRequest} 不支持经纬度，因此**位置（LOCATION）变更在演示模式下不生效**，
 * 提交能被记录，审批时会明确报错而不是静默成功——静默成功会让人以为坐标改了。</p>
 *
 * <p>营业时间、联系电话、地址三项通过 {@link StationService#update} 正常生效。</p>
 */
@Service
@Profile("!mysql")
public class DemoStationChangeService implements StationChangeService {

    private final StationService stationService;
    private final com.friendlystation.platform.auth.DataScopeService dataScopeService;
    private final com.friendlystation.platform.notification.NotificationService notificationService;
    private final com.friendlystation.platform.auth.MockAccountService accountService;
    private final List<Map<String, Object>> requests = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public DemoStationChangeService(StationService stationService,
                                    com.friendlystation.platform.auth.DataScopeService dataScopeService,
                                    com.friendlystation.platform.notification.NotificationService notificationService,
                                    com.friendlystation.platform.auth.MockAccountService accountService) {
        this.stationService = stationService;
        this.dataScopeService = dataScopeService;
        this.notificationService = notificationService;
        this.accountService = accountService;
    }

    @Override
    public synchronized Map<String, Object> submit(CurrentUser applicant, Map<String, Object> payload) {
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
        String stationName;
        try {
            stationName = stationService.detail(stationId).stationName();
        } catch (Exception exception) {
            throw new BusinessException("驿站不存在");
        }
        if (!dataScopeService.isPlatformLevel(applicant)) {
            List<Long> scoped = dataScopeService.stationIds(applicant);
            if (scoped == null || !scoped.contains(stationId)) {
                throw new BusinessException("只能为自己负责的驿站提交变更申请");
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", idGenerator.incrementAndGet());
        row.put("stationId", stationId);
        row.put("stationName", stationName);
        row.put("type", type);
        row.put("typeLabel", MysqlStationChangeService.labelOf(type));
        row.put("oldValue", currentValueOf(stationId, type));
        row.put("newValue", newValue);
        row.put("reason", reason);
        row.put("status", "PENDING");
        row.put("statusLabel", MysqlStationChangeService.statusLabelOf("PENDING"));
        row.put("reviewNote", "");
        row.put("reviewTime", "");
        row.put("createTime", LocalDateTime.now().toString());
        row.put("dataStatus", "系统计算结果");
        row.put("applicantId", applicantId);
        requests.add(row);
        notifyReviewers(row, stationName);
        return copyOf(row);
    }

    @Override
    public synchronized List<Map<String, Object>> listByStation(CurrentUser viewer, long stationId) {
        if (!dataScopeService.isPlatformLevel(viewer)) {
            List<Long> scoped = dataScopeService.stationIds(viewer);
            if (scoped == null || !scoped.contains(stationId)) {
                throw new com.friendlystation.platform.common.ForbiddenException("无权查看该驿站的变更申请");
            }
        }
        return requests.stream()
                .filter(item -> number(item.get("stationId")) == stationId)
                .map(DemoStationChangeService::copyOf)
                .toList();
    }

    @Override
    public synchronized List<Map<String, Object>> mine(CurrentUser viewer) {
        List<Long> scoped = dataScopeService.stationIds(viewer);
        return requests.stream()
                .filter(item -> scoped == null || scoped.contains(number(item.get("stationId"))))
                .map(DemoStationChangeService::copyOf)
                .toList();
    }

    @Override
    public synchronized List<Map<String, Object>> pendingList(CurrentUser viewer) {
        List<Long> scoped = dataScopeService.stationIds(viewer);
        return requests.stream()
                .filter(item -> "PENDING".equals(item.get("status")))
                .filter(item -> scoped == null || scoped.contains(number(item.get("stationId"))))
                .map(DemoStationChangeService::copyOf)
                .toList();
    }

    @Override
    public synchronized Map<String, Object> approve(CurrentUser reviewer, long requestId, String reviewNote) {
        long reviewerId = reviewer.id();
        Map<String, Object> row = require(requestId);
        if (reviewerId == number(row.get("applicantId"))) {
            throw new com.friendlystation.platform.common.ForbiddenException("不能审批自己提交的申请");
        }
        // 与 mysql 实现同口径：只有待审批的申请能通过，否则会出现「已通过 → 再通过/再驳回」。
        if (!"PENDING".equals(row.get("status"))) {
            throw new BusinessException("该申请已处理，不能重复审批");
        }
        // 位置变更在演示模式下无法生效：StationUpdateRequest 不支持经纬度。
        // 明确报错而不是假装成功——静默成功会让人以为坐标真的改了。
        if ("LOCATION".equals(row.get("type"))) {
            throw new BusinessException("演示模式不支持修改驿站坐标（内存实现没有坐标字段的写入通道），"
                    + "请在 MySQL 模式下审批位置变更");
        }
        applyChange(row);
        row.put("status", "APPROVED");
        row.put("statusLabel", MysqlStationChangeService.statusLabelOf("APPROVED"));
        row.put("reviewNote", text(reviewNote));
        row.put("reviewTime", LocalDateTime.now().toString());
        row.put("reviewerId", reviewerId);
        notifyApplicant(row, "变更申请已通过",
                MysqlStationChangeService.labelOf(String.valueOf(row.get("type"))) + "："
                        + row.get("newValue") + " 已核准");
        return copyOf(row);
    }

    @Override
    public synchronized Map<String, Object> reject(CurrentUser reviewer, long requestId, String reviewNote) {
        long reviewerId = reviewer.id();
        Map<String, Object> row = require(requestId);
        if (reviewerId == number(row.get("applicantId"))) {
            throw new com.friendlystation.platform.common.ForbiddenException("不能审批自己提交的申请");
        }
        if (!"PENDING".equals(row.get("status"))) {
            throw new BusinessException("该申请已处理，不能重复审批");
        }
        String note = text(reviewNote);
        if (note.isEmpty()) {
            throw new BusinessException("驳回时请填写原因，驿站会看到这段说明");
        }
        row.put("status", "REJECTED");
        row.put("statusLabel", MysqlStationChangeService.statusLabelOf("REJECTED"));
        row.put("reviewNote", note);
        row.put("reviewTime", LocalDateTime.now().toString());
        row.put("reviewerId", reviewerId);
        notifyApplicant(row, "变更申请已驳回",
                MysqlStationChangeService.labelOf(String.valueOf(row.get("type"))) + "：" + note);
        return copyOf(row);
    }

    // ------------------------------------------------------------ 内部

    private void notifyApplicant(Map<String, Object> row, String title, String content) {
        long applicantId = number(row.get("applicantId"));
        if (applicantId <= 0) {
            return;
        }
        notificationService.push(applicantId, "SYSTEM", title, content,
                "", "STATION_CHANGE", number(row.get("id")));
    }

    /** 新申请提醒平台管理员处理；申请人本人不重复收通知。 */
    private void notifyReviewers(Map<String, Object> row, String stationName) {
        long applicantId = number(row.get("applicantId"));
        String content = stationName + " · "
                + MysqlStationChangeService.labelOf(String.valueOf(row.get("type"))) + " · " + row.get("newValue");
        for (var user : accountService.list()) {
            if (!"PLATFORM_ADMIN".equals(user.role()) || user.id() == applicantId) {
                continue;
            }
            notificationService.push(user.id(), "SYSTEM", "新的驿站变更申请", content,
                    "", "STATION_CHANGE", number(row.get("id")));
        }
    }

    @Override
    public synchronized Map<String, Object> cancel(CurrentUser applicant, long requestId) {
        Map<String, Object> row = require(requestId);
        if (number(row.get("applicantId")) != applicant.id()) {
            throw new com.friendlystation.platform.common.ForbiddenException("只能撤回自己提交的申请");
        }
        if (!"PENDING".equals(row.get("status"))) {
            throw new BusinessException("该申请已处理，不能撤回");
        }
        row.put("status", "CANCELLED");
        row.put("statusLabel", MysqlStationChangeService.statusLabelOf("CANCELLED"));
        row.put("reviewTime", LocalDateTime.now().toString());
        return copyOf(row);
    }

    private void applyChange(Map<String, Object> row) {
        long stationId = number(row.get("stationId"));
        String newValue = text(row.get("newValue"));
        // 读出原值再整体提交：StationUpdateRequest 是整体覆盖式，
        // 只传变更字段会把其他字段清空
        StationDto current = stationService.detail(stationId);
        String openTime = current.openTime();
        String phone = current.contactPhone();
        String address = current.address();
        switch (String.valueOf(row.get("type"))) {
            case "BUSINESS_HOURS" -> openTime = newValue;
            case "PHONE" -> phone = newValue;
            case "ADDRESS" -> address = newValue;
            default -> throw new BusinessException("暂不支持该变更类型");
        }
        stationService.update(stationId, new StationUpdateRequest(
                current.stationName(), current.area(), address, phone, openTime,
                current.status(), current.stationType(), current.cityCode()));
    }

    private String currentValueOf(long stationId, String type) {
        try {
            StationDto station = stationService.detail(stationId);
            return switch (type) {
                case "BUSINESS_HOURS" -> station.openTime() == null ? "" : station.openTime();
                case "PHONE" -> station.contactPhone() == null ? "" : station.contactPhone();
                case "ADDRESS" -> station.address() == null ? "" : station.address();
                default -> "";
            };
        } catch (Exception exception) {
            return "";
        }
    }

    private Map<String, Object> require(long requestId) {
        return requests.stream()
                .filter(item -> number(item.get("id")) == requestId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("变更申请不存在"));
    }

    /** 对外返回副本，避免调用方改到内部状态。 */
    private static Map<String, Object> copyOf(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.remove("reviewerId");
        return out;
    }

    private static long number(Object value) {
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception exception) {
            throw new BusinessException("编号不合法");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
