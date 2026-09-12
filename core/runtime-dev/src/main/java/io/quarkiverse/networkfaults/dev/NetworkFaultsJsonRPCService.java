package io.quarkiverse.networkfaults.dev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.networkfaults.NetworkFaults;
import io.quarkiverse.networkfaults.runtime.internal.ConnectionSnapshot;
import io.quarkiverse.networkfaults.runtime.internal.NetworkFaultsImpl;
import io.quarkiverse.networkfaults.runtime.internal.ObservableTarget;
import io.quarkiverse.networkfaults.runtime.internal.TargetEvent;
import io.quarkiverse.networkfaults.runtime.internal.TargetSnapshot;

/**
 * Backs the Dev UI card: the same snapshots the test API exposes, plus fault toggles.
 * Return values are plain maps and lists so they serialize without any extra Jackson modules.
 * Every public method is called by name from the card's web component through JSON-RPC.
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class NetworkFaultsJsonRPCService {

    private final NetworkFaults faults;

    public NetworkFaultsJsonRPCService(NetworkFaults faults) {
        this.faults = faults;
    }

    private NetworkFaultsImpl impl() {
        return (NetworkFaultsImpl) faults;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean enabled = impl().isEnabled();
        status.put("enabled", enabled);
        status.put("hint",
                enabled ? ""
                        : "Set " + NetworkFaults.ENABLED_PROPERTY + "=true to route outbound connections "
                                + "through fault-injecting proxies in dev mode.");
        status.put("targets", getTargets());
        return status;
    }

    public List<Map<String, Object>> getTargets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TargetSnapshot s : impl().snapshot()) {
            out.add(toMap(s));
        }
        return out;
    }

    public List<Map<String, Object>> getEvents(String target) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TargetEvent e : impl().byName(target).events()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", e.time().toString());
            m.put("type", e.type().name());
            m.put("connectionId", e.connectionId());
            m.put("detail", e.detail());
            out.add(m);
        }
        return out;
    }

    public List<String> getFaults() {
        return FaultSpec.EXAMPLES;
    }

    /**
     * Applies the fault, ending the target's faults first: the card switches faults in one click, and a fault on a
     * whole target has it to itself.
     */
    public String apply(String target, String fault) {
        ObservableTarget selected = impl().byName(target);
        selected.reset();
        FaultSpec.apply(fault, selected);
        return "applied " + fault + " to " + target;
    }

    public String reset(String target) {
        impl().byName(target).reset();
        return "reset " + target;
    }

    public String resetAll() {
        faults.resetAll();
        return "all faults ended";
    }

    public String dropConnections(String target) {
        impl().byName(target).dropConnections();
        return "dropped connections of " + target;
    }

    private static Map<String, Object> toMap(TargetSnapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("upstream", s.upstreamHost() + ":" + s.upstreamPort());
        m.put("proxyPort", s.proxyPort());
        m.put("sourceProperty", s.sourceProperty());
        m.put("listening", s.listening());
        m.put("fault", s.fault());
        m.put("previousFault", s.previousFault());
        m.put("connectionsSeen", s.connectionsSeen());
        m.put("httpRequestsSeen", s.httpRequestsSeen());
        m.put("connectionsReset", s.connectionsReset());
        m.put("upstreamConnectFailures", s.upstreamConnectFailures());
        m.put("bytesUpstream", s.bytesUpstream());
        m.put("bytesDownstream", s.bytesDownstream());
        List<Map<String, Object>> conns = new ArrayList<>();
        for (ConnectionSnapshot c : s.connections()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.id());
            cm.put("client", c.clientAddress());
            cm.put("openedAt", c.openedAt().toString());
            cm.put("lastActivity", c.lastActivity().toString());
            cm.put("bytesUpstream", c.bytesUpstream());
            cm.put("bytesDownstream", c.bytesDownstream());
            cm.put("held", c.held());
            conns.add(cm);
        }
        m.put("connections", conns);
        return m;
    }
}
