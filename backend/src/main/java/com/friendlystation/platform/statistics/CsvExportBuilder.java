package com.friendlystation.platform.statistics;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.evaluation.StationEvaluationService;
import com.friendlystation.platform.station.StationDto;
import com.friendlystation.platform.station.StationService;
import com.friendlystation.platform.station.StationSource;
import com.friendlystation.platform.workflow.WorkflowService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统计报表导出。数据来源统一走各业务服务，因此内存 profile 与 mysql profile 使用同一套报表口径。
 *
 * <p>导出必须遵守两件事：</p>
 * <ol>
 *   <li><b>数据范围</b>：驿站管理员只能导出自己负责的驿站/区域，不能借导出绕过列表的权限；</li>
 *   <li><b>不静默截断</b>：业务分页接口有单页上限（200/50），导出要循环取页，
 *       直到取完或达到导出上限，而不是只拿第一页。</li>
 * </ol>
 */
@Component
public class CsvExportBuilder {
    private static final int EXPORT_LIMIT = 5000;
    private static final int PAGE_SIZE = 200;

    private final WorkflowService workflowService;
    private final StationService stationService;
    private final StationEvaluationService evaluationService;

    public CsvExportBuilder(WorkflowService workflowService,
                            StationService stationService,
                            StationEvaluationService evaluationService) {
        this.workflowService = workflowService;
        this.stationService = stationService;
        this.evaluationService = evaluationService;
    }

    /** 平台级导出（不限制范围）。 */
    public CsvExport build(String type) {
        return build(type, null, null);
    }

    public CsvExport build(String type, List<Long> stationIds, List<String> areas) {
        String normalized = type == null ? "" : type.trim().toLowerCase();
        return switch (normalized) {
            case "appeal", "appeals" -> new CsvExport("appeals.csv", appealsCsv(stationIds, areas));
            case "appointment", "appointments" -> new CsvExport("appointments.csv", appointmentsCsv(stationIds));
            case "station", "stations" -> new CsvExport("stations.csv", stationsCsv(stationIds));
            case "signup", "signups" -> new CsvExport("activity-signups.csv", signupsCsv(stationIds));
            case "evaluation", "evaluations" -> new CsvExport("station-evaluations.csv", evaluationsCsv(stationIds));
            default -> throw new BusinessException("不支持的导出类型：" + (type == null ? "" : type)
                    + "，可选值为 appeals、appointments、stations、signups、evaluations");
        };
    }

    private String appealsCsv(List<Long> stationIds, List<String> areas) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : collectAppeals(stationIds, areas)) {
            rows.add(List.of(
                    text(item.get("id")),
                    text(item.get("title")),
                    text(item.get("type")),
                    text(item.get("area")),
                    text(item.get("userName")),
                    Boolean.TRUE.equals(item.get("anonymous")) ? "是" : "否",
                    text(item.get("statusLabel")),
                    text(item.get("receiverName")),
                    text(item.get("handlerName")),
                    text(item.get("createTime")),
                    text(item.get("finishTime"))
            ));
        }
        return CsvSupport.toCsv(List.of("编号", "诉求标题", "诉求类型", "所属区域", "提交人", "匿名",
                "当前状态", "受理方", "处理人", "提交时间", "办结时间"), rows);
    }

    private String appointmentsCsv(List<Long> stationIds) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : collectAppointments(stationIds)) {
            rows.add(List.of(
                    text(item.get("id")),
                    text(item.get("stationName")),
                    text(item.get("serviceName")),
                    text(item.get("contactPhone")),
                    text(item.get("appointDate")),
                    text(item.get("appointTime")),
                    text(item.get("statusLabel")),
                    text(item.get("userName")),
                    text(item.get("auditReason")),
                    text(item.get("createTime"))
            ));
        }
        return CsvSupport.toCsv(List.of("编号", "服务驿站", "想提前沟通的事项", "联系电话", "预约日期", "预约时段",
                "当前状态", "预约人", "审核说明", "提交时间"), rows);
    }

    private String stationsCsv(List<Long> stationIds) {
        List<List<String>> rows = new ArrayList<>();
        for (StationDto item : collectStations(stationIds)) {
            if ("DELETED".equals(item.status())) {
                continue;
            }
            StationSource source = item.source() == null ? StationSource.empty() : item.source();
            rows.add(List.of(
                    text(item.id()),
                    text(item.stationName()),
                    text(item.stationType()),
                    text(item.area()),
                    text(item.address()),
                    text(item.contactPhone()),
                    text(item.openTime()),
                    "OPEN".equals(item.status()) ? "营业中" : "已停用",
                    String.join(" / ", item.services() == null ? List.of() : item.services()),
                    text(source.sourceName()),
                    text(source.sourceUrl()),
                    text(source.sourcePublishedAt()),
                    text(source.verifiedAt()),
                    text(source.dataStatus()),
                    text(item.createTime())
            ));
        }
        return CsvSupport.toCsv(List.of("编号", "驿站名称", "驿站类型", "所属区域", "详细地址", "联系电话",
                "开放时间", "状态", "服务项目", "数据来源", "来源地址", "来源发布日期", "核对日期", "数据状态",
                "创建时间"), rows);
    }

    private String signupsCsv(List<Long> stationIds) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : workflowService.allSignups(stationIds)) {
            if (rows.size() >= EXPORT_LIMIT) {
                break;
            }
            rows.add(List.of(
                    text(item.get("id")),
                    text(item.get("activityTitle")),
                    text(item.get("userName")),
                    text(item.get("phone")),
                    text(item.get("statusLabel")),
                    text(item.get("createTime"))
            ));
        }
        return CsvSupport.toCsv(List.of("编号", "活动名称", "报名人", "联系电话", "报名状态", "报名时间"), rows);
    }

    private String evaluationsCsv(List<Long> stationIds) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : collectEvaluations(stationIds)) {
            rows.add(List.of(
                    text(item.get("id")),
                    text(item.get("stationName")),
                    text(item.get("userName")),
                    text(item.get("score")),
                    text(item.get("serviceScore")),
                    text(item.get("environmentScore")),
                    text(item.get("facilityScore")),
                    text(item.get("content")),
                    text(item.get("createTime"))
            ));
        }
        return CsvSupport.toCsv(List.of("编号", "评价驿站", "评价人", "综合评分", "服务评分",
                "环境评分", "设施评分", "评价内容", "评价时间"), rows);
    }

    /** 循环取页，避免业务接口的单页上限（200）把导出静默截断。 */
    private List<Map<String, Object>> collectAppointments(List<Long> stationIds) {
        List<Map<String, Object>> collected = new ArrayList<>();
        long current = 1;
        while (collected.size() < EXPORT_LIMIT) {
            PageResult<Map<String, Object>> page = workflowService.appointmentPage(
                    current, PAGE_SIZE, null, null, stationIds);
            collected.addAll(page.records());
            if (page.records().isEmpty() || current * PAGE_SIZE >= page.total()) {
                break;
            }
            current++;
        }
        return collected.size() > EXPORT_LIMIT ? collected.subList(0, EXPORT_LIMIT) : collected;
    }

    private List<Map<String, Object>> collectAppeals(List<Long> stationIds, List<String> areas) {
        List<Map<String, Object>> collected = new ArrayList<>();
        long current = 1;
        while (collected.size() < EXPORT_LIMIT) {
            PageResult<Map<String, Object>> page = workflowService.appealPage(
                    current, PAGE_SIZE, null, null, stationIds, areas);
            collected.addAll(page.records());
            if (page.records().isEmpty() || current * PAGE_SIZE >= page.total()) {
                break;
            }
            current++;
        }
        return collected.size() > EXPORT_LIMIT ? collected.subList(0, EXPORT_LIMIT) : collected;
    }

    private List<StationDto> collectStations(List<Long> stationIds) {
        List<StationDto> collected = new ArrayList<>();
        long current = 1;
        while (collected.size() < EXPORT_LIMIT) {
            PageResult<StationDto> page = stationService.adminPage(current, 50, null, null, null, null);
            for (StationDto item : page.records()) {
                if (stationIds != null && !stationIds.contains(item.id())) {
                    continue;
                }
                collected.add(item);
            }
            if (page.records().isEmpty() || current * 50L >= page.total()) {
                break;
            }
            current++;
        }
        return collected.size() > EXPORT_LIMIT ? collected.subList(0, EXPORT_LIMIT) : collected;
    }

    private List<Map<String, Object>> collectEvaluations(List<Long> stationIds) {
        List<Map<String, Object>> collected = new ArrayList<>();
        long current = 1;
        while (collected.size() < EXPORT_LIMIT) {
            PageResult<Map<String, Object>> page = evaluationService.page(current, 50, null, null);
            for (Map<String, Object> item : page.records()) {
                if (stationIds != null && !stationIds.contains(number(item.get("stationId")))) {
                    continue;
                }
                collected.add(item);
            }
            if (page.records().isEmpty() || current * 50L >= page.total()) {
                break;
            }
            current++;
        }
        return collected.size() > EXPORT_LIMIT ? collected.subList(0, EXPORT_LIMIT) : collected;
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
