package com.friendlystation.platform.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.Station;
import com.friendlystation.platform.persistence.entity.StationEvaluation;
import com.friendlystation.platform.persistence.entity.SysUser;
import com.friendlystation.platform.persistence.mapper.StationEvaluationMapper;
import com.friendlystation.platform.persistence.mapper.StationMapper;
import com.friendlystation.platform.persistence.mapper.SysUserMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库版驿站评价服务：使用 station_evaluation 表，供 mysql Profile 使用。
 * 对外返回字段与 {@link MockStationEvaluationService} 完全一致。
 */
@Service
@Profile("mysql")
public class MysqlStationEvaluationService implements StationEvaluationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StationEvaluationMapper evaluationMapper;
    private final StationMapper stationMapper;
    private final SysUserMapper sysUserMapper;

    public MysqlStationEvaluationService(StationEvaluationMapper evaluationMapper,
                                         StationMapper stationMapper,
                                         SysUserMapper sysUserMapper) {
        this.evaluationMapper = evaluationMapper;
        this.stationMapper = stationMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    @Transactional
    public Map<String, Object> create(Map<String, Object> payload, long userId) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        long stationId = requireStationId(safePayload.get("stationId"));
        int score = readScore(safePayload.get("score"), 0, "综合评分必须在 1 到 5 之间");
        int serviceScore = readScore(safePayload.get("serviceScore"), score, "服务评分必须在 1 到 5 之间");
        int environmentScore = readScore(safePayload.get("environmentScore"), score, "环境评分必须在 1 到 5 之间");
        int facilityScore = readScore(safePayload.get("facilityScore"), score, "设施评分必须在 1 到 5 之间");
        String content = text(safePayload.get("content"));
        // 驿站必须存在
        if (stationMapper.selectById(stationId) == null) {
            throw new BusinessException("驿站不存在");
        }

        StationEvaluation entity = new StationEvaluation();
        entity.setStationId(stationId);
        entity.setUserId(userId);
        entity.setScore(score);
        entity.setServiceScore(serviceScore);
        entity.setEnvironmentScore(environmentScore);
        entity.setFacilityScore(facilityScore);
        entity.setContent(content);
        evaluationMapper.insert(entity);
        // create_time 由数据库默认值补齐，插入后回读一次以获得准确的创建时间
        StationEvaluation saved = entity;
        if (entity.getId() != null) {
            StationEvaluation reload = evaluationMapper.selectById(entity.getId());
            if (reload != null) {
                saved = reload;
            }
        }
        if (saved.getCreateTime() == null) {
            saved.setCreateTime(LocalDateTime.now());
        }

        Map<String, Object> data = toMap(saved);
        data.put("status", "SAVED");
        data.put("dataStatus", "系统计算结果");
        return data;
    }

    @Override
    public PageResult<Map<String, Object>> page(long current, long size, Long stationId, Long userId) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<StationEvaluation> query = new LambdaQueryWrapper<StationEvaluation>()
                .eq(stationId != null, StationEvaluation::getStationId, stationId)
                .eq(userId != null, StationEvaluation::getUserId, userId)
                .orderByDesc(StationEvaluation::getCreateTime)
                .orderByDesc(StationEvaluation::getId);
        Page<StationEvaluation> result = evaluationMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        Map<Long, String> stationNames = stationNames(result.getRecords());
        Map<Long, String> userNames = userNames(result.getRecords());
        List<Map<String, Object>> records = new ArrayList<>();
        for (StationEvaluation item : result.getRecords()) {
            records.add(toMap(item, stationNames, userNames));
        }
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public Map<String, Object> summary(Long stationId) {
        List<StationEvaluation> list = evaluationMapper.selectList(new LambdaQueryWrapper<StationEvaluation>()
                .eq(stationId != null, StationEvaluation::getStationId, stationId));
        List<Integer> totalScores = new ArrayList<>();
        List<Integer> serviceScores = new ArrayList<>();
        List<Integer> environmentScores = new ArrayList<>();
        List<Integer> facilityScores = new ArrayList<>();
        for (StationEvaluation item : list) {
            totalScores.add(item.getScore());
            serviceScores.add(item.getServiceScore());
            environmentScores.add(item.getEnvironmentScore());
            facilityScores.add(item.getFacilityScore());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stationId", stationId);
        summary.put("count", list.size());
        summary.put("averageScore", average(totalScores));
        summary.put("serviceAverage", average(serviceScores));
        summary.put("environmentAverage", average(environmentScores));
        summary.put("facilityAverage", average(facilityScores));
        return summary;
    }

    @Override
    public List<Map<String, Object>> myEvaluations(long userId) {
        List<StationEvaluation> list = evaluationMapper.selectList(new LambdaQueryWrapper<StationEvaluation>()
                .eq(StationEvaluation::getUserId, userId)
                .orderByDesc(StationEvaluation::getCreateTime)
                .orderByDesc(StationEvaluation::getId));
        Map<Long, String> stationNames = stationNames(list);
        Map<Long, String> userNames = userNames(list);
        List<Map<String, Object>> records = new ArrayList<>();
        for (StationEvaluation item : list) {
            records.add(toMap(item, stationNames, userNames));
        }
        return records;
    }

    /** 组装固定字段的评价 Map */
    private Map<String, Object> toMap(StationEvaluation item) {
        return toMap(item, stationNames(List.of(item)), userNames(List.of(item)));
    }

    /** 组装固定字段的评价 Map，复用已批量查询到的名称缓存 */
    private Map<String, Object> toMap(StationEvaluation item,
                                      Map<Long, String> stationNames,
                                      Map<Long, String> userNames) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.getId());
        data.put("stationId", item.getStationId());
        data.put("stationName", stationNames.getOrDefault(item.getStationId(), ""));
        data.put("userId", item.getUserId());
        data.put("userName", userNames.getOrDefault(item.getUserId(), ""));
        data.put("score", item.getScore());
        data.put("serviceScore", item.getServiceScore());
        data.put("environmentScore", item.getEnvironmentScore());
        data.put("facilityScore", item.getFacilityScore());
        data.put("content", item.getContent() == null ? "" : item.getContent());
        data.put("createTime", item.getCreateTime() == null ? "" : item.getCreateTime().format(TIME_FORMATTER));
        return data;
    }

    /** 批量查询驿站名称，查询不到时为空串 */
    private Map<Long, String> stationNames(List<StationEvaluation> list) {
        Map<Long, String> names = new HashMap<>();
        List<Long> ids = list.stream()
                .map(StationEvaluation::getStationId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return names;
        }
        for (Station station : stationMapper.selectBatchIds(ids)) {
            if (station != null && station.getStationName() != null) {
                names.put(station.getId(), station.getStationName());
            }
        }
        return names;
    }

    /** 批量查询用户姓名：realName 为空时使用 username，查询不到时为空串 */
    private Map<Long, String> userNames(List<StationEvaluation> list) {
        Map<Long, String> names = new HashMap<>();
        List<Long> ids = list.stream()
                .map(StationEvaluation::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return names;
        }
        for (SysUser user : sysUserMapper.selectBatchIds(ids)) {
            if (user == null) {
                continue;
            }
            String name = user.getRealName();
            if (name == null || name.isBlank()) {
                name = user.getUsername();
            }
            names.put(user.getId(), name == null ? "" : name);
        }
        return names;
    }

    private long requireStationId(Object value) {
        long stationId = toLong(value);
        if (stationId <= 0) {
            throw new BusinessException("评价驿站不能为空");
        }
        return stationId;
    }

    private int readScore(Object value, int fallback, String message) {
        Object raw = value == null ? fallback : value;
        int score = (int) toLong(raw);
        if (score < 1 || score > 5) {
            throw new BusinessException(message);
        }
        return score;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return "null".equals(text) ? "" : text;
    }

    /** 平均值保留一位小数，无数据时为 0.0 */
    private double average(List<Integer> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        int total = 0;
        for (Integer value : values) {
            total += value == null ? 0 : value;
        }
        return Math.round((double) total / values.size() * 10) / 10.0;
    }
}
