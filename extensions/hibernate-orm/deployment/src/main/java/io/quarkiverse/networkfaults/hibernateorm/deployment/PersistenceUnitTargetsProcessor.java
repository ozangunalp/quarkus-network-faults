package io.quarkiverse.networkfaults.hibernateorm.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

import io.quarkiverse.networkfaults.datasource.DatasourceTargets;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultsEnabled;
import io.quarkiverse.networkfaults.hibernateorm.runtime.internal.PersistenceUnitTargets;
import io.quarkiverse.networkfaults.hibernateorm.runtime.internal.PersistenceUnitTargetsRecorder;
import io.quarkiverse.networkfaults.spi.BeanTargetResolver;
import io.quarkiverse.networkfaults.spi.TargetName;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.hibernate.orm.deployment.PersistenceUnitDescriptorBuildItem;

@BuildSteps(onlyIf = NetworkFaultsEnabled.class, onlyIfNot = IsNormal.class)
class PersistenceUnitTargetsProcessor {

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    SyntheticBeanBuildItem resolver(List<PersistenceUnitDescriptorBuildItem> persistenceUnits,
            PersistenceUnitTargetsRecorder recorder) {
        Map<String, TargetName> blocking = new HashMap<>();
        Map<String, TargetName> reactive = new HashMap<>();
        for (PersistenceUnitDescriptorBuildItem unit : persistenceUnits) {
            String datasource = unit.getConfig().getDataSource().orElse(DataSourceUtil.DEFAULT_DATASOURCE_NAME);
            TargetName target = DatasourceTargets.of(datasource, unit.isReactive());
            (unit.isReactive() ? reactive : blocking).put(unit.getPersistenceUnitName(), target);
        }
        return SyntheticBeanBuildItem.configure(PersistenceUnitTargets.Resolver.class)
                .addType(BeanTargetResolver.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.resolver(blocking, reactive))
                .done();
    }
}
