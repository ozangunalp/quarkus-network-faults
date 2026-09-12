package io.quarkiverse.networkfaults.kafkaclient.deployment;

import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultTargetBuildItem;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultsEnabled;
import io.quarkiverse.networkfaults.kafkaclient.runtime.internal.KafkaClientTargets;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;

@BuildSteps(onlyIf = NetworkFaultsEnabled.class, onlyIfNot = IsNormal.class)
class KafkaClientTargetsProcessor {

    @BuildStep
    NetworkFaultTargetBuildItem target() {
        return new NetworkFaultTargetBuildItem(KafkaClientTargets.DESCRIPTOR);
    }
}
