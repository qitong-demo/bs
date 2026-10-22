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

    public ActivityAdminController(ActivityService activityService,
                                   com.friendlystation.platform.content.ActivitySourceFetcher sourceFetcher,
                                   com.friendlystation.platform.auth.DataScopeService dataScopeService) {
        this.activityService = activityService;
        this.sourceFetcher = sourceFetcher;
        this.dataScopeService = dataScopeService;
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

    /** 新增活动。 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        requireStationInScope(payload == null ? null : payload.get("stationId"));
        return ApiResponse.ok(activityService.create(payload));
    }

    /** 修改活动。 */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        requireActivityScope(id);
        if (payload != null && payload.containsKey("stationId")) {
            requireStationInScope(payload.get("stationId"));
        }
        return ApiResponse.ok(activityService.update(id, payload));
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
