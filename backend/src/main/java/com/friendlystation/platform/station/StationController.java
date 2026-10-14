package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/station")
public class StationController {
    private final StationService stationService;
    private final com.friendlystation.platform.auth.DataScopeService dataScopeService;

    public StationController(StationService stationService,
                                com.friendlystation.platform.auth.DataScopeService dataScopeService) {
        this.stationService = stationService;
        this.dataScopeService = dataScopeService;
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
    public ApiResponse<StationDto> create(@Valid @RequestBody StationCreateRequest request) {
        return ApiResponse.ok(stationService.create(request));
    }

    /**
     * 编辑驿站基础信息。
     */
    @PutMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationDto> update(@PathVariable Long id, @Valid @RequestBody StationUpdateRequest request) {
        return ApiResponse.ok(stationService.update(id, request));
    }

    /**
     * 修改驿站状态，status 只接受 OPEN / CLOSED。
     */
    @PutMapping("/{id}/status")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationDto> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ApiResponse.ok(stationService.updateStatus(id, status));
    }

    /**
     * 逻辑删除驿站，把 status 置为 DELETED。
     */
    @DeleteMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        stationService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("deleted", true);
        result.put("mode", "LOGICAL");
        return ApiResponse.ok(result);
    }
}
