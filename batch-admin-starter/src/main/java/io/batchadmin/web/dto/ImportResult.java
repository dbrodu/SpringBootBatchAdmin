package io.batchadmin.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Outcome of importing job definitions.
 *
 * @param created job names that were newly created
 * @param updated job names that already existed and were overwritten (only when {@code overwrite=true})
 * @param skipped job names that already existed and were left untouched ({@code overwrite=false})
 * @param failed  job names that could not be imported, mapped to the reason
 */
public record ImportResult(List<String> created, List<String> updated, List<String> skipped,
                           Map<String, String> failed) {
}
