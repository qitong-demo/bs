package com.friendlystation.platform.evaluation;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 驿站服务评价接口。
 * POST /station 提交评价，GET /station/page 分页查询，GET /station/summary 统计，GET /my 我的评价。
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final StationEvaluationService evaluationService;

    public EvaluationController(StationEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 提交驿站评价，返回结果同时包含评价明细与 status、dataStatus，供前端直接使用 */
    @PostMapping("/station")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> createStationEvaluation(@RequestBody Map<String, Object> payload) {
        long userId = CurrentUserContext.require().id();
        return ApiResponse.ok(evaluationService.create(payload, userId));
    }

    /** 分页查询评价列表，stationId、userId 为可选过滤条件，按创建时间倒序 */
    @GetMapping("/station/page")
    public ApiResponse<PageResult<Map<String, Object>>> pageStationEvaluations(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Long userId) {
        return ApiResponse.ok(evaluationService.page(current, size, stationId, userId));
    }

    /** 评价统计，stationId 为空时统计全部驿站 */
    @GetMapping("/station/summary")
    public ApiResponse<Map<String, Object>> stationEvaluationSummary(
            @RequestParam(required = false) Long stationId) {
        return ApiResponse.ok(evaluationService.summary(stationId));
    }

    /** 当前登录用户的全部评价 */
    @GetMapping("/my")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> myEvaluations() {
        long userId = CurrentUserContext.require().id();
        return ApiResponse.ok(evaluationService.myEvaluations(userId));
    }
}
