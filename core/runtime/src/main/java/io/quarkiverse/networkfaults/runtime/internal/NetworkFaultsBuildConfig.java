package io.quarkiverse.networkfaults.runtime.internal;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.network-faults")
public interface NetworkFaultsBuildConfig {

    /**
     * Whether outbound connections are routed through fault-injecting proxies. Decided at build time: when off,
     * the extension registers no interceptor and no proxy.
     * <p>
     * Off by default. Test classes opt in with {@code @TestProfile(NetworkFaultsProfile.class)}, whose config
     * overrides set this to {@code true} for the build made for that profile.
     */
    @WithDefault("false")
    boolean enabled();
}
