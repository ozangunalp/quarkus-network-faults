package io.quarkiverse.networkfaults.runtime.internal;

import java.time.Instant;

/**
 * How a fault ended: when, the no-traffic guard's verdict, and the connections and HTTP requests the fault's targets
 * saw while it was active, summed over them.
 */
public record FaultEnd(Instant at, Verdict verdict, long connectionsSeen, long httpRequestsSeen) {

    /**
     * What the no-traffic guard made of a fault once it ended.
     */
    public enum Verdict {
        /**
         * Refused and dropped connections never reach the proxy, so {@code unavailable} and {@code connectTimeout}
         * cannot be judged by their traffic: what the application did is the evidence.
         */
        NOT_JUDGED,
        /**
         * What the fault acts on went through the proxy while it was active.
         */
        SAW_TRAFFIC,
        /**
         * Nothing went through the proxy while the fault was active, so it cannot have affected the application.
         */
        NO_TRAFFIC
    }
}
