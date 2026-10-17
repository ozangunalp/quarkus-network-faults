package io.quarkiverse.networkfaults.runtime.internal;

/**
 * Told when a fault handed out by {@link NetworkFaultsImpl} starts and when it ends, whichever way it ended. Not
 * part of the API: the Dev UI keeps its fault history with it.
 */
public interface FaultListener {

    /**
     * The fault has been applied to every target in {@code record}.
     */
    void started(FaultRecord record);

    /**
     * The fault has ended and been settled: its consequences are cleaned up and the no-traffic guard has judged it.
     */
    void ended(FaultRecord record, FaultEnd end);
}
