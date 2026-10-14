package com.friendlystation.platform.station;

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

/**
 * 驿站服务项目管理接口。
 */
@RestController
@RequestMapping("/api/station/service")
public class StationServiceController {
    private final StationServiceItemService stationServiceItemService;

    public StationServiceController(StationServiceItemService stationServiceItemService) {
        this.stationServiceItemService = stationServiceItemService;
    }

    /**
     * 服务项目分页查询：stationId、keyword（服务名称）均可选。
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<StationServiceItemDto>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(stationServiceItemService.page(current, size, stationId, keyword));
    }

    /**
     * 查询某个驿站的全部服务项目。
     */
    @GetMapping("/list")
    public ApiResponse<List<StationServiceItemDto>> list(@RequestParam Long stationId) {
        return ApiResponse.ok(stationServiceItemService.listByStation(stationId));
    }

    @PostMapping
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationServiceItemDto> create(@Valid @RequestBody StationServiceItemRequest request) {
        return ApiResponse.ok(stationServiceItemService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationServiceItemDto> update(@PathVariable Long id,
                                                    @Valid @RequestBody StationServiceItemRequest request) {
        return ApiResponse.ok(stationServiceItemService.update(id, request));
    }

    /**
     * 启用（1）/ 停用（0）服务项目。
     */
    @PutMapping("/{id}/status")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<StationServiceItemDto> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return ApiResponse.ok(stationServiceItemService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        stationServiceItemService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("deleted", true);
        return ApiResponse.ok(result);
    }
}
