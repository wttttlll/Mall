package com.mallagent.common.api;

import java.util.List;

/**
 * 统一分页响应。
 *
 * @param list     当前页数据
 * @param total    总记录数
 * @param pageNum  当前页码，从 1 开始
 * @param pageSize 每页条数
 */
public record PageResult<T>(
        List<T> list,
        long total,
        long pageNum,
        long pageSize
) {

    public PageResult {
        list = list == null ? List.of() : List.copyOf(list);
    }
}
