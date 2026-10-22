package com.friendlystation.platform.station;

import com.friendlystation.platform.common.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface StationService {
    /**
     * 公开分页查询：返回全部未被逻辑删除的驿站（OPEN 与 CLOSED）。
     *
     * @param cityCode 城市行政区划代码，非空时只返回该城市的驿站
     * @param service  服务项目名称关键字，非空时只返回提供该服务的驿站
     * @param status   驿站状态（OPEN / CLOSED），非空时按状态过滤；是否只看营业中由调用方决定
     */
    PageResult<StationDto> page(long current, long size, String cityCode, String area, String keyword,
                                String service, String status);

    /**
     * 管理端分页查询：返回全部状态（含 DELETED），可按 status 过滤。
     */
    PageResult<StationDto> adminPage(long current, long size, String cityCode, String area, String keyword, String status);

    /**
     * 区域分布统计，用于前台筛选下拉：按驿站数量倒序返回 {@code {"area": ..., "count": ...}}。
     * 传入 cityCode 时只统计该城市的区县。
     */
    List<Map<String, Object>> areaSummary(String cityCode);

    StationDto detail(Long id);

    StationDto create(StationCreateRequest request);

    StationDto update(Long id, StationUpdateRequest request);

    /**
     * 修改驿站状态，只接受 OPEN / CLOSED。
     */
    StationDto updateStatus(Long id, String status);

    /**
     * 逻辑删除：把 status 置为 DELETED。
     */
    void delete(Long id);

    /** 设置暂停服务窗口；期间的新预约会被拒绝。 */
    StationDto suspend(Long id, LocalDateTime from, LocalDateTime to, String reason);

    /** 取消暂停服务。 */
    StationDto resume(Long id);

    /**
     * 查询某个账号负责的驿站，用于驿站管理员的数据范围判定；未分配时返回空列表。
     */
    List<StationDto> listByManager(Long managerId);
}
