package com.friendlystation.platform.statistics;

import com.friendlystation.platform.station.StationDto;
import com.friendlystation.platform.station.StationSource;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.evaluation.StationEvaluationService;
import com.friendlystation.platform.station.StationService;
import com.friendlystation.platform.workflow.WorkflowService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统计报表导出。数据来源统一走各业务服务，因此内存 profile 与 mysql profile 使用同一套报表口径，
 * 只有在 mysql profile 下才会导出数据库中的真实记录。
 */
@Component
public class CsvExportBuilder {
    private static final int EXPORT_LIMIT = 5000;

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

    public CsvExport build(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase();
        return switch (normalized) {
            case "appeal", "appeals" -> new CsvExport("appeals.csv", appealsCsv());
            case "appointment", "appointments" -> new CsvExport("appointments.csv", appointmentsCsv());
            case "station", "stations" -> new CsvExport("stations.csv", stationsCsv());
            case "signup", "signups" -> new CsvExport("activity-signups.csv", signupsCsv());
            case "evaluation", "evaluations" -> new CsvExport("station-evaluations.csv", evaluationsCsv());
            default -> throw new BusinessException("不支持的导出类型：" + (type == null ? "" : type)
                    + "，可选值为 appeals、appointments、stations、signups、evaluations");
        };
    }

    private String appealsCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : workflowService.appealPage(1, EXPORT_LIMIT, null, null, null).records()) {
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

    private String appointmentsCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : workflowService.appointmentPage(1, EXPORT_LIMIT, null, null, null).records()) {
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

    private String stationsCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (StationDto item : stationService.adminPage(1, EXPORT_LIMIT, null, null, null, null).records()) {
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

    private String signupsCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : workflowService.allSignups()) {
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

    private String evaluationsCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : evaluationService.page(1, EXPORT_LIMIT, null, null).records()) {
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

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
