package com.friendlystation.platform.activity;

import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.content.ActivityService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 活动后台管理接口。
 * 只负责管理端的分页、新增、修改与删除；前台的 /api/activity/page、/api/activity/signup、
 * /api/activity/admin/signups、/api/activity/my 由 ActivityController 提供。
 */
@RestController
@RequestMapping("/api/activity/admin")
@RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
@com.friendlystation.platform.auth.RequirePermission({"ACTIVITY_MANAGE"})
public class ActivityAdminController {
    private final ActivityService activityService;
    private final com.friendlystation.platform.content.ActivitySourceFetcher sourceFetcher;
    private final com.friendlystation.platform.auth.DataScopeService dataScopeService;
    private final com.friendlystation.platform.station.StationService stationService;
    private final com.friendlystation.platform.notification.NotificationService notificationService;
    private final com.friendlystation.platform.admin.AdminUserService adminUserService;

    public ActivityAdminController(ActivityService activityService,
                                   com.friendlystation.platform.content.ActivitySourceFetcher sourceFetcher,
                                   com.friendlystation.platform.auth.DataScopeService dataScopeService,
                                   com.friendlystation.platform.station.StationService stationService,
                                   com.friendlystation.platform.notification.NotificationService notificationService,
                                   com.friendlystation.platform.admin.AdminUserService adminUserService) {
        this.activityService = activityService;
        this.sourceFetcher = sourceFetcher;
        this.dataScopeService = dataScopeService;
        this.stationService = stationService;
        this.notificationService = notificationService;
        this.adminUserService = adminUserService;
    }

    /** 后台活动分页，返回全部状态。 */
    @GetMapping("/page")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(activityService.adminPage(current, size, status, keyword));
    }

    @GetMapping("/pending-review-count")
    @RequireRole({"PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> pendingReviewCount() {
        return ApiResponse.ok(Map.of("count", activityService.pendingReviewCount()));
    }

    /** 新增活动：驿站提交先进入待审核，平台管理员可直接发布。 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        Map<String, Object> data = forceStationReview(payload);
        requireStationInScope(data.get("stationId"));
        Map<String, Object> result = activityService.create(data);
        notifyPendingReview(result);
        return ApiResponse.ok(result);
    }

    /** 修改活动：驿站修改后重新进入待审核，平台管理员可直接改状态。 */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        requireActivityScope(id);
        Map<String, Object> data = forceStationReview(payload);
        if (data.containsKey("stationId")) {
            requireStationInScope(data.get("stationId"));
        }
        Map<String, Object> result = activityService.update(id, data);
        notifyPendingReview(result);
        return ApiResponse.ok(result);
    }

    /** 平台审核驿站活动：通过后发布，驳回必须写原因。 */
    @PutMapping("/{id}/review")
    @RequireRole({"PLATFORM_ADMIN"})
    @com.friendlystation.platform.auth.RequirePermission({"ACTIVITY_MANAGE"})
    public ApiResponse<Map<String, Object>> review(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        String action = String.valueOf(data.getOrDefault("action", "approve"));
        String note = String.valueOf(data.getOrDefault("reviewNote", ""));
        Map<String, Object> result = activityService.review(id, action, note,
                com.friendlystation.platform.auth.CurrentUserContext.require().id());
        Object stationId = result.get("stationId");
        if (com.friendlystation.platform.common.FormatUtils.number(stationId) > 0) {
            try {
                var station = stationService.detail(com.friendlystation.platform.common.FormatUtils.number(stationId));
                if (station != null && station.managerId() != null) {
                    String title = "approve".equalsIgnoreCase(action) ? "活动审核通过" : "活动审核未通过";
                    notificationService.push(station.managerId(), "ACTIVITY", title,
                            String.valueOf(result.get("title")) + (note.isBlank() ? "" : "；" + note),
                            "/admin?tab=activities&activityId=" + id, "ACTIVITY", id);
                }
            } catch (RuntimeException ignored) {
                // 驿站已删除：审核结果仍写入活动记录
            }
        }
        return ApiResponse.ok(result);
    }

    /** 驿站提交的活动：给每个平台管理员写一条待审核通知。 */
    private void notifyPendingReview(Map<String, Object> activity) {
        if (!"PENDING_REVIEW".equals(String.valueOf(activity.get("status")))) {
            return;
        }
        long id = com.friendlystation.platform.common.FormatUtils.number(activity.get("id"));
        var page = adminUserService.userPage(1, 100, "PLATFORM_ADMIN", "");
        for (Map<String, Object> user : page.records()) {
            long userId = com.friendlystation.platform.common.FormatUtils.number(user.get("id"));
            if (userId <= 0) continue;
            notificationService.push(userId, "ACTIVITY", "新的公益活动待审核",
                    String.valueOf(activity.get("title")) + " · " + String.valueOf(activity.get("stationName")),
                    "/admin?tab=activities&activityId=" + id, "ACTIVITY", id);
        }
    }

    /** 驿站/工作人员提交的活动统一进待审核；平台管理员不受限制。 */
    private Map<String, Object> forceStationReview(Map<String, Object> payload) {
        Map<String, Object> data = payload == null
                ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(payload);
        var user = com.friendlystation.platform.auth.CurrentUserContext.require();
        if (!dataScopeService.isPlatformLevel(user)) {
            data.put("status", "PENDING_REVIEW");
            data.put("reviewNote", "");
        }
        return data;
    }

    /**
     * 半自动采集：抓取网页标题与摘要，供管理员核对后录入。
     *
     * <p>只做机械提取，不做内容加工。返回结果带 note 提示"需人工核对"——
     * 采集来的信息最终由人负责。主办方、活动日期、分类仍需管理员手工填写，
     * 程序无法从新闻页可靠地推出这些字段，硬猜会污染数据。</p>
     */
    @PostMapping("/fetch-source")
    public ApiResponse<Map<String, Object>> fetchSource(@RequestBody Map<String, Object> payload) {
        Object url = payload == null ? null : payload.get("url");
        return ApiResponse.ok(sourceFetcher.fetch(url == null ? "" : String.valueOf(url)));
    }

    /** 删除活动。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        requireActivityScope(id);
        activityService.delete(id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    /** 驿站管理员只能管理自己负责驿站的活动；平台级不受限。 */
    private void requireActivityScope(Long id) {
        var user = com.friendlystation.platform.auth.CurrentUserContext.require();
        if (dataScopeService.isPlatformLevel(user)) {
            return;
        }
        var detail = activityService.detail(id);
        long stationId = com.friendlystation.platform.common.FormatUtils.number(detail.get("stationId"));
        var scoped = dataScopeService.stationIds(user);
        if (scoped == null || !scoped.contains(stationId)) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权管理该活动");
        }
    }

    /** 新建/改挂活动时，活动所属驿站必须在当前账号范围内。 */
    private void requireStationInScope(Object stationIdRaw) {
        var user = com.friendlystation.platform.auth.CurrentUserContext.require();
        if (dataScopeService.isPlatformLevel(user)) {
            return;
        }
        long stationId = com.friendlystation.platform.common.FormatUtils.number(stationIdRaw);
        var scoped = dataScopeService.stationIds(user);
        if (stationId <= 0 || scoped == null || !scoped.contains(stationId)) {
            throw new com.friendlystation.platform.common.ForbiddenException("只能为自己负责的驿站创建活动");
        }
    }
}
