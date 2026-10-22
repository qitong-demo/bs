package com.friendlystation.platform.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.common.AppealCategory;
import com.friendlystation.platform.common.AppointmentSubject;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.BusinessHours;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.Activity;
import com.friendlystation.platform.persistence.entity.ActivitySignup;
import com.friendlystation.platform.persistence.entity.Appeal;
import com.friendlystation.platform.persistence.entity.AppealEvaluation;
import com.friendlystation.platform.persistence.entity.AppealRecord;
import com.friendlystation.platform.persistence.entity.ServiceAppointment;
import com.friendlystation.platform.persistence.entity.Station;
import com.friendlystation.platform.persistence.entity.StationServiceItem;
import com.friendlystation.platform.persistence.entity.SysUser;
import com.friendlystation.platform.persistence.mapper.ActivityMapper;
import com.friendlystation.platform.persistence.mapper.ActivitySignupMapper;
import com.friendlystation.platform.persistence.mapper.AppealEvaluationMapper;
import com.friendlystation.platform.persistence.mapper.AppealMapper;
import com.friendlystation.platform.persistence.mapper.AppealRecordMapper;
import com.friendlystation.platform.persistence.mapper.ServiceAppointmentMapper;
import com.friendlystation.platform.persistence.mapper.StationMapper;
import com.friendlystation.platform.persistence.mapper.StationServiceItemMapper;
import com.friendlystation.platform.persistence.mapper.SysUserMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** mysql profile 下的预约、诉求与活动报名实现，所有状态变更写入数据库并留存处理记录。 */
@Service
@Profile("mysql")
public class MysqlWorkflowService implements WorkflowService {
    private final ServiceAppointmentMapper appointmentMapper;
    private final AppealMapper appealMapper;
    private final AppealRecordMapper appealRecordMapper;
    private final AppealEvaluationMapper appealEvaluationMapper;
    private final ActivitySignupMapper signupMapper;
    private final StationMapper stationMapper;
    private final StationServiceItemMapper serviceItemMapper;
    private final ActivityMapper activityMapper;
    private final SysUserMapper userMapper;
    private final com.friendlystation.platform.notification.NotificationService notificationService;

    public MysqlWorkflowService(ServiceAppointmentMapper appointmentMapper,
                                AppealMapper appealMapper,
                                AppealRecordMapper appealRecordMapper,
                                AppealEvaluationMapper appealEvaluationMapper,
                                ActivitySignupMapper signupMapper,
                                StationMapper stationMapper,
                                StationServiceItemMapper serviceItemMapper,
                                ActivityMapper activityMapper,
                                SysUserMapper userMapper,
                                com.friendlystation.platform.notification.NotificationService notificationService) {
        this.appointmentMapper = appointmentMapper;
        this.appealMapper = appealMapper;
        this.appealRecordMapper = appealRecordMapper;
        this.appealEvaluationMapper = appealEvaluationMapper;
        this.signupMapper = signupMapper;
        this.stationMapper = stationMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.activityMapper = activityMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ 预约

    @Override
    @Transactional
    public Map<String, Object> addAppointment(Map<String, Object> request, long userId) {
        long stationId = FormatUtils.number(request.get("stationId"));
        if (stationId <= 0) {
            throw new BusinessException("请选择服务驿站");
        }
        Station station = stationMapper.selectById(stationId);
        if (station == null || "DELETED".equalsIgnoreCase(station.getStatus())) {
            throw new BusinessException("驿站不存在");
        }
        if (!"OPEN".equalsIgnoreCase(station.getStatus())) {
            throw new BusinessException("该驿站当前未开放预约");
        }
        Object rawDate = request.get("date") == null ? request.get("appointDate") : request.get("date");
        // 预约事项由用户自填（服务项目不再从固定列表里选），并要求留下联系电话便于回电确认。
        String subject = AppointmentSubject.require(
                request.get("serviceName") == null ? request.get("subject") : request.get("serviceName"));
        String contactPhone = AppointmentSubject.optionalPhone(request.get("contactPhone"));
        LocalDate appointDate = FormatUtils.parseDateOrNull(rawDate);
        if (appointDate == null) {
            throw new BusinessException("请选择预约日期，格式为 yyyy-MM-dd");
        }
        if (appointDate.isBefore(LocalDate.now())) {
            throw new BusinessException("预约日期不能早于今天");
        }
        String slot = FormatUtils.text(request.get("slot"), FormatUtils.text(request.get("appointTime"), ""));
        if (slot.isBlank()) {
            throw new BusinessException("请选择预约时段");
        }
        if (!BusinessHours.isValidSlot(slot)) {
            throw new BusinessException("预约时段格式应为 HH:mm-HH:mm");
        }
        if (com.friendlystation.platform.common.SuspensionWindow.overlaps(
                station.getSuspendedFrom(), station.getSuspendedTo(), appointDate, slot)) {
            String reason = station.getSuspendReason() == null || station.getSuspendReason().isBlank()
                    ? "该驿站暂停服务" : station.getSuspendReason();
            throw new BusinessException("该驿站在所选时段暂停服务：" + reason);
        }

        // 预约时段必须落在驿站营业时间内。营业时间来自公开来源文本或高德地图同步；
        // 缺失或无法解析时不拦截（判断不了就不挡），避免因数据缺失误拒正常预约。
        if (!BusinessHours.contains(station.getOpenTime(), slot)) {
            throw new BusinessException("所选时段 " + slot + " 不在该驿站的营业时间内（"
                    + BusinessHours.describe(station.getOpenTime()) + "）");
        }

        ServiceAppointment item = new ServiceAppointment();
        item.setUserId(userId);
        item.setStationId(stationId);
        // 兼容历史字段：新提交可以不带 serviceId。
        long serviceId = FormatUtils.number(request.get("serviceId"));
        item.setServiceId(serviceId > 0 ? serviceId : null);
        item.setServiceName(subject);
        item.setContactPhone(contactPhone);
        item.setAppointDate(appointDate);
        item.setAppointTime(slot);
        item.setStatus(WorkflowStatus.APPOINTMENT_PENDING);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        appointmentMapper.insert(item);
        if (station.getManagerId() != null) {
            notificationService.push(station.getManagerId(), "APPOINTMENT", "新的预约待确认",
                    subject + " · " + appointDate + " " + slot, "", "APPOINTMENT", item.getId());
        }

        return appointmentView(item, stationName(stationId), appointmentSubject(item), userName(userId));
    }

    @Override
    public List<Map<String, Object>> appointments(long userId) {
        List<ServiceAppointment> items = appointmentMapper.selectList(new LambdaQueryWrapper<ServiceAppointment>()
                .eq(ServiceAppointment::getUserId, userId)
                .orderByDesc(ServiceAppointment::getCreateTime)
                .orderByDesc(ServiceAppointment::getId));
        return appointmentViews(items);
    }

    @Override
    public PageResult<Map<String, Object>> appointmentPage(long current, long size, String status, String keyword,
                                                          List<Long> stationIds) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        if (stationIds != null && stationIds.isEmpty()) {
            return new PageResult<>(safeCurrent, safeSize, 0, List.of());
        }
        LambdaQueryWrapper<ServiceAppointment> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(ServiceAppointment::getStatus, status.trim());
        }
        if (stationIds != null) {
            query.in(ServiceAppointment::getStationId, stationIds);
        }
        String word = keyword == null ? "" : keyword.trim();
        if (!word.isEmpty()) {
            List<Long> matchedStationIds = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                            .like(Station::getStationName, word)).stream()
                    .map(Station::getId)
                    .toList();
            if (matchedStationIds.isEmpty()) {
                return new PageResult<>(safeCurrent, safeSize, 0, List.of());
            }
            query.in(ServiceAppointment::getStationId, matchedStationIds);
        }
        query.orderByDesc(ServiceAppointment::getCreateTime).orderByDesc(ServiceAppointment::getId);
        Page<ServiceAppointment> result = appointmentMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), appointmentViews(result.getRecords()));
    }

    @Override
    @Transactional
    public Map<String, Object> auditAppointment(Map<String, Object> request, long operatorId, List<Long> stationIds) {
        String status = FormatUtils.text(request.get("status"), WorkflowStatus.APPOINTMENT_CONFIRMED);
        if (!WorkflowStatus.isAuditTarget(status)) {
            throw new BusinessException("预约审核结果只能是 CONFIRMED 或 REJECTED");
        }
        long id = FormatUtils.number(request.get("id"));
        String reason = FormatUtils.text(request.get("reason"), "");
        if (stationIds != null && stationIds.isEmpty()) {
            throw new com.friendlystation.platform.common.ForbiddenException("当前账号没有负责的驿站");
        }

        // 条件更新：只有仍处于「待驿站确认」的记录会被更新，并发下重复审核只会成功一次。
        int updated = appointmentMapper.update(null, new LambdaUpdateWrapper<ServiceAppointment>()
                .eq(ServiceAppointment::getId, id)
                .eq(ServiceAppointment::getStatus, WorkflowStatus.APPOINTMENT_PENDING)
                .in(stationIds != null, ServiceAppointment::getStationId, stationIds)
                .set(ServiceAppointment::getStatus, status)
                .set(ServiceAppointment::getAuditReason, reason)
                .set(ServiceAppointment::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            ServiceAppointment existing = appointmentMapper.selectById(id);
            if (existing == null) {
                throw new BusinessException("预约不存在");
            }
            if (stationIds != null && !stationIds.contains(existing.getStationId())) {
                throw new com.friendlystation.platform.common.ForbiddenException("无权审核该驿站的预约");
            }
            throw new BusinessException("该预约已处理，不能重复审核");
        }

        ServiceAppointment item = appointmentMapper.selectById(id);
        String title = WorkflowStatus.APPOINTMENT_CONFIRMED.equals(status) ? "预约已确认" : "预约已拒绝";
        String content = stationName(item.getStationId()) + " · " + FormatUtils.date(item.getAppointDate())
                + " " + item.getAppointTime()
                + (reason == null || reason.isBlank() ? "" : "；说明：" + reason);
        notificationService.push(item.getUserId(), "APPOINTMENT", title, content, "", "APPOINTMENT", id);
        return appointmentView(item, stationName(item.getStationId()), appointmentSubject(item), userName(item.getUserId()));
    }

    @Override
    @Transactional
    public Map<String, Object> cancelAppointment(long id, long userId) {
        int updated = appointmentMapper.update(null, new LambdaUpdateWrapper<ServiceAppointment>()
                .eq(ServiceAppointment::getId, id)
                .eq(ServiceAppointment::getUserId, userId)
                .eq(ServiceAppointment::getStatus, WorkflowStatus.APPOINTMENT_PENDING)
                .set(ServiceAppointment::getStatus, WorkflowStatus.APPOINTMENT_CANCELLED)
                .set(ServiceAppointment::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            ServiceAppointment item = appointmentMapper.selectById(id);
            if (item == null) {
                throw new BusinessException("预约不存在");
            }
            if (item.getUserId() == null || item.getUserId() != userId) {
                throw new BusinessException("只能取消本人提交的预约");
            }
            throw new BusinessException("只有待确认的预约可以取消");
        }
        ServiceAppointment item = appointmentMapper.selectById(id);
        Station station = stationMapper.selectById(item.getStationId());
        if (station != null && station.getManagerId() != null) {
            notificationService.push(station.getManagerId(), "APPOINTMENT", "预约已取消",
                    userName(userId) + " · " + item.getServiceName() + " · "
                            + FormatUtils.date(item.getAppointDate()) + " " + item.getAppointTime(),
                    "", "APPOINTMENT", id);
        }
        return appointmentView(item, stationName(item.getStationId()), appointmentSubject(item), userName(userId));
    }

    // ------------------------------------------------------------------ 诉求

    @Override
    @Transactional
    public Map<String, Object> addAppeal(Map<String, Object> request, long userId) {
        // 分类决定默认子类型：表扬感谢默认「热心帮助」，反映问题默认「驿站服务」。
        String category = AppealCategory.normalize(request.get("category"));
        String type = FormatUtils.text(request.get("type"), AppealCategory.defaultType(category));
        // 反馈对象：用户显式选择优先，未传时按分类与子类型推断（驿站管理员或平台管理员）
        String receiverTarget = AppealCategory.resolveTarget(request.get("receiverTarget"), category, type);
        String area = FormatUtils.text(request.get("area"), "未填写区域");
        Long stationId = resolveAppealStation(request, receiverTarget);
        if (stationId != null) {
            // 定向到驿站时，区域以驿站档案为准：避免用户手填区域与驿站不一致导致驿站端看不到。
            Station station = stationMapper.selectById(stationId);
            if (station != null && station.getArea() != null && !station.getArea().isBlank()) {
                area = station.getArea();
            }
        }
        Appeal item = new Appeal();
        item.setUserId(userId);
        item.setStationId(stationId);
        item.setTitle(FormatUtils.text(request.get("title"), "未填写标题"));
        item.setCategory(category);
        item.setReceiverTarget(receiverTarget);
        item.setAppealType(type);
        item.setArea(area);
        item.setContent(FormatUtils.text(request.get("content"), ""));
        item.setAnonymous(FormatUtils.flag(request.get("anonymous")));
        item.setStatus(WorkflowStatus.APPEAL_PENDING);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        appealMapper.insert(item);
        if (stationId != null) {
            Station station = stationMapper.selectById(stationId);
            if (station != null && station.getManagerId() != null) {
                notificationService.push(station.getManagerId(), "APPEAL", "新的诉求待受理",
                        item.getTitle() + " · " + AppealCategory.label(category) + " · " + area,
                        "", "APPEAL", item.getId());
            }
        }

        insertRecord(item.getId(), userId, "SUBMIT", AppealCategory.label(category) + "已提交");
        return appealView(item, userName(userId), userName(null),
                recordsByAppeal(List.of(item.getId())).getOrDefault(item.getId(), List.of()), false, false);
    }

    @Override
    public List<Map<String, Object>> appeals(long userId) {
        List<Appeal> items = appealMapper.selectList(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getUserId, userId)
                .orderByDesc(Appeal::getCreateTime)
                .orderByDesc(Appeal::getId));
        return appealViews(items, false);
    }

    @Override
    public PageResult<Map<String, Object>> appealPage(long current, long size, String status, String keyword,
                                                      List<Long> stationIds, List<String> areas) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        if (stationIds != null && stationIds.isEmpty()) {
            return new PageResult<>(safeCurrent, safeSize, 0, List.of());
        }
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(Appeal::getStatus, status.trim());
        }
        if (stationIds != null) {
            // 新数据按 station_id 精准路由；历史数据没有 station_id，退回按区域匹配。
            List<String> fallbackAreas = areas == null ? List.of() : areas;
            query.and(wrapper -> {
                wrapper.in(Appeal::getStationId, stationIds);
                if (!fallbackAreas.isEmpty()) {
                    wrapper.or(legacy -> legacy.isNull(Appeal::getStationId)
                            .in(Appeal::getArea, fallbackAreas));
                }
            });
        }
        String word = keyword == null ? "" : keyword.trim();
        if (!word.isEmpty()) {
            query.and(wrapper -> wrapper.like(Appeal::getTitle, word)
                    .or().like(Appeal::getAppealType, word)
                    .or().like(Appeal::getArea, word));
        }
        query.orderByDesc(Appeal::getCreateTime).orderByDesc(Appeal::getId);
        Page<Appeal> result = appealMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), appealViews(result.getRecords(), true));
    }

    @Override
    public Map<String, Object> appealDetail(long id, long viewerId) {
        Appeal item = appealMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        Long evaluated = appealEvaluationMapper.selectCount(new LambdaQueryWrapper<AppealEvaluation>()
                .eq(AppealEvaluation::getAppealId, id));
        return appealView(item, userName(item.getUserId()), userName(item.getHandlerId()),
                recordsByAppeal(List.of(id)).getOrDefault(id, List.of()), shouldMask(item, viewerId),
                evaluated != null && evaluated > 0);
    }

    @Override
    public List<Map<String, Object>> appealRecords(long appealId, long viewerId) {
        Appeal item = appealMapper.selectById(appealId);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        List<Map<String, Object>> records = recordsByAppeal(List.of(appealId))
                .getOrDefault(appealId, List.of());
        return shouldMask(item, viewerId) ? maskSubmitRecords(records) : records;
    }

    @Override
    public boolean canAccessAppeal(long id, long viewerId, boolean platformLevel,
                                   List<Long> stationIds, List<String> areas) {
        Appeal item = appealMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        if (platformLevel) {
            return true;
        }
        if (item.getUserId() != null && item.getUserId() == viewerId) {
            return true;
        }
        // 定向到驿站的诉求按驿站编号判断；历史数据没有编号，才退回按区域近似。
        if (item.getStationId() != null) {
            return stationIds != null && stationIds.contains(item.getStationId());
        }
        return areas != null && item.getArea() != null && areas.contains(item.getArea());
    }

    @Override
    @Transactional
    public Map<String, Object> assignAppeal(Map<String, Object> request, long operatorId) {
        long id = FormatUtils.number(request.get("id"));
        // 条件更新：只有待受理 / 已分派（改派）的诉求可以被分派；
        // 处理中再分派会把状态从 PROCESSING 退回 ASSIGNED，造成状态回退。
        int updated = appealMapper.update(null, new LambdaUpdateWrapper<Appeal>()
                .eq(Appeal::getId, id)
                .in(Appeal::getStatus, List.of(WorkflowStatus.APPEAL_PENDING, WorkflowStatus.APPEAL_ASSIGNED))
                .set(Appeal::getStatus, WorkflowStatus.APPEAL_ASSIGNED)
                .set(Appeal::getHandlerId, operatorId)
                .set(Appeal::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            if (appealMapper.selectById(id) == null) {
                throw new BusinessException("诉求不存在");
            }
            throw new BusinessException("该诉求已办结、已关闭或正在处理中，不能再次分派");
        }
        insertRecord(id, operatorId, "ASSIGN", FormatUtils.text(request.get("content"), "已分派处理人"));
        return appealDetail(id, operatorId);
    }

    @Override
    @Transactional
    public Map<String, Object> handleAppeal(Map<String, Object> request, long operatorId) {
        String status = FormatUtils.text(request.get("status"), WorkflowStatus.APPEAL_RESOLVED);
        if (!WorkflowStatus.isHandleTarget(status)) {
            throw new BusinessException("诉求处理状态只能是 PROCESSING、RESOLVED 或 CLOSED");
        }
        long id = FormatUtils.number(request.get("id"));
        String content = FormatUtils.text(request.get("content"), "");
        boolean resolved = WorkflowStatus.APPEAL_RESOLVED.equals(status);
        // 回复是这条反馈的「结果」——用户端会把它当作结论展示。
        // 因此办结/关闭时必须由受理方写出内容，不能在服务端回退成状态标签
        // （此前回退为「已办结」，等于允许不写回复就结束，直接调接口即可绕过前端校验）。
        if (content.isBlank() && (resolved || WorkflowStatus.APPEAL_CLOSED.equals(status))) {
            throw new BusinessException("请填写给用户的回复，这段话会直接展示给用户");
        }
        // 仅「处理中」允许不填内容，此时记一条状态流转说明。
        if (content.isBlank()) {
            content = WorkflowStatus.label(status);
        }

        // 条件更新：只有未办结 / 未关闭的诉求可以被处理，保证并发下只成功一次、只留一条处理记录。
        int updated = appealMapper.update(null, new LambdaUpdateWrapper<Appeal>()
                .eq(Appeal::getId, id)
                .in(Appeal::getStatus, List.of(WorkflowStatus.APPEAL_PENDING, WorkflowStatus.APPEAL_ASSIGNED,
                        WorkflowStatus.APPEAL_PROCESSING))
                .set(Appeal::getStatus, status)
                .set(Appeal::getHandlerId, operatorId)
                .set(Appeal::getUpdateTime, LocalDateTime.now())
                .set(resolved, Appeal::getFinishTime, LocalDateTime.now()));
        if (updated == 0) {
            if (appealMapper.selectById(id) == null) {
                throw new BusinessException("诉求不存在");
            }
            throw new BusinessException("该诉求已办结或关闭，不能重复处理");
        }

        String action = resolved ? "RESOLVE" : (WorkflowStatus.APPEAL_CLOSED.equals(status) ? "CLOSE" : "HANDLE");
        insertRecord(id, operatorId, action, content);
        Appeal appealAfterHandle = appealMapper.selectById(id);
        String title = WorkflowStatus.APPEAL_RESOLVED.equals(status) ? "诉求已办结"
                : WorkflowStatus.APPEAL_CLOSED.equals(status) ? "诉求已关闭" : "诉求处理中";
        if (appealAfterHandle.getUserId() != null) {
            notificationService.push(appealAfterHandle.getUserId(), "APPEAL", title, content, "", "APPEAL", id);
        }
        return appealDetail(id, operatorId);
    }

    @Override
    @Transactional
    public Map<String, Object> evaluateAppeal(Map<String, Object> request, long userId) {
        long appealId = FormatUtils.number(request.get("appealId"));
        Appeal item = appealMapper.selectById(appealId);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        if (item.getUserId() == null || item.getUserId() != userId) {
            throw new BusinessException("只能评价本人提交的诉求");
        }
        if (!WorkflowStatus.canEvaluateAppeal(item.getStatus())) {
            throw new BusinessException("诉求办结后才能评价");
        }
        long score = FormatUtils.number(request.get("score"));
        if (score < 1 || score > 5) {
            throw new BusinessException("评价分数必须在 1 到 5 之间");
        }
        Long evaluated = appealEvaluationMapper.selectCount(new LambdaQueryWrapper<AppealEvaluation>()
                .eq(AppealEvaluation::getAppealId, appealId)
                .eq(AppealEvaluation::getUserId, userId));
        if (evaluated != null && evaluated > 0) {
            throw new BusinessException("该诉求已评价，不能重复评价");
        }
        String content = FormatUtils.text(request.get("content"), "");

        AppealEvaluation evaluation = new AppealEvaluation();
        evaluation.setAppealId(appealId);
        evaluation.setUserId(userId);
        evaluation.setScore((int) score);
        evaluation.setContent(content);
        evaluation.setCreateTime(LocalDateTime.now());
        appealEvaluationMapper.insert(evaluation);

        insertRecord(appealId, userId, "EVALUATE", content.isBlank() ? "用户已评价" : content);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", evaluation.getId());
        out.put("appealId", appealId);
        out.put("userId", userId);
        out.put("userName", userName(userId));
        out.put("score", score);
        out.put("content", content);
        out.put("createTime", FormatUtils.dateTime(evaluation.getCreateTime()));
        return out;
    }

    // -------------------------------------------------------------- 活动报名

    @Override
    @Transactional
    public Map<String, Object> addSignup(Map<String, Object> request, long userId) {
        long activityId = FormatUtils.number(request.get("activityId"));
        // 先锁定活动行：同一活动的并发报名被串行化，避免重复报名与名额超卖。
        Activity activity = activityMapper.selectOne(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .last("FOR UPDATE"));
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        // 第三方活动只做展示与跳转，不接受站内报名：
        // 报名与审核都在对方平台进行，我们既不掌握名单、也无法替对方联系报名人。
        // 判断统一走 ActivityOrganizer.acceptsSignup()——这里原本内联了它的反义写法，
        // 等于同一条规则有两份实现，改一处必然漏另一处。
        if (!ActivityOrganizer.acceptsSignup(activity.getOrganizerType())) {
            throw new BusinessException("该活动由第三方机构举办，请前往活动来源页面报名");
        }
        // 只有已发布的活动能收报名：取消、草稿、过期活动即使还能通过链接打开，也不该继续收集报名。
        if (!"PUBLISHED".equalsIgnoreCase(activity.getStatus())) {
            throw new BusinessException("活动已取消或尚未发布，不能报名");
        }
        // 报名信息要交给承办方去联系报名人，因此姓名必填、电话必填。
        String contactName = ActivityOrganizer.requireContactName(request.get("contactName"));
        String phone = ActivityOrganizer.requirePhone(request.get("phone"));
        // 重复报名：已取消的记录可以重新激活（唯一索引 uk_activity_user 不允许再插一条），
        // 已报名/已联系的直接拒绝。
        ActivitySignup existing = signupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            String current = ActivityOrganizer.normalizeSignupStatus(existing.getStatus());
            if (!ActivityOrganizer.SIGNUP_CANCELLED.equals(current)) {
                throw new BusinessException("该活动已报名");
            }
            existing.setContactName(contactName);
            existing.setPhone(phone);
            existing.setRemark(FormatUtils.text(request.get("remark"), ""));
            existing.setStatus(ActivityOrganizer.SIGNUP_SIGNED_UP);
            existing.setReviewNote("");
            existing.setReviewerId(null);
            existing.setReviewTime(null);
            existing.setCreateTime(LocalDateTime.now());
            signupMapper.updateById(existing);
            notifyOrganizer(activity, contactName, existing.getId());
            return signupView(existing, activity);
        }
        int maxCount = activity.getMaxCount() == null ? 0 : activity.getMaxCount();
        Long signed = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .ne(ActivitySignup::getStatus, ActivityOrganizer.SIGNUP_CANCELLED));
        if (maxCount > 0 && signed != null && signed >= maxCount) {
            throw new BusinessException("活动名额已满");
        }

        ActivitySignup item = new ActivitySignup();
        item.setActivityId(activityId);
        item.setUserId(userId);
        item.setContactName(contactName);
        item.setPhone(phone);
        item.setRemark(FormatUtils.text(request.get("remark"), ""));
        item.setStatus(ActivityOrganizer.SIGNUP_SIGNED_UP);
        item.setCreateTime(LocalDateTime.now());
        try {
            signupMapper.insert(item);
        } catch (DuplicateKeyException exception) {
            // 唯一索引 uk_activity_user 兜底：把约束冲突转换为可读的业务提示。
            throw new BusinessException("该活动已报名");
        }
        notifyOrganizer(activity, contactName, item.getId());
        return signupView(item, activity);
    }

    @Override
    public List<Map<String, Object>> mySignups(long userId) {
        List<ActivitySignup> items = signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getUserId, userId)
                .orderByDesc(ActivitySignup::getCreateTime)
                .orderByDesc(ActivitySignup::getId));
        return signupViews(items);
    }

    @Override
    public List<Map<String, Object>> allSignups(List<Long> stationIds) {
        List<ActivitySignup> items = signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                .orderByDesc(ActivitySignup::getCreateTime)
                .orderByDesc(ActivitySignup::getId));
        return signupViews(items).stream()
                .filter(row -> stationIds == null
                        || stationIds.contains(FormatUtils.number(row.get("stationId"))))
                .toList();
    }

    // ------------------------------------------------------------------ 概览

    @Override
    public Map<String, Object> overview() {
        return overview(null, null);
    }

    /**
     * 按数据范围统计概览。
     *
     * <p>此前这个方法用 {@code selectCount(null)} 统计，**完全没有按角色过滤**，
     * 导致驿站管理员在首页看到的待办数字是全平台的，与管理工作台里按范围过滤的列表对不上。
     * 现在与列表接口用同一套范围（{@code DataScopeService}）。</p>
     *
     * @param stationIds null 表示不限制；空列表表示没有负责的驿站，结果应为 0
     * @param areas      null 表示不限制；诉求表没有驿站编号，只能按区域近似
     */
    @Override
    public Map<String, Object> overview(List<Long> stationIds, List<String> areas) {
        // null = 平台级，不加限制；空列表 = 该账号没有负责的驿站，必须返回 0 而不是全量。
        boolean restrictStations = stationIds != null;
        boolean emptyScope = restrictStations && stationIds.isEmpty();
        boolean restrictAreas = areas != null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appointments", emptyScope ? 0L : appointmentMapper.selectCount(
                new LambdaQueryWrapper<ServiceAppointment>()
                        .in(restrictStations && !emptyScope, ServiceAppointment::getStationId, stationIds)));
        out.put("pendingAppointments", emptyScope ? 0L : appointmentMapper.selectCount(
                new LambdaQueryWrapper<ServiceAppointment>()
                        .eq(ServiceAppointment::getStatus, WorkflowStatus.APPOINTMENT_PENDING)
                        .in(restrictStations && !emptyScope, ServiceAppointment::getStationId, stationIds)));
        out.put("appeals", emptyScope ? 0L : appealMapper.selectCount(
                new LambdaQueryWrapper<Appeal>()
                        .in(restrictAreas, Appeal::getArea, areas)));
        out.put("pendingAppeals", emptyScope ? 0L : appealMapper.selectCount(
                new LambdaQueryWrapper<Appeal>()
                        .ne(Appeal::getStatus, WorkflowStatus.APPEAL_RESOLVED)
                        .ne(Appeal::getStatus, WorkflowStatus.APPEAL_CLOSED)
                        .in(restrictAreas, Appeal::getArea, areas)));
        out.put("resolvedAppeals", emptyScope ? 0L : appealMapper.selectCount(
                new LambdaQueryWrapper<Appeal>()
                        .eq(Appeal::getStatus, WorkflowStatus.APPEAL_RESOLVED)
                        .in(restrictAreas, Appeal::getArea, areas)));
        // 报名数与诉求评价数也要同口径：此前直接 selectCount(null)，驿站管理员看到的
        // 是全平台数字，和上面按范围过滤的预约/诉求对不上。
        long signupCount;
        if (emptyScope) {
            signupCount = 0L;
        } else if (!restrictStations) {
            signupCount = signupMapper.selectCount(null);
        } else {
            List<Long> activityIds = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                            .select(Activity::getId)
                            .in(Activity::getStationId, stationIds))
                    .stream().map(Activity::getId).toList();
            signupCount = activityIds.isEmpty() ? 0L : signupMapper.selectCount(
                    new LambdaQueryWrapper<ActivitySignup>().in(ActivitySignup::getActivityId, activityIds));
        }
        out.put("signups", signupCount);
        long evaluationCount;
        if (emptyScope) {
            evaluationCount = 0L;
        } else if (!restrictAreas) {
            evaluationCount = appealEvaluationMapper.selectCount(null);
        } else {
            List<Long> appealIds = appealMapper.selectList(new LambdaQueryWrapper<Appeal>()
                            .select(Appeal::getId)
                            .in(Appeal::getArea, areas))
                    .stream().map(Appeal::getId).toList();
            evaluationCount = appealIds.isEmpty() ? 0L : appealEvaluationMapper.selectCount(
                    new LambdaQueryWrapper<AppealEvaluation>().in(AppealEvaluation::getAppealId, appealIds));
        }
        out.put("appealEvaluations", evaluationCount);
        out.put("dataStatus", "系统计算结果");
        out.put("status", "READY");
        // 让前端能如实标注口径，而不是自己猜
        out.put("scoped", restrictStations || restrictAreas);
        out.put("scopeNote", restrictStations || restrictAreas
                ? "已按当前账号负责的驿站与区域限定"
                : "全平台汇总");
        return out;
    }

    /**
     * 标记预约爽约。
     *
     * <p>只允许标记**已确认**的预约：待审核或被拒绝的预约本来就没成立，
     * 标记爽约没有意义，还会误伤用户。</p>
     */
    @Override
    @Transactional
    public Map<String, Object> markNoShow(long id, long operatorId, String remark, List<Long> stationIds) {
        ServiceAppointment item = appointmentMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("预约不存在");
        }
        if (stationIds != null
                && (item.getStationId() == null || !stationIds.contains(item.getStationId()))) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权处理该驿站的预约");
        }
        if (Boolean.TRUE.equals(item.getNoShow() != null && item.getNoShow() == 1)) {
            throw new BusinessException("该预约已标记为爽约");
        }
        if (!WorkflowStatus.APPOINTMENT_CONFIRMED.equals(item.getStatus())) {
            throw new BusinessException("只能标记已确认的预约为爽约");
        }
        appointmentMapper.update(null, new LambdaUpdateWrapper<ServiceAppointment>()
                .eq(ServiceAppointment::getId, id)
                .set(ServiceAppointment::getNoShow, 1)
                .set(ServiceAppointment::getNoShowAt, LocalDateTime.now()));
        // 备注写进审核说明，驿站与管理员都能看到当时的情况
        if (remark != null && !remark.isBlank()) {
            appointmentMapper.update(null, new LambdaUpdateWrapper<ServiceAppointment>()
                    .eq(ServiceAppointment::getId, id)
                    .set(ServiceAppointment::getAuditReason, remark.trim()));
        }
        ServiceAppointment updated = appointmentMapper.selectById(id);
        return appointmentView(updated,
                stationNames(List.of(updated.getStationId())).get(updated.getStationId()),
                appointmentSubject(updated), userName(updated.getUserId()));
    }

    @Override
    @Transactional
    public Map<String, Object> completeAppointment(long id, long operatorId, List<Long> stationIds) {
        if (stationIds != null && stationIds.isEmpty()) {
            throw new com.friendlystation.platform.common.ForbiddenException("当前账号没有负责的驿站");
        }
        // 条件更新：只有已确认的预约能完成；并发下只会成功一次。
        int updated = appointmentMapper.update(null, new LambdaUpdateWrapper<ServiceAppointment>()
                .eq(ServiceAppointment::getId, id)
                .eq(ServiceAppointment::getStatus, WorkflowStatus.APPOINTMENT_CONFIRMED)
                .in(stationIds != null, ServiceAppointment::getStationId, stationIds)
                .set(ServiceAppointment::getStatus, WorkflowStatus.APPOINTMENT_FINISHED)
                .set(ServiceAppointment::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            ServiceAppointment existing = appointmentMapper.selectById(id);
            if (existing == null) {
                throw new BusinessException("预约不存在");
            }
            if (stationIds != null && !stationIds.contains(existing.getStationId())) {
                throw new com.friendlystation.platform.common.ForbiddenException("无权处理该驿站的预约");
            }
            throw new BusinessException("只有已确认的预约可以标记为已完成");
        }
        ServiceAppointment item = appointmentMapper.selectById(id);
        notificationService.push(item.getUserId(), "APPOINTMENT", "预约已完成",
                stationName(item.getStationId()) + " · " + FormatUtils.date(item.getAppointDate())
                        + " " + item.getAppointTime() + " 已完成",
                "", "APPOINTMENT", id);
        return appointmentView(item, stationName(item.getStationId()), appointmentSubject(item),
                userName(item.getUserId()));
    }

    @Override
    @Transactional
    public int notifySuspension(long stationId, LocalDateTime from, LocalDateTime to, String reason) {
        if (from == null || to == null) {
            return 0;
        }
        List<ServiceAppointment> affected = appointmentMapper.selectList(
                new LambdaQueryWrapper<ServiceAppointment>()
                        .eq(ServiceAppointment::getStationId, stationId)
                        .in(ServiceAppointment::getStatus, List.of(
                                WorkflowStatus.APPOINTMENT_PENDING, WorkflowStatus.APPOINTMENT_CONFIRMED)));
        String stationName = stationName(stationId);
        int count = 0;
        for (ServiceAppointment item : affected) {
            if (!com.friendlystation.platform.common.SuspensionWindow.overlaps(
                    from, to, item.getAppointDate(), item.getAppointTime())) {
                continue;
            }
            notificationService.push(item.getUserId(), "APPOINTMENT", "驿站暂停服务提醒",
                    stationName + " 于 " + FormatUtils.dateTime(from) + " 至 " + FormatUtils.dateTime(to)
                            + " 暂停服务（原因：" + (reason == null ? "" : reason) + "）。你预约的 "
                            + FormatUtils.date(item.getAppointDate()) + " " + item.getAppointTime()
                            + " 可能受影响，请与驿站联系。",
                    "", "STATION", stationId);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------ 内部工具

    private void insertRecord(Long appealId, Long handlerId, String action, String content) {
        AppealRecord record = new AppealRecord();
        record.setAppealId(appealId);
        record.setHandlerId(handlerId);
        record.setAction(action);
        record.setContent(content);
        record.setCreateTime(LocalDateTime.now());
        appealRecordMapper.insert(record);
    }

    private List<Map<String, Object>> appointmentViews(List<ServiceAppointment> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, String> stations = stationNames(items.stream().map(ServiceAppointment::getStationId).toList());
        Map<Long, String> users = userNames(items.stream().map(ServiceAppointment::getUserId).toList());
        // 预约事项改为用户自填后不再查服务项目表；批量路径必须与单条路径用同一个取值方法，
        // 否则列表/导出会拿到 null（此前正是这里导致 CSV 导出 500）。
        return items.stream()
                .map(item -> appointmentView(item, stations.get(item.getStationId()),
                        appointmentSubject(item), users.get(item.getUserId())))
                .toList();
    }

    /** 预约事项：优先用用户自填文本；历史数据没有该字段时回退到服务项目名称。 */
    private String appointmentSubject(ServiceAppointment item) {
        String subject = item.getServiceName();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        return serviceName(item.getServiceId());
    }

    private Map<String, Object> appointmentView(ServiceAppointment item, String stationName,
                                                String serviceName, String userName) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("userId", item.getUserId() == null ? 0L : item.getUserId());
        out.put("userName", stationSafe(userName));
        out.put("stationId", item.getStationId() == null ? 0L : item.getStationId());
        out.put("stationName", stationSafe(stationName));
        out.put("serviceId", item.getServiceId() == null ? 0L : item.getServiceId());
        out.put("serviceName", serviceName == null || serviceName.isBlank() ? "未填写沟通事项" : serviceName);
        out.put("contactPhone", item.getContactPhone() == null ? "" : item.getContactPhone());
        out.put("appointDate", FormatUtils.date(item.getAppointDate()));
        out.put("appointTime", item.getAppointTime() == null ? "" : item.getAppointTime());
        out.put("status", item.getStatus());
        out.put("statusLabel", WorkflowStatus.label(item.getStatus()));
        out.put("auditReason", item.getAuditReason() == null ? "" : item.getAuditReason());
        // 爽约：用户预约了没来。驿站据此判断是否还值得排时段给这个人。
        boolean noShow = item.getNoShow() != null && item.getNoShow() == 1;
        out.put("noShow", noShow);
        out.put("noShowLabel", noShow ? "已爽约" : "");
        out.put("noShowAt", FormatUtils.dateTime(item.getNoShowAt()));
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    private List<Map<String, Object>> appealViews(List<Appeal> items, boolean maskAnonymous) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Map<String, Object>>> records = recordsByAppeal(items.stream().map(Appeal::getId).toList());
        List<Long> userIds = new ArrayList<>();
        items.forEach(item -> {
            if (item.getUserId() != null) {
                userIds.add(item.getUserId());
            }
            if (item.getHandlerId() != null) {
                userIds.add(item.getHandlerId());
            }
        });
        Map<Long, String> users = userNames(userIds);
        List<Long> appealIds = items.stream().map(Appeal::getId).filter(Objects::nonNull).toList();
        java.util.Set<Long> evaluatedIds = appealIds.isEmpty() ? java.util.Set.of()
                : appealEvaluationMapper.selectList(new LambdaQueryWrapper<AppealEvaluation>()
                        .in(AppealEvaluation::getAppealId, appealIds)
                        .select(AppealEvaluation::getAppealId))
                .stream().map(AppealEvaluation::getAppealId).collect(Collectors.toSet());
        return items.stream()
                .map(item -> appealView(item, users.get(item.getUserId()), users.get(item.getHandlerId()),
                        records.getOrDefault(item.getId(), List.of()), maskAnonymous,
                        evaluatedIds.contains(item.getId())))
                .toList();
    }

    private Map<String, Object> appealView(Appeal item, String userName, String handlerName,
                                           List<Map<String, Object>> records) {
        return appealView(item, userName, handlerName, records, false, false);
    }

    private Map<String, Object> appealView(Appeal item, String userName, String handlerName,
                                           List<Map<String, Object>> records, boolean maskAnonymous,
                                           boolean evaluated) {
        boolean anonymous = Boolean.TRUE.equals(item.getAnonymous());
        boolean maskThis = maskAnonymous && anonymous;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("userId", maskThis ? 0L : (item.getUserId() == null ? 0L : item.getUserId()));
        out.put("userName", maskThis ? "匿名用户" : stationSafe(userName));
        out.put("title", item.getTitle());
        String category = AppealCategory.normalize(item.getCategory());
        out.put("category", category);
        out.put("categoryLabel", AppealCategory.label(category));
        out.put("positive", AppealCategory.isPositive(category));
        out.put("type", item.getAppealType());
        out.put("stationId", item.getStationId() == null ? 0L : item.getStationId());
        out.put("area", item.getArea() == null ? "" : item.getArea());
        out.put("content", item.getContent() == null ? "" : item.getContent());
        out.put("anonymous", anonymous);
        out.put("status", item.getStatus());
        // 两套文案，对应两类读者：
        // - statusLabel：给用户看，措辞随分类变化（表扬读作「心意已收到」而不是「待受理」）；
        // - statusLabelStandard：给管理端看，保留「待受理/已分派/处理中/已办结」标准术语，
        //   因为开题与中期报告都明确要求「提交→受理→分派→处理→办结→评价」的闭环必须可见。
        //   状态机本身完全一致，只是同一状态对两类读者的表达不同。
        out.put("statusLabel", AppealCategory.statusLabel(category, item.getStatus()));
        out.put("statusLabelStandard", WorkflowStatus.label(item.getStatus()));
        out.put("handlerId", item.getHandlerId() == null ? 0L : item.getHandlerId());
        out.put("handlerName", stationSafe(handlerName));
        out.put("finishTime", FormatUtils.dateTime(item.getFinishTime()));
        // 受理方以用户选择的反馈对象为准，而不是按类型猜测
        String receiverTarget = AppealCategory.resolveTarget(item.getReceiverTarget(), category, item.getAppealType());
        out.put("receiverTarget", receiverTarget);
        out.put("receiverTargetLabel", AppealCategory.targetLabel(receiverTarget));
        out.put("receiverType", AppealCategory.receiverRole(receiverTarget));
        out.put("receiverName", AppealCategory.receiverName(receiverTarget));
        out.put("handlingBoundary", WorkflowStatus.handlingBoundary(item.getAppealType()));
        List<Map<String, Object>> visibleRecords = maskThis ? maskSubmitRecords(records) : records;
        out.put("recordCount", visibleRecords.size());
        out.put("records", visibleRecords);
        out.put("evaluated", evaluated);
        out.put("dataStatus", "系统计算结果");
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        return out;
    }

    /** 匿名诉求只对提交人本人展示身份；管理员列表与详情一律打码。 */
    private static boolean shouldMask(Appeal item, long viewerId) {
        return Boolean.TRUE.equals(item.getAnonymous())
                && (item.getUserId() == null || item.getUserId() != viewerId);
    }

    /** 匿名诉求的提交流水会把提交人写成 handler，需一并打码。 */
    private static List<Map<String, Object>> maskSubmitRecords(List<Map<String, Object>> records) {
        for (Map<String, Object> record : records) {
            if ("SUBMIT".equals(record.get("action"))) {
                record.put("handlerId", 0L);
                record.put("handlerName", "匿名用户");
            }
        }
        return records;
    }

    /** 定向到驿站的诉求必须落到一个真实存在的驿站；未定向返回 null。 */
    private Long resolveAppealStation(Map<String, Object> request, String receiverTarget) {
        if (!AppealCategory.TARGET_STATION.equals(receiverTarget)) {
            return null;
        }
        long raw = FormatUtils.number(request.get("stationId"));
        if (raw <= 0) {
            return null;
        }
        Station station = stationMapper.selectById(raw);
        if (station == null || "DELETED".equalsIgnoreCase(station.getStatus())) {
            throw new BusinessException("驿站不存在");
        }
        return raw;
    }

    private Map<Long, List<Map<String, Object>>> recordsByAppeal(Collection<Long> appealIds) {
        List<Long> ids = appealIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<AppealRecord> records = appealRecordMapper.selectList(new LambdaQueryWrapper<AppealRecord>()
                .in(AppealRecord::getAppealId, ids)
                .orderByAsc(AppealRecord::getCreateTime)
                .orderByAsc(AppealRecord::getId));
        Map<Long, String> handlers = userNames(records.stream().map(AppealRecord::getHandlerId).toList());
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (AppealRecord record : records) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", record.getId());
            out.put("appealId", record.getAppealId());
            out.put("action", record.getAction());
            out.put("actionLabel", WorkflowStatus.actionLabel(record.getAction()));
            out.put("content", record.getContent() == null ? "" : record.getContent());
            out.put("handlerId", record.getHandlerId() == null ? 0L : record.getHandlerId());
            out.put("handlerName", stationSafe(handlers.get(record.getHandlerId())));
            out.put("createTime", FormatUtils.dateTime(record.getCreateTime()));
            grouped.computeIfAbsent(record.getAppealId(), key -> new ArrayList<>()).add(out);
        }
        return grouped;
    }

    private List<Map<String, Object>> signupViews(List<ActivitySignup> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, String> users = userNames(items.stream().map(ActivitySignup::getUserId).toList());
        Map<Long, Activity> activities = activitiesById(items.stream().map(ActivitySignup::getActivityId).toList());
        return items.stream().map(item -> {
            Map<String, Object> out = signupView(item, activities.get(item.getActivityId()));
            out.put("userName", stationSafe(users.get(item.getUserId())));
            return out;
        }).toList();
    }

    private Map<String, Object> signupView(ActivitySignup item, Activity activity) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("activityId", item.getActivityId());
        out.put("activityTitle", activity == null || activity.getTitle() == null ? "" : activity.getTitle());
        // 报名归属：管理端按驿站过滤导出/列表时要用，避免驿站管理员看到别家报名。
        out.put("stationId", activity == null || activity.getStationId() == null ? 0L : activity.getStationId());
        out.put("userId", item.getUserId());
        out.put("userName", userName(item.getUserId()));
        // 报名信息：承办方要靠这些联系报名人，因此列表里直接给出，不必再点开
        out.put("contactName", item.getContactName() == null ? "" : item.getContactName());
        out.put("phone", item.getPhone() == null ? "" : item.getPhone());
        out.put("remark", item.getRemark() == null ? "" : item.getRemark());
        out.put("status", ActivityOrganizer.normalizeSignupStatus(item.getStatus()));
        out.put("statusLabel", ActivityOrganizer.signupLabel(item.getStatus()));
        // 承办方的审核反馈，报名人会看到
        out.put("reviewNote", item.getReviewNote() == null ? "" : item.getReviewNote());
        out.put("reviewerName", item.getReviewerId() == null ? "" : userName(item.getReviewerId()));
        out.put("reviewTime", FormatUtils.dateTime(item.getReviewTime()));
        out.put("dataStatus", "系统计算结果");
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        return out;
    }

    /**
     * 承办方处理报名：联系、审核通过或未通过，并给出反馈。
     *
     * <p>报名人提交的信息（姓名、电话）是为了让承办方能联系到他；
     * 处理结果与反馈会回传给报名人，形成一次完整的双向沟通。</p>
     */
    @Transactional
    public Map<String, Object> reviewSignup(Map<String, Object> request, long operatorId, List<Long> stationIds) {
        long id = FormatUtils.number(request.get("id"));
        String status = FormatUtils.text(request.get("status"), ActivityOrganizer.SIGNUP_APPROVED).toUpperCase();
        if (!ActivityOrganizer.isReviewTarget(status)) {
            throw new BusinessException("报名处理状态只能是 CONTACTED、APPROVED 或 REJECTED");
        }
        ActivitySignup signup = signupMapper.selectById(id);
        if (signup == null) {
            throw new BusinessException("报名记录不存在");
        }
        Activity activity = activityMapper.selectById(signup.getActivityId());
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        if (stationIds != null
                && (activity.getStationId() == null || !stationIds.contains(activity.getStationId()))) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权处理该活动的报名");
        }
        String currentStatus = ActivityOrganizer.normalizeSignupStatus(signup.getStatus());
        if (ActivityOrganizer.SIGNUP_CANCELLED.equals(currentStatus)) {
            throw new BusinessException("该报名已取消，不能处理");
        }
        // APPROVED / REJECTED 是终态：反复审核会让报名人看到的结论来回翻转。
        if (ActivityOrganizer.SIGNUP_APPROVED.equals(currentStatus)
                || ActivityOrganizer.SIGNUP_REJECTED.equals(currentStatus)) {
            throw new BusinessException("该报名已审核，不能重复处理");
        }
        String note = FormatUtils.text(request.get("reviewNote"), "");
        // 审核未通过必须说明原因——报名人有权知道为什么
        if (ActivityOrganizer.SIGNUP_REJECTED.equals(status) && note.isBlank()) {
            throw new BusinessException("审核未通过时请填写原因，报名人会看到这段说明");
        }
        signup.setStatus(status);
        signup.setReviewNote(note);
        signup.setReviewerId(operatorId);
        signup.setReviewTime(LocalDateTime.now());
        signupMapper.updateById(signup);
        String signupTitle = switch (status) {
            case ActivityOrganizer.SIGNUP_APPROVED -> "报名审核通过";
            case ActivityOrganizer.SIGNUP_REJECTED -> "报名未通过";
            default -> "承办方已联系你";
        };
        notificationService.push(signup.getUserId(), "SIGNUP", signupTitle,
                activity.getTitle() + (note.isBlank() ? "" : "；" + note),
                "", "SIGNUP", id);
        return signupView(signup, activity);
    }

    @Override
    @Transactional
    public Map<String, Object> cancelSignup(long id, long userId) {
        ActivitySignup signup = signupMapper.selectById(id);
        if (signup == null) {
            throw new BusinessException("报名记录不存在");
        }
        if (signup.getUserId() == null || signup.getUserId() != userId) {
            throw new BusinessException("只能取消本人提交的报名");
        }
        // 条件更新：已审核（通过/未通过）或已取消的报名是终态，不能再由用户单方面撤销。
        int updated = signupMapper.update(null, new LambdaUpdateWrapper<ActivitySignup>()
                .eq(ActivitySignup::getId, id)
                .eq(ActivitySignup::getUserId, userId)
                .in(ActivitySignup::getStatus, List.of(ActivityOrganizer.SIGNUP_SIGNED_UP,
                        ActivityOrganizer.SIGNUP_CONTACTED))
                .set(ActivitySignup::getStatus, ActivityOrganizer.SIGNUP_CANCELLED));
        if (updated == 0) {
            throw new BusinessException("报名已审核或已取消，不能取消");
        }
        ActivitySignup updatedSignup = signupMapper.selectById(id);
        Activity activity = activityMapper.selectById(updatedSignup.getActivityId());
        notifyOrganizerCancel(activity, updatedSignup.getContactName(), id);
        return signupView(updatedSignup, activity);
    }

    /** 报名成功后提醒承办方（活动所属驿站的负责人）联系报名人。 */
    private void notifyOrganizer(Activity activity, String contactName, Long signupId) {
        if (activity == null || activity.getStationId() == null || signupId == null) {
            return;
        }
        Station station = stationMapper.selectById(activity.getStationId());
        if (station == null || station.getManagerId() == null) {
            return;
        }
        notificationService.push(station.getManagerId(), "SIGNUP", "新的活动报名",
                activity.getTitle() + " · " + contactName, "", "SIGNUP", signupId);
    }

    /** 报名取消后提醒承办方，避免对方还在按原名单联系。 */
    private void notifyOrganizerCancel(Activity activity, String contactName, Long signupId) {
        if (activity == null || activity.getStationId() == null || signupId == null) {
            return;
        }
        Station station = stationMapper.selectById(activity.getStationId());
        if (station == null || station.getManagerId() == null) {
            return;
        }
        notificationService.push(station.getManagerId(), "SIGNUP", "报名已取消",
                activity.getTitle() + " · " + (contactName == null ? "" : contactName),
                "", "SIGNUP", signupId);
    }

    private Map<Long, Activity> activitiesById(Collection<Long> ids) {
        List<Long> clean = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (clean.isEmpty()) {
            return Map.of();
        }
        return activityMapper.selectBatchIds(clean).stream()
                .collect(Collectors.toMap(Activity::getId, java.util.function.Function.identity(), (first, second) -> first));
    }

    private Map<Long, String> stationNames(Collection<Long> ids) {
        List<Long> clean = ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (clean.isEmpty()) {
            // 返回可变 Map：Map.of() 是不可变集合，get(null) 会抛 NullPointerException。
            return new HashMap<>();
        }
        return stationMapper.selectBatchIds(clean).stream()
                .collect(Collectors.toMap(Station::getId, Station::getStationName, (first, second) -> first));
    }

    private Map<Long, String> serviceNames(Collection<Long> ids) {
        List<Long> clean = ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (clean.isEmpty()) {
            return new HashMap<>();
        }
        return serviceItemMapper.selectBatchIds(clean).stream()
                .collect(Collectors.toMap(StationServiceItem::getId, StationServiceItem::getServiceName, (first, second) -> first));
    }

    private Map<Long, String> userNames(Collection<Long> ids) {
        List<Long> clean = ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (clean.isEmpty()) {
            return new HashMap<>();
        }
        return userMapper.selectBatchIds(clean).stream()
                .collect(Collectors.toMap(SysUser::getId, MysqlWorkflowService::displayName, (first, second) -> first));
    }

    private String stationName(Long stationId) {
        if (stationId == null || stationId <= 0) {
            return "";
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? "" : station.getStationName();
    }

    private String serviceName(Long serviceId) {
        if (serviceId == null || serviceId <= 0) {
            return "";
        }
        StationServiceItem item = serviceItemMapper.selectById(serviceId);
        return item == null ? "" : item.getServiceName();
    }

    private String userName(Long userId) {
        if (userId == null || userId <= 0) {
            return "";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? "" : displayName(user);
    }

    private static String displayName(SysUser user) {
        return user.getRealName() == null || user.getRealName().isBlank() ? user.getUsername() : user.getRealName();
    }

    private static String stationSafe(String value) {
        return value == null ? "" : value;
    }

    /** 供统计模块复用的实体字段映射。 */
    private static <T, R> Map<Long, R> index(Collection<T> items, Function<T, Long> key, Function<T, R> value) {
        return items.stream().filter(item -> key.apply(item) != null)
                .collect(Collectors.toMap(key, value, (first, second) -> first));
    }
}
