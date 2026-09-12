package io.quarkiverse.networkfaults.deployment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultDefaultValueBuildItem;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultTargetBuildItem;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultsEnabled;
import io.quarkiverse.networkfaults.runtime.internal.NetworkFaultsRecorder;
import io.quarkiverse.networkfaults.runtime.internal.config.NetworkFaultsConfigBuilder;
import io.quarkiverse.networkfaults.spi.TargetDescriptor;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;

@BuildSteps(onlyIf = NetworkFaultsEnabled.class, onlyIfNot = IsNormal.class)
class NetworkFaultsProcessor {

    private static final String FEATURE = "network-faults";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RunTimeConfigBuilderBuildItem configInterceptor() {
        return new RunTimeConfigBuilderBuildItem(NetworkFaultsConfigBuilder.class);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerTargets(List<NetworkFaultTargetBuildItem> targets, List<NetworkFaultDefaultValueBuildItem> defaults,
            NetworkFaultsRecorder recorder) {
        List<TargetDescriptor> descriptors = new ArrayList<>(targets.size());
        for (NetworkFaultTargetBuildItem target : targets) {
            descriptors.add(target.descriptor());
        }
        Map<String, String> values = new HashMap<>();
        for (NetworkFaultDefaultValueBuildItem d : defaults) {
            values.put(d.property(), d.value());
        }
        recorder.register(descriptors, values);
    }
}
