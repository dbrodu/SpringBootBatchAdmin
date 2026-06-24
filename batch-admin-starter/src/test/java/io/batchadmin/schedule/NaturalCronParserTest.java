package io.batchadmin.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.support.CronExpression;

class NaturalCronParserTest {

    private final NaturalCronParser parser = new NaturalCronParser();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "tous les jours à 2h            | 0 0 2 * * *",
            "tous les jours à 2h30          | 0 30 2 * * *",
            "toutes les 5 minutes           | 0 */5 * * * *",
            "toutes les minutes             | 0 * * * * *",
            "chaque minute                  | 0 * * * * *",
            "toutes les heures              | 0 0 * * * *",
            "toutes les 2 heures            | 0 0 */2 * * *",
            "tous les lundis à 9h           | 0 0 9 * * MON",
            "tous les vendredis à 18h       | 0 0 18 * * FRI",
            "en semaine à 8h                | 0 0 8 * * MON-FRI",
            "le week-end à 10h              | 0 0 10 * * SAT,SUN",
            "tous les mois le 1 à 3h        | 0 0 3 1 * *",
            "tous les mois le 15 à 8h30     | 0 30 8 15 * *",
            "tous les jours à minuit        | 0 0 0 * * *",
            "tous les jours à midi          | 0 0 12 * * *",
            "every day at 7                 | 0 0 7 * * *",
            "every monday at 9              | 0 0 9 * * MON",
            "every 10 minutes               | 0 */10 * * * *",
    })
    void parsesNaturalLanguage(String phrase, String expectedCron) {
        String cron = parser.toCron(phrase);
        assertThat(cron).isEqualTo(expectedCron);
        assertThat(CronExpression.isValidExpression(cron)).isTrue();
    }

    @Test
    void passesThroughRawCronExpressions() {
        assertThat(parser.toCron("0 0 2 * * *")).isEqualTo("0 0 2 * * *");
        assertThat(parser.toCron("0 */15 * * * *")).isEqualTo("0 */15 * * * *");
    }

    @Test
    void passesThroughSpringMacros() {
        assertThat(parser.toCron("@daily")).isEqualTo("@daily");
        assertThat(parser.toCron("@hourly")).isEqualTo("@hourly");
    }

    @Test
    void rejectsUnrecognizablePhrases() {
        assertThatThrownBy(() -> parser.toCron("n'importe quoi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exemples");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> parser.toCron("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
