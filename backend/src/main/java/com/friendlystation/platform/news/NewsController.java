package com.friendlystation.platform.news;

import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.content.NewsService;
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

/** 资讯接口：前台只读已发布资讯，后台可管理全部状态。 */
@RestController
@RequestMapping("/api/news")
public class NewsController {
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    /**
     * 前台资讯分页，仅返回已发布资讯。
     *
     * <p>分城市展示：传了 cityCode 时只返回「该城市 + 不限城市」的资讯，
     * 且该城市的排在前面；不限城市的资讯在所有城市都能看到。接口公开、无需登录。</p>
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String platform
    ) {
        return ApiResponse.ok(newsService.publicPage(current, size, category, keyword, cityCode, sourceType, platform));
    }

    /** 后台资讯分页，返回全部状态。 */
    @GetMapping("/admin/page")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<PageResult<Map<String, Object>>> adminPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(newsService.adminPage(current, size, category, keyword, status));
    }

    /** 资讯详情。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(newsService.detail(id));
    }

    /** 新增资讯。 */
    @PostMapping
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(newsService.create(payload));
    }

    /** 修改资讯。 */
    @PutMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(newsService.update(id, payload));
    }

    /** 删除资讯。 */
    @DeleteMapping("/{id}")
    @RequireRole({"STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"})
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        newsService.delete(id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }
}
