package com.friendlystation.platform.common;

import java.util.ArrayList;
import java.util.List;

public record PageResult<T>(long current, long size, long total, List<T> records) {

    /** 把完整列表按当前页切分，内存实现与 MySQL 实现共用同一套分页边界规则。 */
    public static <T> PageResult<T> of(long current, long size, List<T> all) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        int from = (int) Math.min((safeCurrent - 1) * safeSize, all.size());
        int to = (int) Math.min(from + safeSize, all.size());
        return new PageResult<>(safeCurrent, safeSize, all.size(), new ArrayList<>(all.subList(from, to)));
    }
}
