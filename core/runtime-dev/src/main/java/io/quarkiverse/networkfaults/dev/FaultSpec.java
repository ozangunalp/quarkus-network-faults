package io.quarkiverse.networkfaults.dev;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import io.quarkiverse.networkfaults.ActiveFault;
import io.quarkiverse.networkfaults.Moment;
import io.quarkiverse.networkfaults.Target;
import io.quarkus.runtime.configuration.DurationConverter;

/**
 * Textual form of a fault, as the Dev UI sends it: {@code unavailable}, {@code connect-timeout}, {@code hangs},
 * {@code slow:<duration>}, {@code dies-mid-request}, {@code closes}, and {@code dies-mid-request:<moment>},
 * {@code closes:<moment>} or {@code hangs:<moment>} where the moment is {@code <bytes>}, {@code next-write}, {@code headers},
 * {@code chunks:<n>} or {@code sse:<n>}.
 */
final class FaultSpec {

    /**
     * Every accepted form, with a sample argument where one is needed.
     */
    static final List<String> EXAMPLES = List.of("unavailable", "connect-timeout", "hangs", "slow:10s",
            "dies-mid-request", "dies-mid-request:512", "dies-mid-request:next-write", "dies-mid-request:headers",
            "dies-mid-request:chunks:2", "dies-mid-request:sse:3", "hangs:sse:3", "closes", "closes:sse:1");

    private FaultSpec() {
    }

    /**
     * Applies the fault {@code spec} names to {@code target}.
     *
     * @throws IllegalArgumentException if {@code spec} is not one of the accepted forms
     */
    static ActiveFault apply(String spec, Target target) {
        String s = spec.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        String kind = colon < 0 ? s : s.substring(0, colon);
        String arg = colon < 0 ? null : s.substring(colon + 1).trim();
        switch (kind) {
            case "unavailable", "connect-timeout" -> {
                if (arg != null) {
                    throw new IllegalArgumentException("fault '" + kind + "' takes no argument: " + spec);
                }
                return kind.equals("unavailable") ? target.unavailable() : target.connectTimeout();
            }
            case "hangs" -> {
                return arg == null ? target.hangs() : target.hangs(parseMoment(arg, spec));
            }
            case "dies-mid-request" -> {
                return arg == null ? target.diesMidRequest() : target.diesMidRequest(parseMoment(arg, spec));
            }
            case "closes" -> {
                return arg == null ? target.closes() : target.closes(parseMoment(arg, spec));
            }
            case "slow" -> {
                if (arg == null) {
                    throw new IllegalArgumentException("slow needs a duration, for example slow:10s: " + spec);
                }
                return target.slow(parseDuration(arg));
            }
            default -> throw new IllegalArgumentException("unknown fault '" + kind + "' in '" + spec
                    + "'; expected one of " + EXAMPLES);
        }
    }

    /**
     * {@code <bytes>}, {@code next-write}, {@code headers}, {@code chunks:<n>} or {@code sse:<n>}.
     */
    private static Moment parseMoment(String arg, String spec) {
        if (arg.equals("next-write")) {
            return Moment.onNextWrite();
        }
        if (arg.equals("headers")) {
            return Moment.Http.afterHeaders();
        }
        if (arg.startsWith("chunks:")) {
            return Moment.Http.afterChunks(Integer.parseInt(arg.substring("chunks:".length()).trim()));
        }
        if (arg.startsWith("sse:")) {
            return Moment.Http.afterSseEvents(Integer.parseInt(arg.substring("sse:".length()).trim()));
        }
        try {
            return Moment.afterBytes(Long.parseLong(arg));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cannot parse moment '" + arg + "' in '" + spec
                    + "'; expected a byte count, next-write, headers, chunks:<n> or sse:<n>", e);
        }
    }

    /**
     * Quarkus' own duration syntax: {@code 10s}, {@code 500ms}, {@code 1h30m}, {@code PT10S}, plain seconds.
     */
    private static Duration parseDuration(String value) {
        try {
            return DurationConverter.parseDuration(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cannot parse duration '" + value + "' (use 10s, 500ms, 2m or PT10S)", e);
        }
    }
}
