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

    public DemoWorkflowStore() {
    }

    /** 容器内可选注入，单元测试直接 new 时保持为 null。 */
    @Autowired(required = false)
    public void setStationLookup(StationService stationLookup) {
        this.stationLookup = stationLookup;
    }

    // ------------------------------------------------------------------ 预约

    @Override
    public synchronized Map<String, Object> addAppointment(Map<String, Object> request, long userId) {
        long stationId = FormatUtils.number(request.get("stationId"));
        String stationName = resolveStationName(stationId, request);
        // 与 mysql 实现共用同一套规则：事项自填必填、电话可选但需合格式。
        String serviceName = AppointmentSubject.require(
                request.get("serviceName") == null ? request.get("subject") : request.get("serviceName"));
        String contactPhone = AppointmentSubject.optionalPhone(request.get("contactPhone"));
        Object rawDate = request.get("date") == null ? request.get("appointDate") : request.get("date");
        String appointTime = FormatUtils.text(request.get("slot"),
                FormatUtils.text(request.get("appointTime"), "待确认"));

        // 与 mysql 实现保持一致：预约时段必须落在驿站营业时间内；
        // 营业时间缺失或无法解析时不拦截。stationLookup 为空（脱离容器单测）时跳过校验。
        StationDto station = resolveStation(stationId);
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
        item.put("appointDate", FormatUtils.date(rawDate).toString());
        item.put("appointTime", appointTime);
        item.put("status", WorkflowStatus.APPOINTMENT_PENDING);
        item.put("auditReason", "");
        item.put("operatorId", 0L);
        item.put("receiverType", "STATION_ADMIN");
        item.put("receiverName", "所选驿站管理员");
        item.put("dataStatus", "系统计算结果");
        item.put("createTime", FormatUtils.dateTime(LocalDateTime.now()));
        appointments.add(item);
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
    public synchronized Map<String, Object> auditAppointment(Map<String, Object> request, long operatorId) {
        String status = FormatUtils.text(request.get("status"), WorkflowStatus.APPOINTMENT_CONFIRMED);
        if (!WorkflowStatus.isAuditTarget(status)) {
            throw new BusinessException("预约审核结果只能是 CONFIRMED 或 REJECTED");
        }
        Map<String, Object> item = byId(appointments, FormatUtils.number(request.get("id")));
        if (item == null) {
            throw new BusinessException("预约不存在");
        }
        if (!WorkflowStatus.canAuditAppointment((String) item.get("status"))) {
            throw new BusinessException("该预约已处理，不能重复审核");
        }
        item.put("status", status);
        item.put("operatorId", operatorId);
        item.put("auditReason", FormatUtils.text(request.get("reason"), ""));
        item.put("auditTime", FormatUtils.dateTime(LocalDateTime.now()));
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

        Map<String, Object> item = new HashMap<>();
        item.put("id", appealIds.incrementAndGet());
        item.put("userId", userId);
        item.put("userName", userName(userId));
        item.put("title", title);
        item.put("category", category);
        item.put("categoryLabel", AppealCategory.label(category));
        item.put("positive", AppealCategory.isPositive(category));
        item.put("receiverTarget", receiverTarget);
        item.put("receiverTargetLabel", AppealCategory.targetLabel(receiverTarget));
        item.put("type", type);
        item.put("area", FormatUtils.text(request.get("area"), "未填写区域"));
        item.put("content", FormatUtils.text(request.get("content"), ""));
        item.put("anonymous", anonymous);
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
                                                      List<String> areas) {
        if (areas != null && areas.isEmpty()) {
            return new PageResult<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200), 0, List.of());
        }
        String word = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> filtered = appeals.stream()
                .filter(item -> areas == null || areas.contains(String.valueOf(item.get("area"))))
                .filter(item -> status == null || status.isBlank() || status.equals(item.get("status")))
                .filter(item -> word.isEmpty() || matches(word, item.get("title"), item.get("type"), item.get("area"), item.get("userName")))
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .map(item -> view(item, true))
                .toList();
        return PageResult.of(current, size, filtered);
    }

    @Override
    public Map<String, Object> appealDetail(long id) {
        Map<String, Object> item = byId(appeals, id);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        return view(item, false);
    }

    @Override
    public List<Map<String, Object>> appealRecords(long appealId) {
        Map<String, Object> item = byId(appeals, appealId);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        return recordsOf(item);
    }

    @Override
    public synchronized Map<String, Object> assignAppeal(Map<String, Object> request, long operatorId) {
        Map<String, Object> item = byId(appeals, FormatUtils.number(request.get("id")));
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        if (!WorkflowStatus.canHandleAppeal((String) item.get("status"))) {
            throw new BusinessException("该诉求已办结或关闭，不能再次分派");
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
        return evaluation;
    }

    // -------------------------------------------------------------- 活动报名

    @Override
    public synchronized Map<String, Object> addSignup(Map<String, Object> request, long userId) {
        long activityId = FormatUtils.number(request.get("activityId"));
        // 与 mysql 实现同口径：姓名与电话必填，承办方要靠这些联系报名人。
        String contactName = ActivityOrganizer.requireContactName(request.get("contactName"));
        String phone = ActivityOrganizer.requirePhone(request.get("phone"));
        boolean duplicated = signups.stream()
                .anyMatch(item -> FormatUtils.number(item.get("activityId")) == activityId
                        && FormatUtils.number(item.get("userId")) == userId);
        if (duplicated) {
            throw new IllegalArgumentException("该活动已报名");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", signupIds.incrementAndGet());
        item.put("activityId", activityId);
        item.put("activityTitle", FormatUtils.text(request.get("activityTitle"), FormatUtils.text(request.get("title"), "")));
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
        return view(item);
    }

    /** 承办方处理报名：联系 / 审核通过 / 审核未通过，并给出反馈。 */
    @Override
    public synchronized Map<String, Object> reviewSignup(Map<String, Object> request, long operatorId) {
        long id = FormatUtils.number(request.get("id"));
        String status = FormatUtils.text(request.get("status"), ActivityOrganizer.SIGNUP_APPROVED).toUpperCase();
        if (!ActivityOrganizer.isReviewTarget(status)) {
            throw new IllegalArgumentException("报名处理状态只能是 CONTACTED、APPROVED 或 REJECTED");
        }
        Map<String, Object> item = signups.stream()
                .filter(row -> FormatUtils.number(row.get("id")) == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("报名记录不存在"));
        if (ActivityOrganizer.SIGNUP_CANCELLED.equals(String.valueOf(item.get("status")))) {
            throw new IllegalArgumentException("该报名已取消，不能处理");
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
        return view(item);
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
    public List<Map<String, Object>> allSignups() {
        return signups.stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("createTime")), Comparator.reverseOrder()))
                .map(DemoWorkflowStore::view)
                .toList();
    }

    // ------------------------------------------------------------------ 概览

    @Override
    public Map<String, Object> overview() {
        long pendingAppointments = appointments.stream()
                .filter(item -> WorkflowStatus.APPOINTMENT_PENDING.equals(item.get("status"))).count();
        long pendingAppeals = appeals.stream()
                .filter(item -> !WorkflowStatus.APPEAL_RESOLVED.equals(item.get("status"))
                        && !WorkflowStatus.APPEAL_CLOSED.equals(item.get("status"))).count();
        long resolvedAppeals = appeals.stream()
                .filter(item -> WorkflowStatus.APPEAL_RESOLVED.equals(item.get("status"))).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appointments", appointments.size());
        out.put("pendingAppointments", pendingAppointments);
        out.put("appeals", appeals.size());
        out.put("pendingAppeals", pendingAppeals);
        out.put("resolvedAppeals", resolvedAppeals);
        out.put("signups", signups.size());
        // 诉求评价数：与 mysql 实现的 appealEvaluations 口径一致。
        out.put("appealEvaluations", evaluations.size());
        out.put("dataStatus", "系统计算结果");
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
        }
        return out;
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
}
