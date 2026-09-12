package io.quarkiverse.networkfaults.messagingkafka.deployment;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultTargetBuildItem;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultsEnabled;
import io.quarkiverse.networkfaults.messagingkafka.runtime.internal.MessagingKafkaTargets;
import io.quarkiverse.networkfaults.spi.TargetDescriptor;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;

@BuildSteps(onlyIf = NetworkFaultsEnabled.class, onlyIfNot = IsNormal.class)
class MessagingKafkaTargetsProcessor {

    @BuildStep
    List<NetworkFaultTargetBuildItem> targets() {
        List<NetworkFaultTargetBuildItem> items = new ArrayList<>();
        for (TargetDescriptor d : MessagingKafkaTargets.DESCRIPTORS) {
            items.add(new NetworkFaultTargetBuildItem(d));
        }
        return items;
    }
}
