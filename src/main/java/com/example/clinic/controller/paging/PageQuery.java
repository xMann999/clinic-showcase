package com.example.clinic.controller.paging;

import com.example.clinic.error.FieldViolation;
import com.example.clinic.error.RequestValidationException;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Carries paging values and requested/default sorting for controller delegation.
 *
 * <p>The MVC resolver validates page and size; directly constructing this record does not enforce those
 * bounds. The constructor defensively copies requested orders. Controllers call {@code toPageable} with
 * the resource's allowed properties so unsupported sorting becomes a structured validation error.
 *
 * @param page the zero-based page index
 * @param size the requested page size
 * @param requestedOrders the explicitly requested orders, copied into an unmodifiable list
 * @param defaultSort the sort used when no explicit orders are supplied
 */
public record PageQuery(int page, int size, List<Sort.Order> requestedOrders, Sort defaultSort) {
    public static final int MAX_SIZE = 100;

    public PageQuery {
        requestedOrders = List.copyOf(requestedOrders);
    }

    /**
     * Creates paging input with identifier-ascending default sorting.
     *
     * @param page the zero-based page index
     * @param size the page size
     * @return the paging value; bounds are checked when creating the pageable
     */
    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size, List.of(), Sort.by("id"));
    }

    /**
     * Validates sort properties and appends ascending identifier order when no identifier order exists.
     *
     * <p>This makes ties deterministic within an unchanged result set; it does not provide a snapshot
     * across requests when rows change. An explicitly requested identifier order retains its position/direction.
     *
     * @param allowedProperties the resource's allowed sort properties
     * @return the pageable with validated sorting
     * @throws RequestValidationException if any sort property is unsupported
     * @throws IllegalArgumentException if page or size violates PageRequest bounds
     */
    public Pageable toPageable(Set<String> allowedProperties) {
        Sort sort = requestedOrders.isEmpty() ? defaultSort : Sort.by(requestedOrders);
        List<FieldViolation> violations = sort
            .stream()
            .map(Sort.Order::getProperty)
            .filter(property -> !allowedProperties.contains(property))
            .map(property ->
                new FieldViolation(
                    "sort",
                    "UNSUPPORTED_SORT_PROPERTY",
                    "Sorting by '" +
                    property +
                    "' is not supported; allowed: " +
                    allowedProperties.stream().sorted().toList()
                )
            )
            .toList();
        if (!violations.isEmpty()) {
            throw new RequestValidationException("Unsupported sort property", violations);
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(page, size, sort);
    }
}
