package io.batchadmin.schedule;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.scheduling.support.CronExpression;

/**
 * Converts a human-readable frequency phrase (French or English) into a Spring 6-field cron
 * expression ({@code second minute hour day-of-month month day-of-week}).
 *
 * <p>If the input is already a valid cron expression or a Spring macro ({@code @daily}, {@code @hourly},
 * …) it is returned unchanged, so operators can always fall back to raw cron. Otherwise a small set
 * of rules recognizes the common cadences, e.g.:</p>
 *
 * <pre>
 *   "toutes les 5 minutes"        -> 0 *&#47;5 * * * *
 *   "toutes les heures"           -> 0 0 * * * *
 *   "tous les jours à 2h30"       -> 0 30 2 * * *
 *   "tous les lundis à 9h"        -> 0 0 9 * * MON
 *   "en semaine à 8h"             -> 0 0 8 * * MON-FRI
 *   "le week-end à 10h"           -> 0 0 10 * * SAT,SUN
 *   "tous les mois le 1 à 3h"     -> 0 0 3 1 * *
 *   "every day at 7"              -> 0 0 7 * * *
 * </pre>
 *
 * <p>Unrecognized phrases raise {@link IllegalArgumentException} with guidance.</p>
 */
public class NaturalCronParser {

    private static final Map<String, String> WEEKDAYS = new LinkedHashMap<>();

    static {
        WEEKDAYS.put("lundi", "MON");
        WEEKDAYS.put("monday", "MON");
        WEEKDAYS.put("mardi", "TUE");
        WEEKDAYS.put("tuesday", "TUE");
        WEEKDAYS.put("mercredi", "WED");
        WEEKDAYS.put("wednesday", "WED");
        WEEKDAYS.put("jeudi", "THU");
        WEEKDAYS.put("thursday", "THU");
        WEEKDAYS.put("vendredi", "FRI");
        WEEKDAYS.put("friday", "FRI");
        WEEKDAYS.put("samedi", "SAT");
        WEEKDAYS.put("saturday", "SAT");
        WEEKDAYS.put("dimanche", "SUN");
        WEEKDAYS.put("sunday", "SUN");
    }

    /**
     * @return a valid Spring cron expression
     * @throws IllegalArgumentException if the phrase cannot be interpreted
     */
    public String toCron(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(
                    "Veuillez saisir une fréquence (ex. « tous les jours à 2h ») ou une expression cron.");
        }
        String raw = input.trim();
        // Already a cron expression or a Spring macro (@daily, @hourly, …): keep it as-is.
        if (CronExpression.isValidExpression(raw)) {
            return raw;
        }
        String cron = parse(normalize(raw));
        if (cron == null || !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("Impossible d'interpréter « " + raw + " ». Exemples acceptés : "
                    + "« toutes les 5 minutes », « toutes les heures », « tous les jours à 2h30 », "
                    + "« tous les lundis à 9h », « en semaine à 8h », « le week-end à 10h », "
                    + "« tous les mois le 1 à 3h ». Une expression cron (0 0 2 * * *) ou une macro (@daily) "
                    + "sont aussi acceptées.");
        }
        return cron;
    }

    private String parse(String s) {
        Matcher m;

        // every N minutes ("toutes les 5 minutes", "chaque minute", "every 10 minutes")
        m = Pattern.compile("(?:toutes? les|chaque|every)\\s*(\\d+)?\\s*min(?:ute)?s?\\b").matcher(s);
        if (m.find()) {
            int n = m.group(1) != null ? Integer.parseInt(m.group(1)) : 1;
            return n <= 1 ? "0 * * * * *" : "0 */" + n + " * * * *";
        }

        // every N hours ("toutes les 2 heures", "every hour")
        m = Pattern.compile("(?:toutes? les|chaque|every)\\s*(\\d+)?\\s*(?:heures?|hours?)\\b").matcher(s);
        if (m.find()) {
            int n = m.group(1) != null ? Integer.parseInt(m.group(1)) : 1;
            return n <= 1 ? "0 0 * * * *" : "0 0 */" + n + " * * *";
        }

        int[] time = extractTime(s);
        int hour = time != null ? time[0] : 0;
        int minute = time != null ? time[1] : 0;
        String hm = minute + " " + hour;

        // weekend
        if (s.contains("week-end") || s.contains("week end") || s.contains("weekend")) {
            return "0 " + hm + " * * SAT,SUN";
        }
        // weekdays (Mon–Fri)
        if (s.contains("en semaine") || s.contains("jours ouvres") || s.contains("ouvrables")
                || s.contains("weekday")) {
            return "0 " + hm + " * * MON-FRI";
        }
        // a specific weekday (tolerating the French plural, e.g. "lundis")
        for (Map.Entry<String, String> entry : WEEKDAYS.entrySet()) {
            if (Pattern.compile("\\b" + entry.getKey() + "s?\\b").matcher(s).find()) {
                return "0 " + hm + " * * " + entry.getValue();
            }
        }
        // monthly
        if (s.contains("mois") || s.contains("mensuel") || s.contains("month")) {
            return "0 " + hm + " " + extractDayOfMonth(s) + " * *";
        }
        // daily
        if (s.contains("jour") || s.contains("quotidien") || s.contains("daily") || containsWord(s, "day")) {
            return "0 " + hm + " * * *";
        }
        // a bare time with no cadence ("à 14h") is treated as daily
        if (time != null) {
            return "0 " + hm + " * * *";
        }
        return null;
    }

    private int[] extractTime(String s) {
        if (s.contains("minuit")) {
            return new int[]{0, 0};
        }
        if (s.contains("midi")) {
            return new int[]{12, 0};
        }
        // "2 heures", "14 heures 30", "2 heures et 30"
        int[] t = match(s, "(\\d{1,2})\\s*heures?(?:\\s*(?:et\\s*)?(\\d{1,2}))?", true);
        if (t != null) {
            return t;
        }
        // "2h", "14h30"
        t = match(s, "(\\d{1,2})\\s*h\\s*(\\d{2})?", true);
        if (t != null) {
            return t;
        }
        // "14:30"
        t = match(s, "(\\d{1,2}):(\\d{2})", true);
        if (t != null) {
            return t;
        }
        // "at 7", "a 7"
        t = match(s, "\\b(?:a|at)\\s+(\\d{1,2})\\b", false);
        return t;
    }

    /** Matches {@code pattern}; group 1 is the hour and (optional) group 2 the minute. */
    private int[] match(String s, String pattern, boolean withMinute) {
        Matcher m = Pattern.compile(pattern).matcher(s);
        if (!m.find()) {
            return null;
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = (withMinute && m.groupCount() >= 2 && m.group(2) != null) ? Integer.parseInt(m.group(2)) : 0;
        if (hour > 23 || minute > 59) {
            return null;
        }
        return new int[]{hour, minute};
    }

    private int extractDayOfMonth(String s) {
        Matcher m = Pattern.compile("\\ble\\s+(\\d{1,2})(?:er|eme|e)?\\b").matcher(s);
        if (m.find()) {
            int day = Integer.parseInt(m.group(1));
            if (day >= 1 && day <= 31) {
                return day;
            }
        }
        if (s.contains("premier") || s.contains("1er")) {
            return 1;
        }
        return 1;
    }

    private boolean containsWord(String s, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(s).find();
    }

    /** Lower-cases, strips accents and collapses whitespace so keyword matching is robust. */
    private String normalize(String raw) {
        String n = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
