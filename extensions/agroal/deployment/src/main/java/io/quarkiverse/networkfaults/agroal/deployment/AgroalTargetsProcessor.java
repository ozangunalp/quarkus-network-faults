package io.quarkiverse.networkfaults.agroal.deployment;

import java.util.List;

import io.quarkiverse.networkfaults.agroal.runtime.internal.AgroalTargets;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultTargetBuildItem;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultsEnabled;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;

@BuildSteps(onlyIf = NetworkFaultsEnabled.class, onlyIfNot = IsNormal.class)
class AgroalTargetsProcessor {

    @BuildStep
    void targets(List<JdbcDataSourceBuildItem> dataSources, BuildProducer<NetworkFaultTargetBuildItem> targets) {
        for (JdbcDataSourceBuildItem ds : dataSources) {
            targets.produce(new NetworkFaultTargetBuildItem(AgroalTargets.descriptor(ds.getName(), ds.isDefault())));
        }
    }
}
