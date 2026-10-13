package com.friendlystation.platform.evaluation;

import com.friendlystation.platform.common.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 驿站服务评价业务接口。
 * 对外返回的评价 Map 字段固定为：
 * id、stationId、stationName、userId、userName、score、serviceScore、
 * environmentScore、facilityScore、content、createTime。
 */
public interface StationEvaluationService {

    /**
     * 提交一条驿站评价，允许同一用户对同一驿站重复评价。
     *
     * @param payload 前端请求体，包含 stationId、score、content、serviceScore、environmentScore、facilityScore
     * @param userId  当前登录用户 id
     * @return 评价明细 Map，并附加 status、dataStatus 供前端直接使用
     */
    Map<String, Object> create(Map<String, Object> payload, long userId);

    /**
     * 分页查询评价，stationId 与 userId 为可选过滤条件，按创建时间倒序。
     */
    PageResult<Map<String, Object>> page(long current, long size, Long stationId, Long userId);

    /**
     * 统计某驿站（stationId 为 null 时统计全部驿站）的评价数量与各项平均分，平均值保留一位小数。
     */
    Map<String, Object> summary(Long stationId);

    /**
     * 查询当前用户的全部评价，按创建时间倒序。
     */
    List<Map<String, Object>> myEvaluations(long userId);
}
