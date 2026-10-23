package com.friendlystation.platform.workflow;

import com.friendlystation.platform.common.AppealCategory;
import com.friendlystation.platform.common.AppointmentSubject;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.BusinessHours;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.station.StationDto;
import com.friendlystation.platform.station.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 非 mysql profile 使用的内存工作流存储，用于本地演示与单元测试。
 *
 * <p>说明：内存实现不连接数据库，重启后数据清空；驿站名称优先按 stationId 反查
 * {@link StationService}，查不到时回退到请求中携带的名称，便于脱离 Spring 容器单独测试。</p>
 */
@Service
@Profile("!mysql")
public class DemoWorkflowStore implements WorkflowService {
    private final AtomicLong appointmentIds = new AtomicLong(1000);
    private final AtomicLong appealIds = new AtomicLong(2000);
    private final AtomicLong signupIds = new AtomicLong(3000);
    private final AtomicLong evaluationIds = new AtomicLong(4000);

    private final List<Map<String, Object>> appointments = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> appeals = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> signups = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> evaluations = new CopyOnWriteArrayList<>();

    private StationService stationLookup;

    /**
     * 用于在报名前确认活动的承办方类型。
     *
     * <p>本类用「可选注入」而非构造器注入：单元测试直接 new，
     * 那时为 null，跳过校验，与本类既有的 stationLookup 完全一致。</p>
     */
    private com.friendlystation.platform.content.ActivityService activityLookup;
    private com.friendlystation.platform.notification.NotificationService notificationService;
    private com.friendlystation.platform.auth.MockAccountService accountService;

    public DemoWorkflowStore() {
    }

    /** 容器内可选注入，单元测试直接 new 时保持为 null。 */
    @Autowired(required = false)
    public void setStationLookup(StationService stationLookup) {
        this.stationLookup = stationLookup;
    }

    @Autowired(required = false)
    public void setActivityLookup(com.friendlystation.platform.content.ActivityService activityLookup) {
        this.activityLookup = activityLookup;
    }

    @Autowired(required = false)
    public void setNotificationService(
            com.friendlystation.platform.notification.NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired(required = false)
    public void setAccountService(com.friendlystation.platform.auth.MockAccountService accountService) {
        this.accountService = accountService;
    }

    /** 统一写通知；单元测试未注入时静默跳过。 */
    private void push(Object userId, String type, String title, String content, String bizType, long bizId) {
        pushWithLink(userId, type, title, content, "", bizType, bizId);
    }

    private void pushWithLink(Object userId, String type, String title, String content, String link,
                              String bizType, long bizId) {
        if (notificationService == null) {
            return;
        }
        notificationService.push(FormatUtils.number(userId), type, title, content, link, bizType, bizId);
    }

    // ------------------------------------------------------------------ 预约

    @Override
    public synchronized Map<String, Object> addAppointment(Map<String, Object> request, long userId) {
        long stationId = FormatUtils.number(request.get("stationId"));
        if (stationId <= 0) {
            throw new BusinessException("请选择服务驿站");
        }
        StationDto station = null;
        if (stationLookup != null) {
            try {
                station = stationLookup.detail(stationId);
            } catch (BusinessException exception) {
                throw new BusinessException("驿站不存在");
            }
            if (station == null || !"OPEN".equalsIgnoreCase(station.status())) {
                throw new BusinessException("该驿站当前未开放预约");
            }
        }
        String stationName = resolveStationName(stationId, request);
        // 与 mysql 实现共用同一套规则：事项自填必填、电话可选但需合格式。
        String serviceName = AppointmentSubject.require(
                request.get("serviceName") == null ? request.get("subject") : request.get("serviceName"));
        String contactPhone = AppointmentSubject.optionalPhone(request.get("contactPhone"));
        Object rawDate = request.get("date") == null ? request.get("appointDate") : request.get("date");
        java.time.LocalDate appointDate = FormatUtils.parseDateOrNull(rawDate);
        if (appointDate == null) {
            throw new BusinessException("请选择预约日期，格式为 yyyy-MM-dd");
        }
        if (appointDate.isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("预约日期不能早于今天");
        }
        String appointTime = FormatUtils.text(request.get("slot"),
                FormatUtils.text(request.get("appointTime"), ""));
        if (appointTime.isBlank()) {
            throw new BusinessException("请选择预约时段");
        }
        if (!BusinessHours.isValidSlot(appointTime)) {
            throw new BusinessException("预约时段格式应为 HH:mm-HH:mm");
        }
        if (station != null && com.friendlystation.platform.common.SuspensionWindow.overlaps(
                FormatUtils.parseDateTimeOrNull(station.suspendedFrom()),
                FormatUtils.parseDateTimeOrNull(station.suspendedTo()),
                appointDate, appointTime)) {
            String reason = station.suspendReason() == null || station.suspendReason().isBlank()
                    ? "该驿站暂停服务" : station.suspendReason();
            throw new BusinessException("该驿站在所选时段暂停服务：" + reason);
        }

        // 与 mysql 实现保持一致：预约时段必须落在驿站营业时间内；
        // 营业时间缺失或无法解析时不拦截。
        if (station != null && !BusinessHours.contains(station.openTime(), appointTime)) {
            throw new BusinessException("所选时段 " + appointTime + " 不在该驿站的营业时间内（"
                    + BusinessHours.describe(station.openTime()) + "）");
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", appointmentIds.incrementAndGet());
        item.put("userId", userId);
        item.put("userName", userName(userId));
        item.put("stationId", stationId);
        item.put("stationName", stationName);
        item.put("serviceId", FormatUtils.number(request.get("serviceId")));
        item.put("serviceName", serviceName);
        item.put("contactPhone", contactPhone);
        item.put("appointDate", appointDate.toString());
        item.put("appointTime", appointTime);
        item.put("status", WorkflowStatus.APPOINTMENT_PENDING);
        item.put("auditReason", "");
        item.put("operatorId", 0L);
        item.put("receiverType", "STATION_ADMIN");
        item.put("receiverName", "所选驿站管理员");
        item.put("dataStatus", "系统计算结果");
        item.put("createTime", FormatUtils.dateTime(LocalDateTime.now()));
        appointments.add(item);
        if (station != null && station.managerId() != null) {
            push(station.managerId(), "APPOINTMENT", "新的预约待确认",
                    serviceName + " · " + appointDate + " " + appointTime,
                    "APPOINTMENT", FormatUtils.number(item.get("id")));
        }
        return view(item);
    }

    @Override
    public List<Map<String, Object>> appointments(long userId) {
        return appointments.stream()
                .filter(item -> FormatUtils.number(item.get("userId")) == userId)
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .map(DemoWorkflowStore::view)
                .toList();
    }

    @Override
    public PageResult<Map<String, Object>> appointmentPage(long current, long size, String status, String keyword,
                                                          List<Long> stationIds) {
        if (stationIds != null && stationIds.isEmpty()) {
            return new PageResult<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200), 0, List.of());
        }
        String word = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> filtered = appointments.stream()
                .filter(item -> stationIds == null
                        || stationIds.contains(FormatUtils.number(item.get("stationId"))))
                .filter(item -> status == null || status.isBlank() || status.equals(item.get("status")))
                .filter(item -> word.isEmpty() || matches(word, item.get("stationName"), item.get("serviceName"), item.get("userName")))
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .map(DemoWorkflowStore::view)
                .toList();
        return PageResult.of(current, size, filtered);
    }

    @Override
    public synchronized Map<String, Object> auditAppointment(Map<String, Object> request, long operatorId,
                                                             java.util.List<Long> stationIds) {
        String status = FormatUtils.text(request.get("status"), WorkflowStatus.APPOINTMENT_CONFIRMED);
        if (!WorkflowStatus.isAuditTarget(status)) {
            throw new BusinessException("预约审核结果只能是 CONFIRMED 或 REJECTED");
        }
        Map<String, Object> item = byId(appointments, FormatUtils.number(request.get("id")));
        if (item == null) {
            throw new BusinessException("预约不存在");
        }
        if (stationIds != null && stationIds.isEmpty()) {
            throw new com.friendlystation.platform.common.ForbiddenException("当前账号没有负责的驿站");
        }
        if (stationIds != null && !stationIds.contains(FormatUtils.number(item.get("stationId")))) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权审核该驿站的预约");
        }
        if (!WorkflowStatus.canAuditAppointment((String) item.get("status"))) {
            throw new BusinessException("该预约已处理，不能重复审核");
        }
        item.put("status", status);
        item.put("operatorId", operatorId);
        item.put("auditReason", FormatUtils.text(request.get("reason"), ""));
        item.put("auditTime", FormatUtils.dateTime(LocalDateTime.now()));
        push(item.get("userId"), "APPOINTMENT",
                WorkflowStatus.APPOINTMENT_CONFIRMED.equals(status) ? "预约已确认" : "预约已拒绝",
                item.get("stationName") + " · " + item.get("appointDate") + " " + item.get("appointTime")
                        + (("" + item.get("auditReason")).isBlank() ? "" : "；说明：" + item.get("auditReason")),
                "APPOINTMENT", FormatUtils.number(item.get("id")));
        return view(item);
    }

    @Override
    public synchronized Map<String, Object> cancelAppointment(long id, long userId) {
        Map<String, Object> item = byId(appointments, id);
        if (item == null) {
            throw new BusinessException("预约不存在");
        }
        if (FormatUtils.number(item.get("userId")) != userId) {
            throw new BusinessException("只能取消本人提交的预约");
        }
        if (!WorkflowStatus.APPOINTMENT_PENDING.equals(item.get("status"))) {
            throw new BusinessException("只有待确认的预约可以取消");
        }
        item.put("status", WorkflowStatus.APPOINTMENT_CANCELLED);
        item.put("operatorId", userId);
        item.put("cancelTime", FormatUtils.dateTime(LocalDateTime.now()));
        if (stationLookup != null) {
            try {
                StationDto station = stationLookup.detail(FormatUtils.number(item.get("stationId")));
                if (station != null && station.managerId() != null) {
                    push(station.managerId(), "APPOINTMENT", "预约已取消",
                            userName(userId) + " · " + item.get("serviceName") + " · "
                                    + item.get("appointDate") + " " + item.get("appointTime"),
                            "APPOINTMENT", FormatUtils.number(item.get("id")));
                }
            } catch (BusinessException ignored) {
                // 驿站已删除：取消本身仍然有效
            }
        }
        return view(item);
    }

    // ------------------------------------------------------------------ 诉求

    @Override
    public synchronized Map<String, Object> addAppeal(Map<String, Object> request, long userId) {
        // 与 mysql 实现共用分类口径：分类决定默认子类型与状态文案
        String category = AppealCategory.normalize(request.get("category"));
        String type = FormatUtils.text(request.get("type"), AppealCategory.defaultType(category));
        String receiverTarget = AppealCategory.resolveTarget(request.get("receiverTarget"), category, type);
        boolean anonymous = FormatUtils.flag(request.get("anonymous"));
        String title = FormatUtils.text(request.get("title"), "未填写标题");
        String area = FormatUtils.text(request.get("area"), "未填写区域");
        Long stationId = null;
        if (AppealCategory.TARGET_STATION.equals(receiverTarget)) {
            long rawStationId = FormatUtils.number(request.get("stationId"));
            if (rawStationId > 0) {
                if (stationLookup != null) {
                    StationDto station;
                    try {
                        station = stationLookup.detail(rawStationId);
                    } catch (BusinessException exception) {
                        throw new BusinessException("驿站不存在");
                    }
                    if (station == null) {
                        throw new BusinessException("驿站不存在");
                    }
                    if (station.area() != null && !station.area().isBlank()) {
                        area = station.area();
                    }
                }
                stationId = rawStationId;
            }
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", appealIds.incrementAndGet());
        item.put("userId", userId);
        item.put("stationId", stationId == null ? 0L : stationId);
        item.put("userName", userName(userId));
        item.put("title", title);
        item.put("category", category);
        item.put("categoryLabel", AppealCategory.label(category));
        item.put("positive", AppealCategory.isPositive(category));
        item.put("receiverTarget", receiverTarget);
        item.put("receiverTargetLabel", AppealCategory.targetLabel(receiverTarget));
        item.put("type", type);
        item.put("area", area);
        item.put("content", FormatUtils.text(request.get("content"), ""));
        item.put("anonymous", anonymous);
        item.put("evaluated", false);
        item.put("status", WorkflowStatus.APPEAL_PENDING);
        item.put("handlerId", 0L);
        item.put("handlerName", "");
        item.put("finishTime", "");
        // 受理方以用户选择的反馈对象为准（驿站 / 平台管理员）
        item.put("receiverType", AppealCategory.receiverRole(receiverTarget));
        item.put("receiverName", AppealCategory.receiverName(receiverTarget));
        item.put("handlingBoundary", WorkflowStatus.handlingBoundary(type));
        item.put("dataStatus", "系统计算结果");
        item.put("createTime", FormatUtils.dateTime(LocalDateTime.now()));
        item.put("records", new ArrayList<>(List.of(
                record("SUBMIT", AppealCategory.label(category) + "已提交", userId, AppealCategory.label(category) + "已提交"))));
        appeals.add(item);
        if (stationId != null && stationId > 0 && stationLookup != null) {
            StationDto station = stationLookup.detail(stationId);
            if (station != null && station.managerId() != null) {
                push(station.managerId(), "APPEAL", "新的诉求待受理",
                        item.get("title") + " · " + AppealCategory.label(category) + " · " + area,
                        "APPEAL", FormatUtils.number(item.get("id")));
            }
        }
        return view(item);
    }

    @Override
    public List<Map<String, Object>> appeals(long userId) {
        return appeals.stream()
                .filter(item -> FormatUtils.number(item.get("userId")) == userId)
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .map(item -> view(item, false))
                .toList();
    }

    @Override
    public PageResult<Map<String, Object>> appealPage(long current, long size, String status, String keyword,
                                                      List<Long> stationIds, List<String> areas) {
        if (stationIds != null && stationIds.isEmpty()) {
            return new PageResult<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200), 0, List.of());
        }
        String word = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> filtered = appeals.stream()
                .filter(item -> inAppealScope(item, stationIds, areas))
                .filter(item -> status == null || status.isBlank() || status.equals(item.get("status")))
                .filter(item -> word.isEmpty() || matches(word, item.get("title"), item.get("type"), item.get("area"), item.get("userName")))
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .map(item -> view(item, true))
                .toList();
        return PageResult.of(current, size, filtered);
    }

    @Override
    public Map<String, Object> appealDetail(long id, long viewerId) {
        Map<String, Object> item = byId(appeals, id);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        return view(item, shouldMask(item, viewerId));
    }

    @Override
    public List<Map<String, Object>> appealRecords(long appealId, long viewerId) {
        Map<String, Object> item = byId(appeals, appealId);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        return shouldMask(item, viewerId) ? maskedRecords(item) : recordsOf(item);
    }

    @Override
    public boolean canAccessAppeal(long id, long viewerId, boolean platformLevel,
                                   List<Long> stationIds, List<String> areas) {
        Map<String, Object> item = byId(appeals, id);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        if (platformLevel) {
            return true;
        }
        if (FormatUtils.number(item.get("userId")) == viewerId) {
            return true;
        }
        return inAppealScope(item, stationIds, areas);
    }

    /** 定向驿站按编号，历史数据按区域兜底。 */
    private static boolean inAppealScope(Map<String, Object> item, List<Long> stationIds, List<String> areas) {
        if (stationIds == null) {
            return true;
        }
        long stationId = FormatUtils.number(item.get("stationId"));
        if (stationId > 0) {
            return stationIds.contains(stationId);
        }
        return areas != null && areas.contains(String.valueOf(item.get("area")));
    }

    @Override
    public synchronized Map<String, Object> assignAppeal(Map<String, Object> request, long operatorId) {
        Map<String, Object> item = byId(appeals, FormatUtils.number(request.get("id")));
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        String current = String.valueOf(item.get("status"));
        // 与 mysql 实现同口径：处理中不能回退到已分派；已办结/已关闭也不能再分派。
        if (!WorkflowStatus.APPEAL_PENDING.equals(current) && !WorkflowStatus.APPEAL_ASSIGNED.equals(current)) {
            throw new BusinessException("该诉求已办结、已关闭或正在处理中，不能再次分派");
        }
        item.put("status", WorkflowStatus.APPEAL_ASSIGNED);
        item.put("handlerId", operatorId);
        item.put("handlerName", userName(operatorId));
        item.put("assignedAt", FormatUtils.dateTime(LocalDateTime.now()));
        recordsOf(item).add(record("ASSIGN", "分派处理人", operatorId, FormatUtils.text(request.get("content"), "已分派处理人")));
        return view(item);
    }

    @Override
    public synchronized Map<String, Object> handleAppeal(Map<String, Object> request, long operatorId) {
        String status = FormatUtils.text(request.get("status"), WorkflowStatus.APPEAL_RESOLVED);
        if (!WorkflowStatus.isHandleTarget(status)) {
            throw new BusinessException("诉求处理状态只能是 PROCESSING、RESOLVED 或 CLOSED");
        }
        // 与 mysql 实现同口径：办结/关闭必须写明给用户的回复，不能在服务端回退成状态标签。
        String handleContent = FormatUtils.text(request.get("content"), "");
        if (handleContent.isBlank()
                && (WorkflowStatus.APPEAL_RESOLVED.equals(status) || WorkflowStatus.APPEAL_CLOSED.equals(status))) {
            throw new BusinessException("请填写给用户的回复，这段话会直接展示给用户");
        }
        Map<String, Object> item = byId(appeals, FormatUtils.number(request.get("id")));
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        if (!WorkflowStatus.canHandleAppeal((String) item.get("status"))) {
            throw new BusinessException("该诉求已办结或关闭，不能重复处理");
        }
        item.put("status", status);
        item.put("handlerId", operatorId);
        item.put("handlerName", userName(operatorId));
        item.put("handledAt", FormatUtils.dateTime(LocalDateTime.now()));
        if (WorkflowStatus.APPEAL_RESOLVED.equals(status)) {
            item.put("finishTime", FormatUtils.dateTime(LocalDateTime.now()));
        }
        String content = FormatUtils.text(request.get("content"), WorkflowStatus.label(status));
        recordsOf(item).add(record(WorkflowStatus.APPEAL_RESOLVED.equals(status) ? "RESOLVE" : "HANDLE", content,
                operatorId, content));
        push(item.get("userId"), "APPEAL",
                WorkflowStatus.APPEAL_RESOLVED.equals(status) ? "诉求已办结"
                        : WorkflowStatus.APPEAL_CLOSED.equals(status) ? "诉求已关闭" : "诉求处理中",
                content, "APPEAL", FormatUtils.number(item.get("id")));
        return view(item);
    }

    @Override
    public synchronized Map<String, Object> evaluateAppeal(Map<String, Object> request, long userId) {
        Map<String, Object> item = byId(appeals, FormatUtils.number(request.get("appealId")));
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        if (FormatUtils.number(item.get("userId")) != userId) {
            throw new BusinessException("只能评价本人提交的诉求");
        }
        if (!WorkflowStatus.canEvaluateAppeal((String) item.get("status"))) {
            throw new BusinessException("诉求办结后才能评价");
        }
        long score = FormatUtils.number(request.get("score"));
        if (score < 1 || score > 5) {
            throw new BusinessException("评价分数必须在 1 到 5 之间");
        }
        boolean evaluated = evaluations.stream().anyMatch(row ->
                FormatUtils.number(row.get("appealId")) == FormatUtils.number(request.get("appealId"))
                        && FormatUtils.number(row.get("userId")) == userId);
        if (evaluated) {
            throw new BusinessException("该诉求已评价，不能重复评价");
        }
        String content = FormatUtils.text(request.get("content"), "");
        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("id", evaluationIds.incrementAndGet());
        evaluation.put("appealId", FormatUtils.number(request.get("appealId")));
        evaluation.put("userId", userId);
        evaluation.put("userName", userName(userId));
        evaluation.put("score", score);
        evaluation.put("content", content);
        evaluation.put("createTime", FormatUtils.dateTime(LocalDateTime.now()));
        evaluations.add(evaluation);
        recordsOf(item).add(record("EVALUATE", content.isBlank() ? "用户已评价" : content, userId, content));
        item.put("evaluationScore", score);
        item.put("evaluated", true);
        return evaluation;
    }

    /**
     * 标记预约爽约，与 mysql 实现同口径：只允许标记已确认的预约。
     */
    @Override
    public synchronized Map<String, Object> markNoShow(long id, long operatorId, String remark,
                                                       java.util.List<Long> stationIds) {
        Map<String, Object> item = appointments.stream()
                .filter(row -> FormatUtils.number(row.get("id")) == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        if (stationIds != null && !stationIds.contains(FormatUtils.number(item.get("stationId")))) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权处理该驿站的预约");
        }
        if (Integer.valueOf(1).equals(item.get("noShow"))) {
            throw new IllegalArgumentException("该预约已标记为爽约");
        }
        if (!WorkflowStatus.APPOINTMENT_CONFIRMED.equals(item.get("status"))) {
            throw new IllegalArgumentException("只能标记已确认的预约为爽约");
        }
        item.put("noShow", 1);
        item.put("noShowAt", FormatUtils.dateTime(LocalDateTime.now()));
        if (remark != null && !remark.isBlank()) {
            item.put("auditReason", remark.trim());
        }
        return view(item);
    }

    @Override
    public synchronized Map<String, Object> completeAppointment(long id, long operatorId,
                                                                java.util.List<Long> stationIds) {
        Map<String, Object> item = byId(appointments, id);
        if (item == null) {
            throw new BusinessException("预约不存在");
        }
        if (stationIds != null && !stationIds.contains(FormatUtils.number(item.get("stationId")))) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权处理该驿站的预约");
        }
        if (!WorkflowStatus.APPOINTMENT_CONFIRMED.equals(item.get("status"))) {
            throw new BusinessException("只有已确认的预约可以标记为已完成");
        }
        item.put("status", WorkflowStatus.APPOINTMENT_FINISHED);
        item.put("operatorId", operatorId);
        item.put("finishTime", FormatUtils.dateTime(LocalDateTime.now()));
        push(item.get("userId"), "APPOINTMENT", "预约已完成",
                item.get("stationName") + " · " + item.get("appointDate") + " " + item.get("appointTime") + " 已完成",
                "APPOINTMENT", FormatUtils.number(item.get("id")));
        return view(item);
    }

    @Override
    public synchronized Map<String, Object> interveneAppointment(long id, String action, String reason,
                                                                 long operatorId) {
        Map<String, Object> item = byId(appointments, id);
        if (item == null) {
            throw new BusinessException("预约不存在");
        }
        String normalized = action == null ? "" : action.trim().toUpperCase();
        if (!java.util.List.of("CONFIRM", "CANCEL", "COMPLETE").contains(normalized)) {
            throw new BusinessException("平台干预动作不正确");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("平台干预必须填写原因");
        }
        String current = String.valueOf(item.get("status"));
        String target;
        switch (normalized) {
            case "CONFIRM" -> {
                if (!WorkflowStatus.APPOINTMENT_PENDING.equals(current)) {
                    throw new BusinessException("当前预约状态不允许该干预操作");
                }
                target = WorkflowStatus.APPOINTMENT_CONFIRMED;
            }
            case "COMPLETE" -> {
                if (!WorkflowStatus.APPOINTMENT_CONFIRMED.equals(current)) {
                    throw new BusinessException("当前预约状态不允许该干预操作");
                }
                target = WorkflowStatus.APPOINTMENT_FINISHED;
            }
            default -> {
                if (!WorkflowStatus.APPOINTMENT_PENDING.equals(current)
                        && !WorkflowStatus.APPOINTMENT_CONFIRMED.equals(current)) {
                    throw new BusinessException("当前预约状态不允许该干预操作");
                }
                target = WorkflowStatus.APPOINTMENT_CANCELLED;
            }
        }
        item.put("status", target);
        item.put("operatorId", operatorId);
        item.put("interveneReason", reason);
        item.put("interveneTime", FormatUtils.dateTime(LocalDateTime.now()));
        String title = switch (normalized) {
            case "CONFIRM" -> "预约已由平台协调确认";
            case "COMPLETE" -> "预约已由平台标记完成";
            default -> "预约已由平台协调取消";
        };
        push(item.get("userId"), "APPOINTMENT", title,
                item.get("stationName") + " · " + item.get("appointDate") + " " + item.get("appointTime")
                        + "；原因：" + reason,
                "APPOINTMENT", FormatUtils.number(item.get("id")));
        return view(item);
    }

    @Override
    public int notifySuspension(long stationId, LocalDateTime from, LocalDateTime to, String reason) {
        if (from == null || to == null) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> item : appointments) {
            if (FormatUtils.number(item.get("stationId")) != stationId) {
                continue;
            }
            String status = String.valueOf(item.get("status"));
            if (!WorkflowStatus.APPOINTMENT_PENDING.equals(status)
                    && !WorkflowStatus.APPOINTMENT_CONFIRMED.equals(status)) {
                continue;
            }
            if (!com.friendlystation.platform.common.SuspensionWindow.overlaps(
                    from, to, FormatUtils.parseDateOrNull(item.get("appointDate")),
                    String.valueOf(item.get("appointTime")))) {
                continue;
            }
            push(item.get("userId"), "APPOINTMENT", "驿站暂停服务提醒",
                    item.get("stationName") + " 于 " + FormatUtils.dateTime(from) + " 至 " + FormatUtils.dateTime(to)
                            + " 暂停服务（原因：" + (reason == null ? "" : reason) + "）。你预约的 "
                            + item.get("appointDate") + " " + item.get("appointTime") + " 可能受影响，请与驿站联系。",
                    "STATION", stationId);
            count++;
        }
        return count;
    }

    // -------------------------------------------------------------- 活动报名

    @Override
    public synchronized Map<String, Object> addSignup(Map<String, Object> request, long userId) {
        long activityId = FormatUtils.number(request.get("activityId"));
        Map<String, Object> activity = null;
        // 与 mysql 实现同口径：第三方活动不接受站内报名，取消/未发布活动也不收报名。
        if (activityLookup != null) {
            try {
                activity = activityLookup.detail(activityId);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("活动不存在");
            }
            String organizerType = activity == null ? "" : String.valueOf(activity.get("organizerType"));
            if (!ActivityOrganizer.acceptsSignup(organizerType)) {
                throw new IllegalArgumentException("该活动由第三方机构举办，请前往活动来源页面报名");
            }
            if (activity != null && !"PUBLISHED".equalsIgnoreCase(String.valueOf(activity.get("status")))) {
                throw new IllegalArgumentException("活动已取消或尚未发布，不能报名");
            }
        }
        // 与 mysql 实现同口径：姓名与电话必填，承办方要靠这些联系报名人。
        String contactName = ActivityOrganizer.requireContactName(request.get("contactName"));
        String phone = ActivityOrganizer.requirePhone(request.get("phone"));
        // 已取消的记录可以重新激活；已报名/已联系直接拒绝。
        Map<String, Object> existing = signups.stream()
                .filter(item -> FormatUtils.number(item.get("activityId")) == activityId
                        && FormatUtils.number(item.get("userId")) == userId)
                .findFirst()
                .orElse(null);
        if (existing != null) {
            String current = ActivityOrganizer.normalizeSignupStatus(existing.get("status"));
            if (!ActivityOrganizer.SIGNUP_CANCELLED.equals(current)) {
                throw new IllegalArgumentException("该活动已报名");
            }
            existing.put("contactName", contactName);
            existing.put("phone", phone);
            existing.put("remark", FormatUtils.text(request.get("remark"), ""));
            existing.put("status", ActivityOrganizer.SIGNUP_SIGNED_UP);
            existing.put("statusLabel", ActivityOrganizer.signupLabel(ActivityOrganizer.SIGNUP_SIGNED_UP));
            existing.put("reviewNote", "");
            existing.put("reviewerName", "");
            existing.put("reviewTime", "");
            existing.put("createTime", FormatUtils.dateTime(LocalDateTime.now()));
            if (activityLookup != null) {
                activityLookup.onSignup(activityId);
            }
            notifySignupManager(activity, contactName, FormatUtils.number(existing.get("id")));
            return view(existing);
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", signupIds.incrementAndGet());
        item.put("activityId", activityId);
        item.put("activityTitle", activity != null
                ? String.valueOf(activity.get("title"))
                : FormatUtils.text(request.get("activityTitle"), FormatUtils.text(request.get("title"), "")));
        item.put("stationId", activity == null ? 0L : FormatUtils.number(activity.get("stationId")));
        item.put("userId", userId);
        item.put("userName", userName(userId));
        item.put("contactName", contactName);
        item.put("phone", phone);
        item.put("remark", FormatUtils.text(request.get("remark"), ""));
        item.put("status", ActivityOrganizer.SIGNUP_SIGNED_UP);
        item.put("statusLabel", ActivityOrganizer.signupLabel(ActivityOrganizer.SIGNUP_SIGNED_UP));
        item.put("reviewNote", "");
        item.put("reviewerName", "");
        item.put("reviewTime", "");
        item.put("dataStatus", "系统计算结果");
        item.put("createTime", FormatUtils.dateTime(LocalDateTime.now()));
        signups.add(item);
        if (activityLookup != null) {
            activityLookup.onSignup(activityId);
        }
        notifySignupManager(activity, contactName, FormatUtils.number(item.get("id")));
        return view(item);
    }

    /** 报名成功后提醒活动所属驿站的负责人；平台管理员始终保留一份可处理的通知。 */
    private void notifySignupManager(Map<String, Object> activity, String contactName, long signupId) {
        if (activity == null) {
            return;
        }
        String link = "/admin?tab=signups&signupId=" + signupId;
        String content = activity.get("title") + " · " + contactName;
        Long managerId = null;
        long stationId = FormatUtils.number(activity.get("stationId"));
        if (stationId > 0 && stationLookup != null) {
            try {
                StationDto station = stationLookup.detail(stationId);
                if (station != null && station.managerId() != null) {
                    managerId = station.managerId();
                    pushWithLink(managerId, "SIGNUP", "新的活动报名", content, link, "SIGNUP", signupId);
                }
            } catch (BusinessException ignored) {
                // 活动所属驿站已删除：报名本身仍有效，不阻断。
            }
        }
        if (accountService == null) {
            return;
        }
        for (var user : accountService.list()) {
            if (!"PLATFORM_ADMIN".equals(user.role()) || (managerId != null && managerId == user.id())) {
                continue;
            }
            pushWithLink(user.id(), "SIGNUP", "新的活动报名", content, link, "SIGNUP", signupId);
        }
    }

    /** 承办方处理报名：联系 / 审核通过 / 审核未通过，并给出反馈。 */
    @Override
    public synchronized Map<String, Object> reviewSignup(Map<String, Object> request, long operatorId,
                                                         java.util.List<Long> stationIds) {
        long id = FormatUtils.number(request.get("id"));
        String status = FormatUtils.text(request.get("status"), ActivityOrganizer.SIGNUP_APPROVED).toUpperCase();
        if (!ActivityOrganizer.isReviewTarget(status)) {
            throw new IllegalArgumentException("报名处理状态只能是 CONTACTED、APPROVED 或 REJECTED");
        }
        Map<String, Object> item = signups.stream()
                .filter(row -> FormatUtils.number(row.get("id")) == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("报名记录不存在"));
        if (activityLookup != null) {
            Map<String, Object> activity;
            try {
                activity = activityLookup.detail(FormatUtils.number(item.get("activityId")));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("活动不存在");
            }
            if (activity == null) {
                throw new IllegalArgumentException("活动不存在");
            }
            if (stationIds != null && !stationIds.contains(FormatUtils.number(activity.get("stationId")))) {
                throw new com.friendlystation.platform.common.ForbiddenException("无权处理该活动的报名");
            }
        }
        String currentStatus = ActivityOrganizer.normalizeSignupStatus(item.get("status"));
        if (ActivityOrganizer.SIGNUP_CANCELLED.equals(currentStatus)) {
            throw new IllegalArgumentException("该报名已取消，不能处理");
        }
        if (ActivityOrganizer.SIGNUP_APPROVED.equals(currentStatus)
                || ActivityOrganizer.SIGNUP_REJECTED.equals(currentStatus)) {
            throw new IllegalArgumentException("该报名已审核，不能重复处理");
        }
        String note = FormatUtils.text(request.get("reviewNote"), "");
        // 未通过必须说明原因，报名人有权知道为什么
        if (ActivityOrganizer.SIGNUP_REJECTED.equals(status) && note.isBlank()) {
            throw new IllegalArgumentException("审核未通过时请填写原因，报名人会看到这段说明");
        }
        item.put("status", status);
        item.put("statusLabel", ActivityOrganizer.signupLabel(status));
        item.put("reviewNote", note);
        item.put("reviewerName", userName(operatorId));
        item.put("reviewTime", FormatUtils.dateTime(LocalDateTime.now()));
        push(item.get("userId"), "SIGNUP",
                ActivityOrganizer.SIGNUP_APPROVED.equals(status) ? "报名审核通过"
                        : ActivityOrganizer.SIGNUP_REJECTED.equals(status) ? "报名未通过" : "承办方已联系你",
                item.get("activityTitle") + (note.isBlank() ? "" : "；" + note),
                "SIGNUP", FormatUtils.number(item.get("id")));
        return view(item);
    }

    @Override
    public synchronized Map<String, Object> cancelSignup(long id, long userId) {
        Map<String, Object> item = signups.stream()
                .filter(row -> FormatUtils.number(row.get("id")) == id)
                .findFirst()
                .orElseThrow(() -> new BusinessException("报名记录不存在"));
        if (FormatUtils.number(item.get("userId")) != userId) {
            throw new BusinessException("只能取消本人提交的报名");
        }
        String current = ActivityOrganizer.normalizeSignupStatus(item.get("status"));
        if (!ActivityOrganizer.SIGNUP_SIGNED_UP.equals(current)
                && !ActivityOrganizer.SIGNUP_CONTACTED.equals(current)) {
            throw new BusinessException("报名已审核或已取消，不能取消");
        }
        item.put("status", ActivityOrganizer.SIGNUP_CANCELLED);
        item.put("statusLabel", ActivityOrganizer.signupLabel(ActivityOrganizer.SIGNUP_CANCELLED));
        if (activityLookup != null) {
            activityLookup.onSignupCancel(FormatUtils.number(item.get("activityId")));
        }
        if (stationLookup != null) {
            try {
                StationDto station = stationLookup.detail(FormatUtils.number(item.get("stationId")));
                if (station != null && station.managerId() != null) {
                    push(station.managerId(), "SIGNUP", "报名已取消",
                            item.get("activityTitle") + " · " + item.get("contactName"),
                            "SIGNUP", FormatUtils.number(item.get("id")));
                }
            } catch (BusinessException ignored) {
                // 驿站已删除：取消本身仍然有效
            }
        }
        return view(item);
    }

    @Override
    public long pendingSignupCount(java.util.List<Long> stationIds) {
        return allSignups(stationIds).stream()
                .filter(row -> ActivityOrganizer.SIGNUP_SIGNED_UP.equals(
                                ActivityOrganizer.normalizeSignupStatus(row.get("status")))
                        || ActivityOrganizer.SIGNUP_CONTACTED.equals(
                                ActivityOrganizer.normalizeSignupStatus(row.get("status"))))
                .count();
    }

    @Override
    public List<Map<String, Object>> mySignups(long userId) {
        return signups.stream()
                .filter(item -> FormatUtils.number(item.get("userId")) == userId)
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .map(DemoWorkflowStore::view)
                .toList();
    }

    @Override
    public List<Map<String, Object>> allSignups(java.util.List<Long> stationIds) {
        return signups.stream()
                .filter(item -> stationIds == null
                        || stationIds.contains(FormatUtils.number(item.get("stationId"))))
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .map(DemoWorkflowStore::view)
                .toList();
    }

    // ------------------------------------------------------------------ 概览

    @Override
    public Map<String, Object> overview() {
        return overview(null, null);
    }

    /**
     * 按数据范围统计概览，与 mysql 实现同口径。
     *
     * @param stationIds null 表示不限制；空列表表示没有负责的驿站，结果应为 0
     * @param areas      null 表示不限制
     */
    @Override
    public Map<String, Object> overview(List<Long> stationIds, List<String> areas) {
        boolean restrictStations = stationIds != null;
        boolean emptyScope = restrictStations && stationIds.isEmpty();
        boolean restrictAreas = areas != null;

        // 预约按驿站范围、诉求按区域范围：两者字段不同，不能共用一个 predicate。
        java.util.function.Predicate<Map<String, Object>> appointmentScope = item -> {
            if (emptyScope) {
                return false;
            }
            return !restrictStations || stationIds.contains(FormatUtils.number(item.get("stationId")));
        };
        java.util.function.Predicate<Map<String, Object>> appealScope = item -> {
            if (emptyScope) {
                return false;
            }
            return !restrictAreas || areas.contains(String.valueOf(item.get("area")));
        };

        long pendingAppointments = appointments.stream()
                .filter(appointmentScope)
                .filter(item -> WorkflowStatus.APPOINTMENT_PENDING.equals(item.get("status"))).count();
        long pendingAppeals = appeals.stream()
                .filter(appealScope)
                .filter(item -> !WorkflowStatus.APPEAL_RESOLVED.equals(item.get("status"))
                        && !WorkflowStatus.APPEAL_CLOSED.equals(item.get("status"))).count();
        long resolvedAppeals = appeals.stream()
                .filter(appealScope)
                .filter(item -> WorkflowStatus.APPEAL_RESOLVED.equals(item.get("status"))).count();
        long signupCount = signups.stream().filter(appointmentScope).count();
        long evaluationCount = evaluations.stream().filter(evaluation -> {
            if (emptyScope) {
                return false;
            }
            if (!restrictAreas) {
                return true;
            }
            Map<String, Object> appeal = byId(appeals, FormatUtils.number(evaluation.get("appealId")));
            return appeal != null && areas.contains(String.valueOf(appeal.get("area")));
        }).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appointments", appointments.stream().filter(appointmentScope).count());
        out.put("pendingAppointments", pendingAppointments);
        out.put("appeals", appeals.stream().filter(appealScope).count());
        out.put("pendingAppeals", pendingAppeals);
        out.put("resolvedAppeals", resolvedAppeals);
        out.put("signups", signupCount);
        // 诉求评价数：与 mysql 实现的 appealEvaluations 口径一致。
        out.put("appealEvaluations", evaluationCount);
        out.put("dataStatus", "系统计算结果");
        // 让前端能如实标注口径
        out.put("scoped", restrictStations || restrictAreas);
        out.put("scopeNote", restrictStations || restrictAreas ? "已按当前账号负责的驿站与区域限定" : "全平台汇总");
        out.put("status", "READY");
        return out;
    }

    // ------------------------------------------------------------ 内部工具

    private String resolveStationName(long stationId, Map<String, Object> request) {
        String fromRequest = FormatUtils.text(request.get("stationName"), FormatUtils.text(request.get("station"), ""));
        if (stationId > 0 && stationLookup != null) {
            try {
                StationDto station = stationLookup.detail(stationId);
                if (station != null && station.stationName() != null && !station.stationName().isBlank()) {
                    return station.stationName();
                }
            } catch (BusinessException ignored) {
                // 查不到驿站时退回请求中携带的名称，保证演示流程不中断。
            }
        }
        return fromRequest.isBlank() ? (stationId > 0 ? "驿站" + stationId : "所选驿站") : fromRequest;
    }

    /** 反查驿站；脱离 Spring 容器（单测直接 new）或驿站不存在时返回 null。 */
    private StationDto resolveStation(long stationId) {
        if (stationId <= 0 || stationLookup == null) {
            return null;
        }
        try {
            return stationLookup.detail(stationId);
        } catch (BusinessException exception) {
            return null;
        }
    }

    private static Map<String, Object> record(String action, String content, long handlerId, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("action", action);
        item.put("actionLabel", WorkflowStatus.actionLabel(action));
        item.put("content", content);
        item.put("handlerId", handlerId);
        item.put("handlerName", userName(handlerId));
        item.put("createTime", FormatUtils.dateTime(LocalDateTime.now()));
        return item;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> recordsOf(Map<String, Object> appeal) {
        Object records = appeal.get("records");
        if (records instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        List<Map<String, Object>> created = new ArrayList<>();
        appeal.put("records", created);
        return created;
    }

    private static Map<String, Object> view(Map<String, Object> item) {
        return view(item, false);
    }

    /** 生成对外返回的副本；匿名诉求在管理端列表中隐藏提交人。 */
    private static Map<String, Object> view(Map<String, Object> item, boolean maskAnonymous) {
        Map<String, Object> out = new LinkedHashMap<>(item);
        // 这个方法被预约/诉求/报名/评价共用，状态文案要按实体各用各的口径，
        // 否则报名状态会被诉求的映射覆盖（SIGNED_UP 找不到对应文案就原样返回）。
        Object category = item.get("category");
        String rawStatus = String.valueOf(item.get("status"));
        if (ActivityOrganizer.isSignupStatus(rawStatus)) {
            // 报名状态：已报名，等待联系 / 承办方已联系 / 审核通过 / 审核未通过
            out.put("statusLabel", ActivityOrganizer.signupLabel(rawStatus));
        } else if (category == null) {
            // 与 mysql 实现同口径：管理端用标准闭环术语，用户端用分类感知的温暖文案
            out.put("statusLabelStandard", WorkflowStatus.label(rawStatus));
            out.put("statusLabel", WorkflowStatus.label(rawStatus));
        } else {
            out.put("statusLabelStandard", WorkflowStatus.label(rawStatus));
            out.put("statusLabel", AppealCategory.statusLabel(String.valueOf(category), rawStatus));
        }
        if (item.get("records") instanceof List<?> records) {
            out.put("recordCount", records.size());
        }
        if (maskAnonymous && Boolean.TRUE.equals(item.get("anonymous"))) {
            out.put("userName", "匿名用户");
            out.put("userId", 0L);
            List<Map<String, Object>> masked = maskedRecords(item);
            out.put("records", masked);
            out.put("recordCount", masked.size());
        }
        return out;
    }

    /** 匿名诉求只对提交人本人展示身份；管理员列表/详情一律打码。 */
    private static boolean shouldMask(Map<String, Object> item, long viewerId) {
        return Boolean.TRUE.equals(item.get("anonymous"))
                && FormatUtils.number(item.get("userId")) != viewerId;
    }

    /** 返回副本，避免打码时改到内存里的原始记录。 */
    private static List<Map<String, Object>> maskedRecords(Map<String, Object> item) {
        List<Map<String, Object>> masked = new ArrayList<>();
        for (Map<String, Object> record : recordsOf(item)) {
            Map<String, Object> copy = new LinkedHashMap<>(record);
            if ("SUBMIT".equals(copy.get("action"))) {
                copy.put("handlerId", 0L);
                copy.put("handlerName", "匿名用户");
            }
            masked.add(copy);
        }
        return masked;
    }

    private static Map<String, Object> byId(List<Map<String, Object>> items, long id) {
        return items.stream().filter(item -> FormatUtils.number(item.get("id")) == id).findFirst().orElse(null);
    }

    private static boolean matches(String word, Object... values) {
        for (Object value : values) {
            if (value != null && value.toString().contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String userName(long userId) {
        return switch ((int) userId) {
            case 1 -> "骑手小王";
            case 2 -> "陈管理员";
            case 3 -> "平台管理员";
            default -> "用户" + userId;
        };
    }

    // -------------------------------------------------------------- 邮箱绑定（演示模式）

    /**
     * 演示模式的邮箱绑定。与 userName 一样用内存维护——演示库没有 sys_user 表，
     * 但邮箱验证码是敏感操作的前置条件，两种 profile 都必须能走通。
     */
    private final Map<Long, String> boundEmails = new java.util.concurrent.ConcurrentHashMap<>();

    /** 读取账号绑定的邮箱，未绑定返回 null。 */
    public String emailOf(long userId) {
        return boundEmails.get(userId);
    }

    /** 绑定或更换邮箱。 */
    public void bindEmail(long userId, String email) {
        if (email == null || email.isBlank()) {
            boundEmails.remove(userId);
        } else {
            boundEmails.put(userId, email.trim().toLowerCase());
        }
    }

    /**
     * 凭据版本：改账号 ID 或密码时递增，让已签发令牌失效。
     *
     * <p>与 mysql 实现的 sys_user.credential_version 同一语义。
     * 用 computeIfAbsent 保证首次读取即为 0，与数据库默认值一致。</p>
     */
    private final Map<Long, Integer> credentialVersions = new java.util.concurrent.ConcurrentHashMap<>();

    public int credentialVersionOf(long userId) {
        // 演示模式没有用户表，未知账号视为版本 0（而非 -1），
        // 否则演示环境的既有令牌会全部被判为失效。
        return credentialVersions.computeIfAbsent(userId, key -> 0);
    }

    public void bumpCredentialVersion(long userId) {
        credentialVersions.merge(userId, 1, Integer::sum);
    }
}
