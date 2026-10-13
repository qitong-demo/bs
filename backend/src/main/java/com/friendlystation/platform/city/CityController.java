package com.friendlystation.platform.city;

import com.friendlystation.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 城市接口（公开）。平台覆盖多个城市，前端在进入页面前用这两个接口确定「用户城市」。
 */
@RestController
@RequestMapping("/api/city")
public class CityController {
    private final CityService cityService;
    private final CityLocator cityLocator;

    public CityController(CityService cityService, CityLocator cityLocator) {
        this.cityService = cityService;
        this.cityLocator = cityLocator;
    }

    /**
     * 平台已覆盖的城市列表，含各城市已收录的驿站数量。列表第一项为平台默认城市。
     */
    @GetMapping("/list")
    public ApiResponse<List<CityDto>> list() {
        return ApiResponse.ok(cityService.list());
    }

    /**
     * 按浏览器定位得到的经纬度返回最近的城市，供前端在用户授权定位后判断所在城市。
     */
    @GetMapping("/nearest")
    public ApiResponse<CityDto> nearest(@RequestParam Double lng, @RequestParam Double lat) {
        return ApiResponse.ok(cityService.nearest(lng, lat));
    }

    /**
     * 按经纬度定位「用户在哪个城市」：用高德逆地理编码得到真实城市名，
     * 并判断该城市是否在平台已覆盖范围内；未覆盖时同时返回最近的已覆盖城市供降级展示。
     */
    @GetMapping("/locate")
    public ApiResponse<CityLocation> locate(@RequestParam Double lng, @RequestParam Double lat) {
        return ApiResponse.ok(cityLocator.locate(lng, lat));
    }
}
