package com.friendlystation.platform.station;

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
    private final List<Map<String, Object>> requests = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public DemoStationChangeService(StationService stationService) {
        this.stationService = stationService;
    }

    @Override
    public synchronized Map<String, Object> submit(long applicantId, Map<String, Object> payload) {
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
        return copyOf(row);
    }

    @Override
    public synchronized List<Map<String, Object>> listByStation(long stationId) {
        return requests.stream()
                .filter(item -> number(item.get("stationId")) == stationId)
                .map(DemoStationChangeService::copyOf)
                .toList();
    }

    @Override
    public synchronized List<Map<String, Object>> pendingList() {
        return requests.stream()
                .filter(item -> "PENDING".equals(item.get("status")))
                .map(DemoStationChangeService::copyOf)
                .toList();
    }

    @Override
    public synchronized Map<String, Object> approve(long reviewerId, long requestId, String reviewNote) {
        Map<String, Object> row = require(requestId);
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
        return copyOf(row);
    }

    @Override
    public synchronized Map<String, Object> reject(long reviewerId, long requestId, String reviewNote) {
        Map<String, Object> row = require(requestId);
        String note = text(reviewNote);
        if (note.isEmpty()) {
            throw new BusinessException("驳回时请填写原因，驿站会看到这段说明");
        }
        row.put("status", "REJECTED");
        row.put("statusLabel", MysqlStationChangeService.statusLabelOf("REJECTED"));
        row.put("reviewNote", note);
        row.put("reviewTime", LocalDateTime.now().toString());
        row.put("reviewerId", reviewerId);
        return copyOf(row);
    }

    // ------------------------------------------------------------ 内部

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
        out.remove("applicantId");
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
