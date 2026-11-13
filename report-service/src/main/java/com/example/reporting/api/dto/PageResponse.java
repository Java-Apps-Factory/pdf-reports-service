package com.example.reporting.api.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * One page of results.
 *
 * @param <T> item type
 */
public final class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    private PageResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = List.copyOf(content);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    /**
     * Maps a Spring Data page.
     *
     * @param page source page
     * @param mapper item mapper
     * @param <S> source item type
     * @param <T> response item type
     * @return the response page
     */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
