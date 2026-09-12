package io.quarkiverse.networkfaults.deployment.spi;

import io.quarkiverse.networkfaults.spi.TargetDescriptor;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Declares a kind of configuration property that carries an upstream address, so the network faults extension
 * can put a proxy in front of it. Produce one per property pattern; the descriptor's family groups them.
 */
public final class NetworkFaultTargetBuildItem extends MultiBuildItem {

    private final TargetDescriptor descriptor;

    public NetworkFaultTargetBuildItem(TargetDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public TargetDescriptor descriptor() {
        return descriptor;
    }
}
