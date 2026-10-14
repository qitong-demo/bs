package com.friendlystation.platform.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.workflow.ActivityOrganizer;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.Activity;
import com.friendlystation.platform.persistence.entity.ActivitySignup;
import com.friendlystation.platform.persistence.entity.Station;
import com.friendlystation.platform.persistence.mapper.ActivityMapper;
import com.friendlystation.platform.persistence.mapper.ActivitySignupMapper;
import com.friendlystation.platform.persistence.mapper.StationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** mysql Profile 下的活动实现，报名人数取自 activity_signup 表。 */
@Service
@Profile("mysql")
public class MysqlActivityService implements ActivityService {
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final StationMapper stationMapper;

    public MysqlActivityService(ActivityMapper activityMapper, ActivitySignupMapper signupMapper, StationMapper stationMapper) {
        this.activityMapper = activityMapper;
        this.signupMapper = signupMapper;
        this.stationMapper = stationMapper;
    }

    @Override
    public PageResult<Map<String, Object>> publicPage(long current, long size, Long stationId, String keyword) {
        return doPage(current, size, "PUBLISHED", stationId, keyword);
    }

    @Override
    public PageResult<Map<String, Object>> adminPage(long current, long size, String status, String keyword) {
        return doPage(current, size, status, null, keyword);
    }

    @Override
    public Map<String, Object> detail(Long id) {
        return toMap(require(id));
    }

    @Override
    @Transactional
    public Map<String, Object> create(Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        String title = requireTitle(data.get("title"));
        Long stationId = ContentSupport.toNullableLong(data.get("stationId"));
        requireStation(stationId);
        int maxCount = requireMaxCount(data.get("maxCount"));
        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setStationId(stationId);
        LocalDateTime activityTime = ContentSupport.parseDateTime(ContentSupport.trimText(data.get("activityTime")));
        activity.setActivityTime(activityTime == null ? LocalDateTime.now() : activityTime);
        activity.setAddress(ContentSupport.trimText(data.get("address")));
        activity.setMaxCount(maxCount);
        activity.setContent(ContentSupport.trimText(data.get("content")));
        activity.setStatus(ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"));
        applyOrganizer(activity, data);
        activityMapper.insert(activity);
        return toMap(require(activity.getId()));
    }

    /**
     * 设置承办方。
     *
     * <p>第三方活动（EXTERNAL）必须给出跳转链接——它在站内只做展示，
     * 报名要去对方平台，没有链接用户就无处可去。</p>
     */
    private void applyOrganizer(Activity activity, Map<String, Object> data) {
        String type = ActivityOrganizer.normalize(data.get("organizerType"));
        String externalUrl = ContentSupport.trimText(data.get("externalUrl"));
        if (ActivityOrganizer.TYPE_EXTERNAL.equals(type)) {
            if (externalUrl.isEmpty()) {
                throw new BusinessException("第三方活动必须填写报名或详情链接");
            }
            if (!externalUrl.startsWith("http://") && !externalUrl.startsWith("https://")) {
                throw new BusinessException("活动链接需以 http:// 或 https:// 开头");
            }
        } else {
            // 非第三方活动不接受外部链接，避免出现两套报名入口
            externalUrl = "";
        }
        activity.setOrganizerType(type);
        activity.setExternalUrl(externalUrl);
    }

    @Override
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> payload) {
        Activity activity = require(id);
        Map<String, Object> data = payload == null ? Map.of() : payload;
        if (data.containsKey("title")) {
            activity.setTitle(requireTitle(data.get("title")));
        }
        if (data.containsKey("stationId")) {
            Long stationId = ContentSupport.toNullableLong(data.get("stationId"));
            requireStation(stationId);
            activity.setStationId(stationId);
        }
        if (data.containsKey("activityTime")) {
            LocalDateTime activityTime = ContentSupport.parseDateTime(ContentSupport.trimText(data.get("activityTime")));
            if (activityTime != null) {
                activity.setActivityTime(activityTime);
            }
        }
        if (data.containsKey("address")) {
            activity.setAddress(ContentSupport.trimText(data.get("address")));
        }
        if (data.containsKey("maxCount")) {
            activity.setMaxCount(requireMaxCount(data.get("maxCount")));
        }
        if (data.containsKey("content")) {
            activity.setContent(ContentSupport.trimText(data.get("content")));
        }
        if (data.containsKey("organizerType") || data.containsKey("externalUrl")) {
            Map<String, Object> merged = new java.util.HashMap<>(data);
            merged.putIfAbsent("organizerType", activity.getOrganizerType());
            applyOrganizer(activity, merged);
        }        if (data.containsKey("status")) {
            activity.setStatus(ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"));
        }
        activityMapper.updateById(activity);
        return toMap(require(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        require(id);
        long signed = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, id));
        // 已有人报名的活动不能删：activity_signup 有外键指向 activity，
        // 原先直接 deleteById 会撞外键、抛出 500「服务器内部错误」，
        // 管理员只看到一句无意义的报错。
        // 这里也不做级联删除——删掉报名记录会让骑手已提交的报名凭空消失，
        // 他们那边只会看到活动不见了、自己的报名也没了。
        if (signed > 0) {
            throw new BusinessException("该活动已有 " + signed + " 人报名，不能直接删除。"
                    + "请改为取消活动，已报名的骑手仍能看到记录与取消说明。");
        }
        activityMapper.deleteById(id);
    }

    @Override
    public int remainingSlots(Long activityId) {
        Activity activity = require(activityId);
        return remaining(activity, signedCount(activityId));
    }

    /** 来源等级中文文案，与 news 的 source_level 同一套口径。 */
    private static String sourceLevelLabel(String level) {
        if (level == null || level.isBlank()) {
            return "驿站自办";
        }
        return switch (level.trim().toUpperCase()) {
            case "GOV" -> "政府群团";
            case "MEDIA" -> "官方媒体";
            case "PLATFORM" -> "平台自办";
            default -> "驿站自办";
        };
    }

    private PageResult<Map<String, Object>> doPage(long current, long size, String status, Long stationId, String keyword) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<Activity> query = new LambdaQueryWrapper<Activity>()
                .orderByDesc(Activity::getActivityTime)
                .orderByDesc(Activity::getId);
        if (!ContentSupport.isBlank(status)) {
            query.eq(Activity::getStatus, status.trim());
        }
        if (stationId != null) {
            query.eq(Activity::getStationId, stationId);
        }
        if (!ContentSupport.isBlank(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(Activity::getTitle, normalizedKeyword)
                    .or()
                    .like(Activity::getAddress, normalizedKeyword)
                    .or()
                    .like(Activity::getContent, normalizedKeyword));
        }
        Page<Activity> result = activityMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        List<Map<String, Object>> records = result.getRecords().stream().map(this::toMap).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    private Activity require(Long id) {
        if (id == null) {
            throw new BusinessException("活动不存在");
        }
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        return activity;
    }

    private static String requireTitle(Object value) {
        String title = ContentSupport.trimText(value);
        if (title.isEmpty()) {
            throw new BusinessException("活动标题不能为空");
        }
        return title;
    }

    private static int requireMaxCount(Object value) {
        long maxCount = ContentSupport.toLong(value, 50L);
        if (maxCount <= 0) {
            throw new BusinessException("活动名额必须大于 0");
        }
        return (int) Math.min(maxCount, Integer.MAX_VALUE);
    }

    /** 校验驿站是否存在，不存在抛出业务异常；stationId 为空时表示不限定驿站。 */
    private void requireStation(Long stationId) {
        if (stationId == null) {
            return;
        }
        Station station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException("驿站不存在");
        }
    }

    /** 统计该活动的报名人数。 */
    private int signedCount(Long activityId) {
        if (activityId == null) {
            return 0;
        }
        long count = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId));
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private static int remaining(Activity activity, int signedCount) {
        int maxCount = activity.getMaxCount() == null ? 0 : activity.getMaxCount();
        return Math.max(maxCount - signedCount, 0);
    }

    /** 转换为对外固定字段结构。 */
    private Map<String, Object> toMap(Activity activity) {
        int signedCount = signedCount(activity.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", activity.getId());
        out.put("title", activity.getTitle());
        out.put("stationId", activity.getStationId() == null ? 0L : activity.getStationId());
        out.put("stationName", stationName(activity.getStationId()));
        out.put("activityTime", ContentSupport.formatDateTime(activity.getActivityTime()));
        out.put("address", activity.getAddress() == null ? "" : activity.getAddress());
        out.put("maxCount", activity.getMaxCount() == null ? 0 : activity.getMaxCount());
        out.put("signedCount", signedCount);
        out.put("remaining", remaining(activity, signedCount));
        out.put("content", activity.getContent() == null ? "" : activity.getContent());
        out.put("status", activity.getStatus());
        out.put("statusLabel", ContentSupport.statusLabel(activity.getStatus()));
        // 承办方与报名入口：第三方活动给出跳转链接，用户无从报名会以为功能坏了
        String organizer = ActivityOrganizer.normalize(activity.getOrganizerType());
        out.put("organizerType", organizer);
        out.put("organizerLabel", ActivityOrganizer.typeLabel(organizer));
        out.put("externalUrl", activity.getExternalUrl() == null ? "" : activity.getExternalUrl());
        // 采集来的真实活动：必须能核对到原文与主办方，字段缺失时返回空串而不是省略，
        // 否则前端拿到 undefined 会显示成 "undefined"。
        out.put("cityCode", activity.getCityCode() == null ? "" : activity.getCityCode());
        out.put("publisher", activity.getPublisher() == null ? "" : activity.getPublisher());
        out.put("category", activity.getCategory() == null ? "" : activity.getCategory());
        out.put("summary", activity.getSummary() == null ? "" : activity.getSummary());
        out.put("sourceLevel", activity.getSourceLevel() == null ? "" : activity.getSourceLevel());
        out.put("sourceLevelLabel", sourceLevelLabel(activity.getSourceLevel()));
        out.put("acceptsSignup", ActivityOrganizer.acceptsSignup(organizer));
        out.put("createTime", ContentSupport.formatDateTime(activity.getCreateTime()));
        return out;
    }

    /** 查询驿站名称，查不到返回空串。 */
    private String stationName(Long stationId) {
        if (stationId == null) {
            return "";
        }
        Station station = stationMapper.selectById(stationId);
        return station == null || station.getStationName() == null ? "" : station.getStationName();
    }
}
