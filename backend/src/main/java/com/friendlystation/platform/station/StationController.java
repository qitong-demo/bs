package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/station")
public class StationController {
    private final StationService stationService;
    private final com.friendlystation.platform.auth.DataScopeService dataScopeService;
    private final com.friendlystation.platform.workflow.WorkflowService workflowService;

    public StationController(StationService stationService,
                                com.friendlystation.platform.auth.DataScopeService dataScopeService,
                                com.friendlystation.platform.workflow.WorkflowService workflowService) {
        this.stationService = stationService;
        this.dataScopeService = dataScopeService;
        this.workflowService = workflowService;
    }

    /**
     * 公开分页查询：返回未逻辑删除的驿站，可按城市、区域、关键词、服务项目与状态过滤。
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<StationDto>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(stationService.page(current, size, cityCode, area, keyword, service, status));
    }

    @GetMapping("/nearby")
    public ApiResponse<PageResult<StationDto>> nearby(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(stationService.page(current, size, cityCode, area, keyword, service, status));
    }

    /**
     * 区域分布统计，供前台筛选下拉使用；传入 cityCode 时只统计该城市的区县。
     */
    @GetMapping("/areas")
    public ApiResponse<List<Map<String, Object>>> areas(@RequestParam(required = false) String cityCode) {
        return ApiResponse.ok(stationService.areaSummary(cityCode));
    }

    /**
     * 管理端分页查询，返回全部状态（含已逻辑删除），可按城市与 status 过滤。
     */
    @GetMapping("/admin/page")
    public ApiResponse<PageResult<StationDto>> adminPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(stationService.adminPage(current, size, cityCode, area, keyword, status));
    }

    /**
     * 我的驿站：当前账号负责的驿站。
     *
     * <p>驿站端此前**看不到自己的驿站信息**——只能通过公开的驿站查询按名字找，
     * 既不知道自己负责哪几家，也看不到营业时间、联系电话这些需要维护的字段。</p>
     *
     * <p>平台级账号按定义不负责具体驿站，返回空列表；前端应据此隐藏该入口。</p>
     */
    @GetMapping("/mine")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<List<StationDto>> mine() {
        return ApiResponse.ok(dataScopeService.managedStations(CurrentUserContext.require()));
    }

    @GetMapping("/{id}")
    public ApiResponse<StationDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(stationService.detail(id));
    }

    @PostMapping
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @com.friendlystation.platform.auth.RequirePermission({"STATION_MANAGE"})
    public ApiResponse<StationDto> create(@Valid @RequestBody StationCreateRequest request) {
        return ApiResponse.ok(stationService.create(request));
    }

    /**
     * 编辑驿站基础信息。
     */
    @PutMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationDto> update(@PathVariable Long id, @Valid @RequestBody StationUpdateRequest request) {
        requireStationScope(id);
        return ApiResponse.ok(stationService.update(id, request));
    }

    /**
     * 修改驿站状态，status 只接受 OPEN / CLOSED。
     */
    @PutMapping("/{id}/status")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationDto> updateStatus(@PathVariable Long id, @RequestParam String status) {
        requireStationScope(id);
        return ApiResponse.ok(stationService.updateStatus(id, status));
    }

    /**
     * 逻辑删除驿站，把 status 置为 DELETED。
     */
    @DeleteMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @com.friendlystation.platform.auth.RequirePermission({"STATION_MANAGE"})
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        stationService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("deleted", true);
        result.put("mode", "LOGICAL");
        return ApiResponse.ok(result);
    }

    /**
     * 设置暂停服务窗口：期间的新预约会被拒绝。
     * 请求体：{"from":"2026-11-01 08:00:00","to":"2026-11-03 18:00:00","reason":"装修"}
     */
    @PutMapping("/{id}/suspend")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationDto> suspend(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        requireStationScope(id);
        Map<String, Object> data = payload == null ? Map.of() : payload;
        LocalDateTime from = FormatUtils.parseDateTimeOrNull(data.get("from"));
        LocalDateTime to = FormatUtils.parseDateTimeOrNull(data.get("to"));
        if (from == null || to == null) {
            throw new BusinessException("请填写暂停开始和结束时间，格式为 yyyy-MM-dd HH:mm:ss");
        }
        String reason = FormatUtils.text(data.get("reason"), "");
        if (reason.isBlank()) {
            throw new BusinessException("请填写暂停原因，用户需要知道为什么约不上");
        }
        StationDto suspended = stationService.suspend(id, from, to, reason);
        try {
            // 通知是“尽力而为”：暂停已经生效，不能因为消息写入失败把整个操作回滚。
            workflowService.notifySuspension(id, from, to, reason);
        } catch (RuntimeException ignored) {
            // 预约人仍可在“我的预约”看到状态，下次进入时由管理员另行告知。
        }
        return ApiResponse.ok(suspended);
    }

    /** 取消暂停服务，恢复接收新预约。 */
    @DeleteMapping("/{id}/suspend")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationDto> resume(@PathVariable Long id) {
        requireStationScope(id);
        return ApiResponse.ok(stationService.resume(id));
    }

    /** 驿站管理员只能改自己负责的驿站；平台级不受限。 */
    private void requireStationScope(Long stationId) {
        var user = CurrentUserContext.require();
        if (dataScopeService.isPlatformLevel(user)) {
            return;
        }
        var scoped = dataScopeService.stationIds(user);
        if (scoped == null || !scoped.contains(stationId)) {
            throw new com.friendlystation.platform.common.ForbiddenException("无权修改该驿站");
        }
    }
}
