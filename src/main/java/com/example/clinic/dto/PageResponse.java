package com.example.clinic.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Defines the application's page response independently of Spring Data's Page serialization.
 *
 * <p>Contains zero-based page number, requested size, total row count and total page count alongside
 * content. The record retains its supplied list reference, so direct construction does not make content
 * deeply immutable. {@code from} uses the content supplied by the source Page.
 *
 * @param <T> the content element type
 * @param content the page elements
 * @param page the zero-based page index
 * @param size the requested page size
 * @param totalElements the total matching row count
 * @param totalPages the total matching page count
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    /**
     * Copies content and pagination metadata from a Spring Data page.
     *
     * @param <T> the content element type
     * @param page the nonnull source page
     * @return the response using the source's content list and metadata
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
