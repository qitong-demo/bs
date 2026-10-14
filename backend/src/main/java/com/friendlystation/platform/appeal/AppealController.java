package com.friendlystation.platform.appeal;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.DataScopeService;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.PageResult;
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

@RestController
@RequestMapping("/api/appeal")
public class AppealController {
    private final WorkflowService workflowService;
    private final DataScopeService dataScopeService;

    public AppealController(WorkflowService workflowService, DataScopeService dataScopeService) {
        this.workflowService = workflowService;
        this.dataScopeService = dataScopeService;
    }

    @PostMapping
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(workflowService.addAppeal(payload, CurrentUserContext.require().id()));
    }

    @GetMapping("/my")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> myAppeals() {
        return ApiResponse.ok(workflowService.appeals(CurrentUserContext.require().id()));
    }

    @GetMapping("/admin/page")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<PageResult<Map<String, Object>>> adminPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(workflowService.appealPage(current, size, status, keyword,
                dataScopeService.areas(CurrentUserContext.require())));
    }

    @GetMapping("/{id}")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(workflowService.appealDetail(id));
    }

    @GetMapping("/{id}/records")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> records(@PathVariable Long id) {
        return ApiResponse.ok(workflowService.appealRecords(id));
    }

    @PutMapping("/assign")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> assign(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(workflowService.assignAppeal(payload, CurrentUserContext.require().id()));
    }

    @PutMapping("/handle")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> handle(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(workflowService.handleAppeal(payload, CurrentUserContext.require().id()));
    }

    @PostMapping("/evaluate")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> evaluate(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(workflowService.evaluateAppeal(payload, CurrentUserContext.require().id()));
    }
}
