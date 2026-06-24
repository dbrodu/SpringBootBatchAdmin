package io.batchadmin.web;

import io.batchadmin.autoconfigure.BatchAdminProperties;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page GUI: requests to the base path are forwarded to the static
 * {@code index.html} bundled under {@code classpath:/static<basePath>/}. The static assets
 * themselves (JS/CSS) are served directly by Spring's default resource handler.
 */
@Controller
public class BatchAdminUiController {

    private final String forward;

    public BatchAdminUiController(BatchAdminProperties properties) {
        this.forward = "forward:" + properties.getBasePath() + "/index.html";
    }

    @GetMapping({"${batch.admin.base-path:/batch-admin}", "${batch.admin.base-path:/batch-admin}/"})
    public String index() {
        return forward;
    }
}
