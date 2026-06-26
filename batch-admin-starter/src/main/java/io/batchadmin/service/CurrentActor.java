package io.batchadmin.service;

import java.security.Principal;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Best-effort resolution of <em>who</em> is performing the current action, for audit metadata on
 * job-definition versions.
 *
 * <p>Reads the authenticated principal from the in-flight HTTP request — set by the servlet
 * container or by Spring Security's filter chain when the component's optional security layer is on
 * — and falls back to {@code system} outside a request (e.g. the startup re-registration of
 * persisted jobs) or when the caller is unauthenticated. It deliberately has no compile-time
 * dependency on Spring Security, so it works whether or not security is enabled.</p>
 */
final class CurrentActor {

    static final String SYSTEM = "system";

    private CurrentActor() {
    }

    static String name() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servlet) {
                Principal principal = servlet.getRequest().getUserPrincipal();
                if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
                    return principal.getName();
                }
            }
        } catch (RuntimeException ignored) {
            // No request bound, or the container cannot supply a principal: treat as a system action.
        }
        return SYSTEM;
    }
}
