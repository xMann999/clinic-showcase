package com.example.clinic.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a request correlation identifier before MVC handling.
 *
 * <h2>Responsibility</h2>
 * <p>Places the identifier in the response header, request attribute and logging context.
 *
 * <h2>How it works</h2>
 * <p>Accepts 8-64 ASCII letters, digits or hyphens; otherwise generates a UUID. Delegates through the
 * filter chain and restores the previous mapped diagnostic context (MDC) value in finally.
 *
 * <h2>Design choices</h2>
 * <p>MDC is thread-bound; restoring it prevents request IDs leaking when servlet threads are reused.
 * The request attribute lets advice find the identifier independently of later MDC changes.
 *
 * <h2>Important details</h2>
 * <p>The highest-precedence servlet filter runs outside controller argument binding. This code does
 * not propagate MDC into arbitrary asynchronous work. The identifier is for correlation, not identity.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String REQUEST_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    /**
     * Establishes correlation state, delegates and restores the previous thread logging context.
     *
     * <p>The request attribute and response header keep the resolved identifier. The finally block restores
     * MDC even when downstream handling fails.
     *
     * @throws ServletException if downstream servlet processing fails
     * @throws IOException if downstream I/O fails
     *
     * @param request the current servlet-backed request
     * @param response the servlet response receiving the Location header
     * @param chain the downstream servlet filter chain
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, requestId);
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }

    /**
     * Accepts an allowed client identifier or generates a UUID for missing/invalid input.
     *
     * @param candidate the optional client header value
     * @return the safe correlation identifier
     */
    static String resolveRequestId(String candidate) {
        if (candidate != null && ALLOWED.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
