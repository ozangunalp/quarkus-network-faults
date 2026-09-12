package io.quarkiverse.networkfaults.deployment;

import io.quarkiverse.networkfaults.dev.NetworkFaultsJsonRPCService;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.devjsonrpc.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Dev UI card: live targets, fault toggles, connection table and event log.
 */
@BuildSteps(onlyIf = IsLocalDevelopment.class)
class NetworkFaultsDevUIProcessor {

    @BuildStep
    CardPageBuildItem card() {
        CardPageBuildItem card = new CardPageBuildItem();
        card.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:plug-circle-xmark")
                .title("Network faults")
                .componentLink("qwc-network-faults.js"));
        return card;
    }

    @BuildStep
    JsonRPCProvidersBuildItem jsonRpc() {
        return new JsonRPCProvidersBuildItem(NetworkFaultsJsonRPCService.class);
    }
}
