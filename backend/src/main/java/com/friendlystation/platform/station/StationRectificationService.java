package com.friendlystation.platform.station;

import com.friendlystation.platform.common.PageResult;

import java.util.List;
import java.util.Map;

/** 服务质量整改。 */
public interface StationRectificationService {
    PageResult<Map<String, Object>> page(long current, long size, String status, List<Long> stationIds);

    Map<String, Object> create(long stationId, Map<String, Object> request, long operatorId);

    Map<String, Object> reply(long id, String reply, long operatorId);

    Map<String, Object> review(long id, String action, String note, long operatorId);

    Map<String, Object> detail(long id);

    long pendingCount(List<Long> stationIds);
}
