package io.quarkiverse.networkfaults.runtime.internal;

import java.util.List;

import io.quarkiverse.networkfaults.Target;

/**
 * A target with the observation and control the Dev UI and the extension's own tests use; not part of the API.
 */
public interface ObservableTarget extends Target {

    /**
     * The faults active right now, {@code none} when idle; a target made of several proxies lists them all.
     */
    String currentFault();

    /**
     * The event log of every proxy behind this target, oldest first.
     */
    List<TargetEvent> events();

    /**
     * Ends every active fault without going through their handles.
     */
    void reset();
}
