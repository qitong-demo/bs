package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequirePermission;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
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

/**
 * 驿站信息变更申请与审批。
 *
 * <p>驿站侧提交与查看自己的申请；管理员侧在「变更审批」里处理，需要 CHANGE_APPROVE 功能权限。</p>
 */
@RestController
@RequestMapping("/api/station/change")
public class StationChangeController {

    private final StationChangeService stationChangeService;

    public StationChangeController(StationChangeService stationChangeService) {
        this.stationChangeService = stationChangeService;
    }

    /** 提交变更申请。位置走审批；营业时间、电话、地址也可走审批但不能直接改时由驿站侧另行验证。 */
    @PostMapping
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> submit(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(stationChangeService.submit(CurrentUserContext.require(), payload));
    }

    /** 某驿站的申请记录，含审批结果与驳回原因。 */
    @GetMapping("/list")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam long stationId) {
        return ApiResponse.ok(stationChangeService.listByStation(CurrentUserContext.require(), stationId));
    }

    /** 当前账号负责驿站的申请记录（驿站端「我的申请」）。 */
    @GetMapping("/mine")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(stationChangeService.mine(CurrentUserContext.require()));
    }

    /** 待审批列表。需要 CHANGE_APPROVE 权限——子账号没被授予时会 403。 */
    @GetMapping("/pending")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @RequirePermission({"CHANGE_APPROVE"})
    public ApiResponse<List<Map<String, Object>>> pending() {
        return ApiResponse.ok(stationChangeService.pendingList(CurrentUserContext.require()));
    }

    /** 审批通过：写入驿站字段并留痕。 */
    @PutMapping("/{id}/approve")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @RequirePermission({"CHANGE_APPROVE"})
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        return ApiResponse.ok(stationChangeService.approve(
                CurrentUserContext.require(), id, text(data.get("reviewNote"))));
    }

    /** 审批驳回：必须写明原因。 */
    @PutMapping("/{id}/reject")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @RequirePermission({"CHANGE_APPROVE"})
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        return ApiResponse.ok(stationChangeService.reject(
                CurrentUserContext.require(), id, text(data.get("reviewNote"))));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 撤回自己提交、仍待审批的申请。 */
    @PutMapping("/{id}/cancel")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return ApiResponse.ok(stationChangeService.cancel(CurrentUserContext.require(), id));
    }
}
