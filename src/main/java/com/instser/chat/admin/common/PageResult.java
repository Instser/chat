package com.instser.chat.admin.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员列表统一分页结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private long page;
    private long size;
    private long total;
    private long pages;

    public static <T> PageResult<T> of(IPage<T> pageData) {
        return new PageResult<>(
                pageData.getRecords() != null ? pageData.getRecords() : Collections.emptyList(),
                pageData.getCurrent(),
                pageData.getSize(),
                pageData.getTotal(),
                pageData.getPages()
        );
    }

    public static <S, T> PageResult<T> of(IPage<S> pageData, Function<S, T> mapper) {
        List<T> items = pageData.getRecords() == null
                ? Collections.emptyList()
                : pageData.getRecords().stream().map(mapper).collect(Collectors.toList());
        return new PageResult<>(
                items,
                pageData.getCurrent(),
                pageData.getSize(),
                pageData.getTotal(),
                pageData.getPages()
        );
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(Collections.emptyList(), page, size, 0, 0);
    }
}
