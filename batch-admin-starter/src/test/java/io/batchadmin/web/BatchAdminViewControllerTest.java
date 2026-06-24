package io.batchadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.batchadmin.dynamic.StepDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BatchAdminViewControllerTest {

    @Test
    void parsesStepsWithAndWithoutProperties() {
        List<StepDefinition> steps = BatchAdminViewController.parseSteps(
                "extract = log (message=Extracting)\n"
                        + "wait = sleep (millis=2000)\n"
                        + "cleanup = log");

        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).name()).isEqualTo("extract");
        assertThat(steps.get(0).type()).isEqualTo("log");
        assertThat(steps.get(0).properties()).containsEntry("message", "Extracting");
        assertThat(steps.get(1).properties()).containsEntry("millis", "2000");
        assertThat(steps.get(2).properties()).isEmpty();
    }

    @Test
    void rejectsMalformedStepLine() {
        assertThatThrownBy(() -> BatchAdminViewController.parseSteps("this is not valid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesParameterLines() {
        Map<String, String> params = BatchAdminViewController.parseParameters("region=EU\nmode=full\n");
        assertThat(params).containsEntry("region", "EU").containsEntry("mode", "full");
    }

    @Test
    void emptyTextYieldsEmptyCollections() {
        assertThat(BatchAdminViewController.parseSteps("   ")).isEmpty();
        assertThat(BatchAdminViewController.parseParameters(null)).isEmpty();
    }
}
