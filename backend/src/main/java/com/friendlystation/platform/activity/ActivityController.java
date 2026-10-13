package com.friendlystation.platform.activity;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.content.ActivityService;
import com.friendlystation.platform.workflow.WorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ActivityController(ActivityService activityService, WorkflowService workflowService) {
        this.activityService = activityService;
        this.workflowService = workflowService;
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(activityService.publicPage(current, size, stationId, keyword));
    }

    @GetMapping("/my")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> mySignups() {
        return ApiResponse.ok(workflowService.mySignups(CurrentUserContext.require().id()));
    }

    /** 报名前先校验剩余名额，重复报名由工作流层按用户与活动校验。 */
    @PostMapping("/signup")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> signup(@RequestBody Map<String, Object> payload) {
        long activityId = FormatUtils.number(payload.get("activityId"));
        if (activityId <= 0) {
            throw new BusinessException("请选择要报名的活动");
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
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> reviewSignup(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(workflowService.reviewSignup(payload, CurrentUserContext.require().id()));
    }

    @GetMapping("/admin/signups")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> signups() {
        return ApiResponse.ok(workflowService.allSignups());
    }
}
