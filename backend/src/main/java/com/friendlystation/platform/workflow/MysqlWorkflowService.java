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

    public MysqlWorkflowService(ServiceAppointmentMapper appointmentMapper,
                                AppealMapper appealMapper,
                                AppealRecordMapper appealRecordMapper,
                                AppealEvaluationMapper appealEvaluationMapper,
                                ActivitySignupMapper signupMapper,
                                StationMapper stationMapper,
                                StationServiceItemMapper serviceItemMapper,
                                ActivityMapper activityMapper,
                                SysUserMapper userMapper) {
        this.appointmentMapper = appointmentMapper;
        this.appealMapper = appealMapper;
        this.appealRecordMapper = appealRecordMapper;
        this.appealEvaluationMapper = appealEvaluationMapper;
        this.signupMapper = signupMapper;
        this.stationMapper = stationMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.activityMapper = activityMapper;
        this.userMapper = userMapper;
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
        if (station == null) {
            throw new BusinessException("驿站不存在");
        }
        Object rawDate = request.get("date") == null ? request.get("appointDate") : request.get("date");
        String slot = FormatUtils.text(request.get("slot"), FormatUtils.text(request.get("appointTime"), "待确认"));

        // 预约时段必须落在驿站营业时间内。营业时间来自公开来源文本或高德地图同步；
        // 缺失或无法解析时不拦截（判断不了就不挡），避免因数据缺失误拒正常预约。
        if (!BusinessHours.contains(station.getOpenTime(), slot)) {
            throw new BusinessException("所选时段 " + slot + " 不在该驿站的营业时间内（"
                    + BusinessHours.describe(station.getOpenTime()) + "）");
        }

        // 预约事项由用户自填（服务项目不再从固定列表里选），并要求留下联系电话便于回电确认。
        String subject = AppointmentSubject.require(
                request.get("serviceName") == null ? request.get("subject") : request.get("serviceName"));
        String contactPhone = AppointmentSubject.optionalPhone(request.get("contactPhone"));

        ServiceAppointment item = new ServiceAppointment();
        item.setUserId(userId);
        item.setStationId(stationId);
        // 兼容历史字段：新提交可以不带 serviceId。
        long serviceId = FormatUtils.number(request.get("serviceId"));
        item.setServiceId(serviceId > 0 ? serviceId : null);
        item.setServiceName(subject);
        item.setContactPhone(contactPhone);
        item.setAppointDate(FormatUtils.date(rawDate));
        item.setAppointTime(slot);
        item.setStatus(WorkflowStatus.APPOINTMENT_PENDING);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        appointmentMapper.insert(item);

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
    public Map<String, Object> auditAppointment(Map<String, Object> request, long operatorId) {
        String status = FormatUtils.text(request.get("status"), WorkflowStatus.APPOINTMENT_CONFIRMED);
        if (!WorkflowStatus.isAuditTarget(status)) {
            throw new BusinessException("预约审核结果只能是 CONFIRMED 或 REJECTED");
        }
        long id = FormatUtils.number(request.get("id"));
        String reason = FormatUtils.text(request.get("reason"), "");

        // 条件更新：只有仍处于「待驿站确认」的记录会被更新，并发下重复审核只会成功一次。
        int updated = appointmentMapper.update(null, new LambdaUpdateWrapper<ServiceAppointment>()
                .eq(ServiceAppointment::getId, id)
                .eq(ServiceAppointment::getStatus, WorkflowStatus.APPOINTMENT_PENDING)
                .set(ServiceAppointment::getStatus, status)
                .set(ServiceAppointment::getAuditReason, reason)
                .set(ServiceAppointment::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            if (appointmentMapper.selectById(id) == null) {
                throw new BusinessException("预约不存在");
            }
            throw new BusinessException("该预约已处理，不能重复审核");
        }

        ServiceAppointment item = appointmentMapper.selectById(id);
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
        Appeal item = new Appeal();
        item.setUserId(userId);
        item.setTitle(FormatUtils.text(request.get("title"), "未填写标题"));
        item.setCategory(category);
        item.setReceiverTarget(receiverTarget);
        item.setAppealType(type);
        item.setArea(FormatUtils.text(request.get("area"), "未填写区域"));
        item.setContent(FormatUtils.text(request.get("content"), ""));
        item.setAnonymous(FormatUtils.flag(request.get("anonymous")));
        item.setStatus(WorkflowStatus.APPEAL_PENDING);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        appealMapper.insert(item);

        insertRecord(item.getId(), userId, "SUBMIT", AppealCategory.label(category) + "已提交");
        return appealView(item, userName(userId), userName(null),
                recordsByAppeal(List.of(item.getId())).getOrDefault(item.getId(), List.of()));
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
                                                      List<String> areas) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        if (areas != null && areas.isEmpty()) {
            return new PageResult<>(safeCurrent, safeSize, 0, List.of());
        }
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(Appeal::getStatus, status.trim());
        }
        if (areas != null) {
            query.in(Appeal::getArea, areas);
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
    public Map<String, Object> appealDetail(long id) {
        Appeal item = appealMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("诉求不存在");
        }
        return appealViews(List.of(item), false).get(0);
    }

    @Override
    public List<Map<String, Object>> appealRecords(long appealId) {
        if (appealMapper.selectById(appealId) == null) {
            throw new BusinessException("诉求不存在");
        }
        return recordsByAppeal(List.of(appealId)).getOrDefault(appealId, List.of());
    }

    @Override
    @Transactional
    public Map<String, Object> assignAppeal(Map<String, Object> request, long operatorId) {
        long id = FormatUtils.number(request.get("id"));
        // 条件更新：只有待受理 / 已分派 / 处理中的诉求可以被分派，并发下只会成功一次。
        int updated = appealMapper.update(null, new LambdaUpdateWrapper<Appeal>()
                .eq(Appeal::getId, id)
                .in(Appeal::getStatus, List.of(WorkflowStatus.APPEAL_PENDING, WorkflowStatus.APPEAL_ASSIGNED,
                        WorkflowStatus.APPEAL_PROCESSING))
                .set(Appeal::getStatus, WorkflowStatus.APPEAL_ASSIGNED)
                .set(Appeal::getHandlerId, operatorId)
                .set(Appeal::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            if (appealMapper.selectById(id) == null) {
                throw new BusinessException("诉求不存在");
            }
            throw new BusinessException("该诉求已办结或关闭，不能再次分派");
        }
        insertRecord(id, operatorId, "ASSIGN", FormatUtils.text(request.get("content"), "已分派处理人"));
        return appealDetail(id);
    }

    @Override
    @Transactional
    public Map<String, Object> handleAppeal(Map<String, Object> request, long operatorId) {
        String status = FormatUtils.text(request.get("status"), WorkflowStatus.APPEAL_RESOLVED);
        if (!WorkflowStatus.isHandleTarget(status)) {
            throw new BusinessException("诉求处理状态只能是 PROCESSING、RESOLVED 或 CLOSED");
        }
        long id = FormatUtils.number(request.get("id"));
        String content = FormatUtils.text(request.get("content"), WorkflowStatus.label(status));
        boolean resolved = WorkflowStatus.APPEAL_RESOLVED.equals(status);

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
        return appealDetail(id);
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
        Long duplicated = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException("该活动已报名");
        }
        int maxCount = activity.getMaxCount() == null ? 0 : activity.getMaxCount();
        Long signed = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId));
        if (maxCount > 0 && signed != null && signed >= maxCount) {
            throw new BusinessException("活动名额已满");
        }

        ActivitySignup item = new ActivitySignup();
        item.setActivityId(activityId);
        item.setUserId(userId);
        item.setPhone(FormatUtils.text(request.get("phone"), ""));
        item.setStatus("SUBMITTED");
        item.setCreateTime(LocalDateTime.now());
        try {
            signupMapper.insert(item);
        } catch (DuplicateKeyException exception) {
            // 唯一索引 uk_activity_user 兜底：把约束冲突转换为可读的业务提示。
            throw new BusinessException("该活动已报名");
        }
        return signupView(item, activity.getTitle());
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
    public List<Map<String, Object>> allSignups() {
        List<ActivitySignup> items = signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                .orderByDesc(ActivitySignup::getCreateTime)
                .orderByDesc(ActivitySignup::getId));
        return signupViews(items);
    }

    // ------------------------------------------------------------------ 概览

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appointments", appointmentMapper.selectCount(null));
        out.put("pendingAppointments", appointmentMapper.selectCount(new LambdaQueryWrapper<ServiceAppointment>()
                .eq(ServiceAppointment::getStatus, WorkflowStatus.APPOINTMENT_PENDING)));
        out.put("appeals", appealMapper.selectCount(null));
        out.put("pendingAppeals", appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .ne(Appeal::getStatus, WorkflowStatus.APPEAL_RESOLVED)
                .ne(Appeal::getStatus, WorkflowStatus.APPEAL_CLOSED)));
        out.put("resolvedAppeals", appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getStatus, WorkflowStatus.APPEAL_RESOLVED)));
        out.put("signups", signupMapper.selectCount(null));
        // 诉求评价数：与工作流概览中的诉求数据同源，避免与驿站服务评价混淆。
        out.put("appealEvaluations", appealEvaluationMapper.selectCount(null));
        out.put("dataStatus", "系统计算结果");
        out.put("status", "READY");
        return out;
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
        return items.stream()
                .map(item -> appealView(item, users.get(item.getUserId()), users.get(item.getHandlerId()),
                        records.getOrDefault(item.getId(), List.of()), maskAnonymous))
                .toList();
    }

    private Map<String, Object> appealView(Appeal item, String userName, String handlerName,
                                           List<Map<String, Object>> records) {
        return appealView(item, userName, handlerName, records, false);
    }

    private Map<String, Object> appealView(Appeal item, String userName, String handlerName,
                                           List<Map<String, Object>> records, boolean maskAnonymous) {
        boolean anonymous = Boolean.TRUE.equals(item.getAnonymous());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("userId", item.getUserId() == null ? 0L : item.getUserId());
        out.put("userName", maskAnonymous && anonymous ? "匿名用户" : stationSafe(userName));
        out.put("title", item.getTitle());
        String category = AppealCategory.normalize(item.getCategory());
        out.put("category", category);
        out.put("categoryLabel", AppealCategory.label(category));
        out.put("positive", AppealCategory.isPositive(category));
        out.put("type", item.getAppealType());
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
        out.put("recordCount", records.size());
        out.put("records", records);
        out.put("dataStatus", "系统计算结果");
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        return out;
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
        Map<Long, String> titles = activityTitles(items.stream().map(ActivitySignup::getActivityId).toList());
        return items.stream().map(item -> {
            Map<String, Object> out = signupView(item, titles.get(item.getActivityId()));
            out.put("userName", stationSafe(users.get(item.getUserId())));
            return out;
        }).toList();
    }

    private Map<String, Object> signupView(ActivitySignup item, String activityTitle) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("activityId", item.getActivityId());
        out.put("activityTitle", activityTitle == null ? "" : activityTitle);
        out.put("userId", item.getUserId());
        out.put("userName", userName(item.getUserId()));
        out.put("phone", item.getPhone() == null ? "" : item.getPhone());
        out.put("status", item.getStatus());
        out.put("statusLabel", "SUBMITTED".equals(item.getStatus()) ? "已提交报名" : item.getStatus());
        out.put("dataStatus", "系统计算结果");
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        return out;
    }

    private Map<Long, String> activityTitles(Collection<Long> ids) {
        List<Long> clean = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (clean.isEmpty()) {
            return Map.of();
        }
        return activityMapper.selectBatchIds(clean).stream()
                .collect(Collectors.toMap(Activity::getId, Activity::getTitle, (first, second) -> first));
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
