package com.friendlystation.platform.statistics;

import java.util.List;
import java.util.Map;

/**
 * 统计分析服务。所有指标都从业务表实时聚合，不使用固定演示值。
 *
 * <p>指标口径：驿站数量只统计 OPEN 与 CLOSED 状态，逻辑删除的驿站不计入；办结率 = 已办结诉求 / 诉求总量。</p>
 */
public interface StatisticsService {
    Map<String, Object> overview();

    Map<String, Object> monthlyTrend();

    List<Map<String, Object>> serviceRanking();
}
