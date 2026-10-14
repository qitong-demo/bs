package com.friendlystation.platform.appointment;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.DataScopeService;
import com.friendlystation.platform.auth.RequirePermission;
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
@RequestMapping("/api/appointment")
public class AppointmentController {
    private final WorkflowService workflowService;
    private final DataScopeService dataScopeService;

    public AppointmentController(WorkflowService workflowService, DataScopeService dataScopeService) {
        this.workflowService = workflowService;
        this.dataScopeService = dataScopeService;
    }

    @PostMapping
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(workflowService.addAppointment(payload, CurrentUserContext.require().id()));
    }

    @GetMapping("/my")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<List<Map<String, Object>>> myAppointments() {
        return ApiResponse.ok(workflowService.appointments(CurrentUserContext.require().id()));
    }

    @PutMapping("/{id}/cancel")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return ApiResponse.ok(workflowService.cancelAppointment(id, CurrentUserContext.require().id()));
    }

    /**
     * 标记预约爽约：用户预约了但未按时到场。
     *
     * <p>只允许标记已确认的预约——待审核或被拒绝的预约本来就没成立，
     * 标记爽约没有意义，还会误伤用户。</p>
     */
    @PutMapping("/{id}/no-show")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    @RequirePermission({"APPOINTMENT_AUDIT"})
    public ApiResponse<Map<String, Object>> markNoShow(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        Object remark = data.get("remark");
        return ApiResponse.ok(workflowService.markNoShow(
                id, CurrentUserContext.require().id(), remark == null ? "" : String.valueOf(remark)));
    }

    @GetMapping("/admin/page")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<PageResult<Map<String, Object>>> adminPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(workflowService.appointmentPage(current, size, status, keyword,
                dataScopeService.stationIds(CurrentUserContext.require())));
    }

    @PutMapping("/audit")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> audit(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(workflowService.auditAppointment(payload, CurrentUserContext.require().id()));
    }
}
