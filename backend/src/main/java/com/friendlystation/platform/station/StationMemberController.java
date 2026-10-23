package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CurrentUserContext;
import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** 驿站负责人管理本站工作人员/志愿者账号。 */
@RestController
@RequestMapping("/api/station/member")
@RequireRole({"STATION_ADMIN"})
public class StationMemberController {
    private final StationMemberService memberService;

    public StationMemberController(StationMemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam Long stationId) {
        return ApiResponse.ok(memberService.list(stationId, CurrentUserContext.require().id()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> add(@RequestBody Map<String, Object> payload) {
        long stationId = com.friendlystation.platform.common.FormatUtils.number(payload.get("stationId"));
        return ApiResponse.ok(memberService.add(stationId, payload, CurrentUserContext.require().id()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> payload) {
        String status = payload == null ? "ACTIVE" : String.valueOf(payload.getOrDefault("status", "ACTIVE"));
        return ApiResponse.ok(memberService.updateStatus(id, status, CurrentUserContext.require().id()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> remove(@PathVariable Long id) {
        memberService.remove(id, CurrentUserContext.require().id());
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }
}
