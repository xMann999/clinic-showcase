package com.example.clinic.controller.paging;

import com.example.clinic.error.FieldViolation;
import com.example.clinic.error.RequestValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Binds raw MVC paging query parameters to {@code PageQuery}.
 *
 * <h2>Responsibility</h2>
 * <p>Handles parameters whose type is exactly {@code PageQuery}; registered by WebConfiguration.
 *
 * <h2>How it works</h2>
 * <p>Reads page, size and repeated sort values directly from the request. Defaults come from
 * {@code @PageableDefault}; invalid values are collected before a structured exception is thrown.
 *
 * <h2>Design choices</h2>
 * <p>Reading raw sort values preserves commas in {@code property,desc}; controllers later enforce
 * resource-specific property allowlists.
 *
 * <h2>Important details</h2>
 * <p>Blank values use defaults or are ignored. Page must be nonnegative and size must be 1-100.
 * Sort syntax validation here does not establish that the named property belongs to a resource.
 */
public class PageQueryArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * Reports whether the controller parameter has exactly the supported paging type.
     *
     * @param parameter the MVC controller parameter being resolved
     * @return whether the parameter type is exactly PageQuery
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PageQuery.class.equals(parameter.getParameterType());
    }

    /**
     * Builds paging input and reports collected numeric, bounds and sort-syntax errors together.
     *
     * <p>Resource-specific allowed sort properties are checked later by {@code PageQuery.toPageable}.
     *
     * @param parameter the MVC controller parameter being resolved
     * @param mavContainer the MVC model/view container, unused
     * @param request the current servlet-backed request
     * @param binderFactory the MVC binder factory, unused
     * @return the bound paging input
     * @throws RequestValidationException if any collected paging input is invalid
     */
    @Override
    public PageQuery resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest request,
        WebDataBinderFactory binderFactory
    ) {
        PageableDefault defaults = parameter.getParameterAnnotation(PageableDefault.class);
        int defaultSize = defaults != null ? defaults.size() : 20;
        Sort defaultSort = defaults != null && defaults.sort().length > 0
            ? Sort.by(defaults.direction(), defaults.sort())
            : Sort.by("id");

        List<FieldViolation> violations = new ArrayList<>();
        int page = parseInt(request.getParameter("page"), 0, "page", violations);
        int size = parseInt(request.getParameter("size"), defaultSize, "size", violations);
        if (page < 0) {
            violations.add(new FieldViolation("page", "Min", "must be greater than or equal to 0"));
        }
        if (size < 1 || size > PageQuery.MAX_SIZE) {
            violations.add(new FieldViolation("size", "Range", "must be between 1 and " + PageQuery.MAX_SIZE));
        }
        List<Sort.Order> orders = parseSort(request.getParameterValues("sort"), violations);
        if (!violations.isEmpty()) {
            throw new RequestValidationException("Invalid paging parameters", violations);
        }
        return new PageQuery(page, size, orders, defaultSort);
    }

    /**
     * Parses a stripped integer, using the default for absent/blank input.
     *
     * <p>Malformed input appends a type error and returns the default so other fields can also be checked.
     *
     * @param raw the raw input text, possibly null
     * @param defaultValue the fallback for absent or invalid input
     * @param field the input field name for error reporting
     * @param violations the field violations, collected or rendered by this operation
     * @return the parsed value or the supplied fallback
     */
    private static int parseInt(String raw, int defaultValue, String field, List<FieldViolation> violations) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException exception) {
            violations.add(new FieldViolation(field, "TYPE_MISMATCH", "must be an integer"));
            return defaultValue;
        }
    }

    /**
     * Parses repeated sort values, skipping blank values and collecting errors in the supplied list.
     *
     * <p>Processes every nonblank expression in request order so multiple syntax errors can be returned
     * together with page and size errors. Valid orders retain that same order in an immutable snapshot.
     *
     * @param rawValues the raw repeated sort parameters, possibly null
     * @param violations the field violations, collected or rendered by this operation
     * @return an immutable list of valid orders, empty if no values were supplied or accepted
     */
    private static List<Sort.Order> parseSort(String[] rawValues, List<FieldViolation> violations) {
        if (rawValues == null) {
            return List.of();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String value : rawValues) {
            if (!value.isBlank()) {
                appendSortOrder(value, orders, violations);
            }
        }
        return List.copyOf(orders);
    }

    /**
     * Appends an order for {@code property} or {@code property,asc|desc}, or records a syntax violation.
     *
     * <p>Missing direction defaults to ascending. The split discards trailing empty segments; direction
     * matching ignores case and surrounding whitespace. A rejected expression does not add an order.
     *
     * @param value the single raw sort expression
     * @param orders the valid orders accumulated in request order
     * @param violations the field violations, collected or rendered by this operation
     */
    private static void appendSortOrder(
        String value,
        List<Sort.Order> orders,
        List<FieldViolation> violations
    ) {
        String[] parts = value.split(",");
        String property = parts[0].strip();
        if (parts.length > 2 || property.isEmpty()) {
            violations.add(new FieldViolation("sort", "INVALID_SORT", "use sort=property or sort=property,asc|desc"));
            return;
        }
        if (parts.length == 1) {
            orders.add(Sort.Order.asc(property));
            return;
        }
        switch (parts[1].strip().toLowerCase(Locale.ROOT)) {
            case "asc" -> orders.add(Sort.Order.asc(property));
            case "desc" -> orders.add(Sort.Order.desc(property));
            default -> violations.add(new FieldViolation("sort", "INVALID_SORT", "direction must be asc or desc"));
        }
    }
}
