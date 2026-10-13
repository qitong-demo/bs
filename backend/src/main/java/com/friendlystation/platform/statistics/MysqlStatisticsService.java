package com.friendlystation.platform.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.friendlystation.platform.persistence.entity.Activity;
import com.friendlystation.platform.persistence.entity.Appeal;
import com.friendlystation.platform.persistence.entity.ServiceAppointment;
import com.friendlystation.platform.persistence.entity.Station;
import com.friendlystation.platform.persistence.entity.StationEvaluation;
import com.friendlystation.platform.persistence.entity.StationServiceItem;
import com.friendlystation.platform.persistence.mapper.ActivityMapper;
import com.friendlystation.platform.persistence.mapper.ActivitySignupMapper;
import com.friendlystation.platform.persistence.mapper.AppealMapper;
import com.friendlystation.platform.persistence.mapper.AppealRecordMapper;
import com.friendlystation.platform.persistence.mapper.ServiceAppointmentMapper;
import com.friendlystation.platform.persistence.mapper.StationEvaluationMapper;
import com.friendlystation.platform.persistence.mapper.StationMapper;
import com.friendlystation.platform.persistence.mapper.StationServiceItemMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** mysql profile 的统计聚合：全部指标由数据库实时计算，不使用固定演示值。 */
@Service
@Profile("mysql")
public class MysqlStatisticsService implements StatisticsService {
    private static final String DATA_SCOPE = "本系统数据库当前记录";

    private final StationMapper stationMapper;
    private final ServiceAppointmentMapper appointmentMapper;
    private final AppealMapper appealMapper;
    private final AppealRecordMapper appealRecordMapper;
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final StationEvaluationMapper evaluationMapper;
    private final StationServiceItemMapper serviceItemMapper;

    public MysqlStatisticsService(StationMapper stationMapper,
                                  ServiceAppointmentMapper appointmentMapper,
                                  AppealMapper appealMapper,
                                  AppealRecordMapper appealRecordMapper,
                                  ActivityMapper activityMapper,
                                  ActivitySignupMapper signupMapper,
                                  StationEvaluationMapper evaluationMapper,
                                  StationServiceItemMapper serviceItemMapper) {
        this.stationMapper = stationMapper;
        this.appointmentMapper = appointmentMapper;
        this.appealMapper = appealMapper;
        this.appealRecordMapper = appealRecordMapper;
        this.activityMapper = activityMapper;
        this.signupMapper = signupMapper;
        this.evaluationMapper = evaluationMapper;
        this.serviceItemMapper = serviceItemMapper;
    }

    @Override
    public Map<String, Object> overview() {
        LocalDateTime monthStart = LocalDate.now().minusDays(30).atStartOfDay();

        long openStations = stationMapper.selectCount(new LambdaQueryWrapper<Station>().eq(Station::getStatus, "OPEN"));
        long closedStations = stationMapper.selectCount(new LambdaQueryWrapper<Station>().eq(Station::getStatus, "CLOSED"));
        long appointmentCount = appointmentMapper.selectCount(null);
        long monthlyAppointments = appointmentMapper.selectCount(new LambdaQueryWrapper<ServiceAppointment>()
                .ge(ServiceAppointment::getCreateTime, monthStart));
        long pendingAppointments = appointmentMapper.selectCount(new LambdaQueryWrapper<ServiceAppointment>()
                .eq(ServiceAppointment::getStatus, "PENDING_STATION_CONFIRMATION"));
        long appealCount = appealMapper.selectCount(null);
        long monthlyAppeals = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .ge(Appeal::getCreateTime, monthStart));
        long resolvedAppeals = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getStatus, "RESOLVED"));
        long pendingAppeals = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .ne(Appeal::getStatus, "RESOLVED").ne(Appeal::getStatus, "CLOSED"));
        long activityCount = activityMapper.selectCount(null);
        long publishedActivityCount = activityMapper.selectCount(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, "PUBLISHED"));
        long signupCount = signupMapper.selectCount(null);
        long evaluationCount = evaluationMapper.selectCount(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stationCount", openStations + closedStations);
        out.put("openStationCount", openStations);
        out.put("closedStationCount", closedStations);
        out.put("appointmentCount", appointmentCount);
        out.put("monthlyAppointmentCount", monthlyAppointments);
        out.put("pendingAppointmentCount", pendingAppointments);
        out.put("appealCount", appealCount);
        out.put("monthlyAppealCount", monthlyAppeals);
        out.put("pendingAppealCount", pendingAppeals);
        out.put("resolvedAppealCount", resolvedAppeals);
        out.put("finishRate", StatisticsSupport.finishRate(resolvedAppeals, appealCount));
        out.put("appealRecordCount", appealRecordMapper.selectCount(null));
        out.put("activityCount", activityCount);
        out.put("publishedActivityCount", publishedActivityCount);
        out.put("signupCount", signupCount);
        out.put("evaluationCount", evaluationCount);
        out.put("averageScore", averageScore());
        out.put("dataScope", DATA_SCOPE);
        out.put("dataStatus", "系统计算结果");
        out.put("generatedAt", StatisticsSupport.format(LocalDateTime.now()));
        return out;
    }

    @Override
    public Map<String, Object> monthlyTrend() {
        List<YearMonth> months = StatisticsSupport.recentMonths(6);
        List<String> labels = new ArrayList<>();
        List<Long> appointments = new ArrayList<>();
        List<Long> appeals = new ArrayList<>();
        List<Long> signups = new ArrayList<>();
        List<Double> finishRates = new ArrayList<>();

        for (YearMonth month : months) {
            LocalDateTime from = month.atDay(1).atStartOfDay();
            LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
            labels.add(StatisticsSupport.monthLabel(month));

            long monthAppointments = appointmentMapper.selectCount(new LambdaQueryWrapper<ServiceAppointment>()
                    .ge(ServiceAppointment::getCreateTime, from)
                    .lt(ServiceAppointment::getCreateTime, to));
            long monthAppeals = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                    .ge(Appeal::getCreateTime, from)
                    .lt(Appeal::getCreateTime, to));
            long monthResolved = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                    .ge(Appeal::getCreateTime, from)
                    .lt(Appeal::getCreateTime, to)
                    .eq(Appeal::getStatus, "RESOLVED"));
            long monthSignups = signupMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.friendlystation.platform.persistence.entity.ActivitySignup>()
                    .ge("create_time", from)
                    .lt("create_time", to));

            appointments.add(monthAppointments);
            appeals.add(monthAppeals);
            signups.add(monthSignups);
            finishRates.add(StatisticsSupport.finishRate(monthResolved, monthAppeals));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", labels);
        out.put("appointments", appointments);
        out.put("appeals", appeals);
        out.put("signups", signups);
        out.put("finishRate", finishRates);
        out.put("dataScope", DATA_SCOPE);
        out.put("dataNote", "按各业务记录的创建时间统计自然月数据。");
        out.put("dataStatus", "系统计算结果");
        out.put("generatedAt", StatisticsSupport.format(LocalDateTime.now()));
        return out;
    }

    /**
     * 排行：按**驿站**统计联系申请数量。
     *
     * <p>语义变更说明：预约已从「选一个服务项目（饮水、充电…）」改为
     * 「就某件事提前联系驿站确认」，因此按服务项目排行不再成立
     * （用户填的是自由文本，每条都不同）。改为按驿站排行——
     * 「哪些驿站被联系得最多」在新语义下才是有意义的信息。</p>
     */
    @Override
    public List<Map<String, Object>> serviceRanking() {
        List<ServiceAppointment> appointments = appointmentMapper.selectList(
                new LambdaQueryWrapper<ServiceAppointment>().select(ServiceAppointment::getStationId));
        Map<Long, List<Long>> grouped = appointments.stream()
                .map(ServiceAppointment::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(id -> id));

        if (grouped.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = stationMapper.selectBatchIds(grouped.keySet()).stream()
                .collect(Collectors.toMap(Station::getId, Station::getStationName, (a, b) -> a));
        long max = grouped.values().stream().mapToLong(List::size).max().orElse(0L);

        return grouped.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("stationId", entry.getKey());
                    row.put("name", names.getOrDefault(entry.getKey(), "驿站 " + entry.getKey()));
                    row.put("count", (long) entry.getValue().size());
                    row.put("percent", max <= 0 ? 0 : Math.round(entry.getValue().size() * 100.0 / max));
                    return row;
                })
                .sorted(Comparator.comparingLong((Map<String, Object> row) -> (Long) row.get("count")).reversed())
                .limit(10)
                .toList();
    }

    private double averageScore() {
        List<StationEvaluation> evaluations = evaluationMapper.selectList(
                new LambdaQueryWrapper<StationEvaluation>().select(StationEvaluation::getScore));
        return StatisticsSupport.round1(evaluations.stream()
                .map(StationEvaluation::getScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
    }
}
