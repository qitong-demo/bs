package com.friendlystation.platform.activity;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.content.ActivityService;
import com.friendlystation.platform.workflow.ActivityOrganizer;
import com.friendlystation.platform.workflow.WorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 前台活动：活动浏览、报名与我的报名。活动的增删改由 {@link ActivityAdminController} 提供。 */
@RestController
@RequestMapping("/api/activity")
public class ActivityController {
    private final ActivityService activityService;
    private final WorkflowService workflowService;
    private final com.friendlystation.platform.auth.DataScopeService dataScopeService;

    public ActivityController(ActivityService activityService, WorkflowService workflowService,
                              com.friendlystation.platform.auth.DataScopeService dataScopeService) {
        this.activityService = activityService;
        this.workflowService = workflowService;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cityCode
    ) {
        return ApiResponse.ok(activityService.publicPage(current, size, stationId, keyword, cityCode));
    }

    @GetMapping("/my")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> mySignups() {
        return ApiResponse.ok(workflowService.mySignups(CurrentUserContext.require().id()));
    }

    /** 报名前先校验剩余名额，重复报名由工作流层按用户与活动校验。 */
    @PostMapping("/signup")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> signup(@RequestBody Map<String, Object> payload) {
        long activityId = FormatUtils.number(payload.get("activityId"));
        if (activityId <= 0) {
            throw new BusinessException("请选择要报名的活动");
        }
        // 先判断这个活动到底接不接受站内报名，再判断名额。
        // 第三方活动的 maxCount 是 0（本站不放号），若先查名额会返回「活动名额已满」，
        // 用户以为是自己手慢，实际该活动从来就不接受站内报名——原因说错了比不说更糟。
        String organizerType = "";
        try {
            Map<String, Object> activity = activityService.detail(activityId);
            if (activity != null && activity.get("organizerType") != null) {
                organizerType = String.valueOf(activity.get("organizerType"));
            }
        } catch (RuntimeException exception) {
            // 活动不存在等情况交给后续流程按既有语义报错
            organizerType = "";
        }
        if (!ActivityOrganizer.acceptsSignup(organizerType)) {
            throw new BusinessException("该活动由第三方机构举办，请前往活动来源页面报名");
        }
        if (activityService.remainingSlots(activityId) <= 0) {
            throw new BusinessException("活动名额已满");
        }
        return ApiResponse.ok(workflowService.addSignup(payload, CurrentUserContext.require().id()));
    }

    /**
     * 承办方处理报名：联系、审核通过或审核未通过，并给出反馈。
     *
     * <p>报名人填写的姓名与电话随报名记录一起展示给承办方供其联系核实；
     * 处理结果与反馈会回传给报名人。</p>
     */
    @PutMapping("/signup/review")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @com.friendlystation.platform.auth.RequirePermission({"SIGNUP_MANAGE"})
    public ApiResponse<Map<String, Object>> reviewSignup(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(workflowService.reviewSignup(
                payload, CurrentUserContext.require().id(),
                dataScopeService.stationIds(CurrentUserContext.require())));
    }

    @GetMapping("/admin/signups")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @com.friendlystation.platform.auth.RequirePermission({"SIGNUP_MANAGE"})
    public ApiResponse<List<Map<String, Object>>> signups() {
        return ApiResponse.ok(workflowService.allSignups(
                dataScopeService.stationIds(CurrentUserContext.require())));
    }

    /** 待处理报名数：平台端标签角标 / 工作台待办。 */
    @GetMapping("/admin/signups/pending-count")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @com.friendlystation.platform.auth.RequirePermission({"SIGNUP_MANAGE"})
    public ApiResponse<Map<String, Object>> pendingSignupCount() {
        long count = workflowService.pendingSignupCount(
                dataScopeService.stationIds(CurrentUserContext.require()));
        return ApiResponse.ok(Map.of("count", count));
    }

    /** 报名人取消自己的报名：审核通过/未通过之后不可再取消。 */
    @PutMapping("/signup/{id}/cancel")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> cancelSignup(@PathVariable Long id) {
        return ApiResponse.ok(workflowService.cancelSignup(id, CurrentUserContext.require().id()));
    }
}
