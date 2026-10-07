package com.neueda.leap.team.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public PageResponse { content = List.copyOf(content); }
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        return new PageResponse<>(content, page, size, total, (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size));
    }
}
