package com.friendlystation.platform.station;

import java.util.List;
import java.util.Map;

/** 驿站工作人员/志愿者账号。 */
public interface StationMemberService {
    List<Map<String, Object>> list(long stationId, long operatorId);

    Map<String, Object> add(long stationId, Map<String, Object> request, long operatorId);

    Map<String, Object> updateStatus(long id, String status, long operatorId);

    void remove(long id, long operatorId);

    /** 当前账号作为工作人员/志愿者加入的站点编号。 */
    List<Long> stationIdsOf(long userId);
}
