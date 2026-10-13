package com.friendlystation.platform.station;

import com.friendlystation.platform.common.PageResult;

import java.util.List;

public interface StationServiceItemService {
    PageResult<StationServiceItemDto> page(long current, long size, Long stationId, String keyword);

    List<StationServiceItemDto> listByStation(Long stationId);

    StationServiceItemDto create(StationServiceItemRequest request);

    StationServiceItemDto update(Long id, StationServiceItemRequest request);

    /**
     * 修改服务项目状态，status 只接受 1（启用）或 0（停用）。
     */
    StationServiceItemDto updateStatus(Long id, Integer status);

    void delete(Long id);
}
