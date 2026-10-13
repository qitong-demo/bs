package com.friendlystation.platform.news;

import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.content.NoticeService;
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

/** 公告接口：前台只读已发布公告，后台可管理全部状态。 */
@RestController
@RequestMapping("/api/notice")
public class NoticeController {
    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 前台公告分页，仅返回已发布公告。 */
    @GetMapping("/page")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(noticeService.publicPage(current, size, noticeType, keyword));
    }

    /** 后台公告分页，返回全部状态。 */
    @GetMapping("/admin/page")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<PageResult<Map<String, Object>>> adminPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(noticeService.adminPage(current, size, noticeType, keyword, status));
    }

    /** 公告详情。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(noticeService.detail(id));
    }

    /** 新增公告。 */
    @PostMapping
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(noticeService.create(payload));
    }

    /** 修改公告。 */
    @PutMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(noticeService.update(id, payload));
    }

    /** 删除公告。 */
    @DeleteMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }
}
