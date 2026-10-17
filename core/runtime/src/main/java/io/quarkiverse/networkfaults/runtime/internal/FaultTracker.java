package io.quarkiverse.networkfaults.runtime.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.logging.Logger;

import io.quarkiverse.networkfaults.ActiveFault;
import io.quarkiverse.networkfaults.runtime.internal.proxy.ProxyTarget;
import io.quarkiverse.networkfaults.runtime.internal.proxy.ProxyTarget.FaultHandle;
import io.quarkiverse.networkfaults.runtime.internal.proxy.ProxyTarget.Outcome;
import io.quarkiverse.networkfaults.spi.ConsequenceCleaner;

/**
 * Follows every fault handed to a test through {@link NetworkFaultsImpl}. When one ends, whichever way, two things
 * happen: the consequences are cleaned up (pools flushed, circuit breakers reset), and the no-traffic guard is
 * evaluated.
 * <p>
 * <b>The guard.</b> A proxy-based fault test can pass while testing nothing: the fault sits on a proxy the code
 * under test never talks to (the client resolved its address through another configuration key, the wrong bean
 * was named, a pooled connection was reused), the request fails or succeeds for unrelated reasons, and the
 * assertions happen to hold. The guard makes that a failure: if a fault was observable and no connection went
 * through any of its targets while it was active, or, for a fault on some requests only, none of those requests,
 * a violation naming the targets and the configuration properties they came from is recorded. Refused and dropped
 * connects never reach the proxy, so {@code unavailable} and {@code connectTimeout} are not observable and never
 * trigger it. When connections did arrive but the proxy could not pass them on because the upstream was
 * unreachable, the violation says that instead: the application was reset, and the place to look is the
 * dependency, not the configuration.
 * <p>
 * The guard records rather than throws: a fault usually ends inside a try-with-resources, where an exception from
 * {@code close()} would mask the assertion the test just failed with, or from {@code resetAll()} after the test
 * method has returned. The JUnit callback takes the recorded violations after each test and fails the test with
 * them.
 * <p>
 * A fault applied to a composite target is one group, judged by all of its members together: traffic through any
 * member is enough.
 */
public final class FaultTracker {

    private static final Logger LOG = Logger.getLogger(FaultTracker.class);

    private final List<ConsequenceCleaner> cleaners;
    private final Set<Group> active = new LinkedHashSet<>();
    private final List<String> violations = new ArrayList<>();
    private final List<FaultListener> listeners = new CopyOnWriteArrayList<>();

    public FaultTracker(List<ConsequenceCleaner> cleaners) {
        this.cleaners = cleaners;
    }

    /**
     * A fault as the test sees it: ending it ends every member and settles the group.
     */
    final class Group implements ActiveFault {
        private final List<FaultHandle> members;
        private final FaultRecord record;

        private Group(List<FaultHandle> members) {
            this.members = members;
            List<String> targets = new ArrayList<>(members.size());
            for (FaultHandle h : members) {
                targets.add(h.target().name());
            }
            this.record = new FaultRecord(Instant.now(), List.copyOf(targets), members.getFirst().describe());
        }

        FaultRecord record() {
            return record;
        }

        @Override
        public void end() {
            for (FaultHandle h : members) {
                h.end();
            }
            settle(this);
        }
    }

    /**
     * Tells {@code listener} of every fault that starts or ends from now on.
     */
    void addListener(FaultListener listener) {
        listeners.add(listener);
    }

    Group track(List<FaultHandle> handles) {
        Group group = new Group(handles);
        // told before the group is active, so a concurrent end cannot reach a listener ahead of the start
        for (FaultListener listener : listeners) {
            try {
                listener.started(group.record);
            } catch (RuntimeException e) {
                warn(listener, e);
            }
        }
        synchronized (this) {
            active.add(group);
        }
        return group;
    }

    /**
     * Runs cleanup and the guard once per group, whichever way it ended.
     */
    private void settle(Group group) {
        synchronized (this) {
            if (!active.remove(group)) {
                return;
            }
        }
        cleanUp();
        List<FaultHandle> members = group.members;
        boolean observable = true;
        boolean traffic = false;
        for (FaultHandle h : members) {
            observable &= h.fault().observable();
            traffic |= h.outcome().sawTraffic();
        }
        if (observable && !traffic) {
            StringJoiner names = new StringJoiner(", ", "[", "]");
            Set<String> properties = new LinkedHashSet<>();
            Set<String> unreachable = new LinkedHashSet<>();
            for (FaultHandle h : members) {
                names.add(h.target().name());
                properties.add(h.target().sourceProperty());
                Outcome outcome = h.outcome();
                if (outcome.unreachable() > 0) {
                    unreachable.add(h.target().upstreamAddress() + " (" + outcome.unreachableBecause() + ")");
                }
            }
            FaultHandle applied = members.getFirst();
            String fault = "Target " + names + ": the '" + applied.describe() + "' fault was active but ";
            String verdict;
            if (unreachable.isEmpty()) {
                verdict = fault + "no " + applied.actsOn() + " went through the proxy while it was, so it cannot have "
                        + "affected the test. Is the application really using " + String.join(", ", properties) + "?";
            } else {
                // the application did connect; it is the dependency that was not there
                verdict = fault + "the proxy could not connect to the upstream " + String.join(", ", unreachable)
                        + ", so the application's connections were reset instead. A fault acts on a connection only "
                        + "once the upstream has accepted it. Is the dependency behind "
                        + String.join(", ", properties) + " running?";
            }
            synchronized (this) {
                violations.add(verdict);
            }
        }
        FaultEnd.Verdict verdict = !observable ? FaultEnd.Verdict.NOT_JUDGED
                : traffic ? FaultEnd.Verdict.SAW_TRAFFIC : FaultEnd.Verdict.NO_TRAFFIC;
        notifyEnded(group, verdict);
    }

    /**
     * Tells the listeners a fault has ended, with what its handles saw while it was active; one listener failing does
     * not stop the others.
     */
    private void notifyEnded(Group group, FaultEnd.Verdict verdict) {
        long connections = 0;
        long requests = 0;
        for (FaultHandle h : group.members) {
            Outcome outcome = h.outcome();
            connections += outcome.connectionsSeen();
            requests += outcome.httpRequestsSeen();
        }
        FaultEnd end = new FaultEnd(Instant.now(), verdict, connections, requests);
        for (FaultListener listener : listeners) {
            try {
                listener.ended(group.record, end);
            } catch (RuntimeException e) {
                warn(listener, e);
            }
        }
    }

    private static void warn(FaultListener listener, RuntimeException e) {
        LOG.warnf(e, "network faults: fault listener %s failed", listener.getClass().getName());
    }

    /**
     * Runs every cleaner; one failing does not stop the others.
     */
    private void cleanUp() {
        for (ConsequenceCleaner cleaner : cleaners) {
            try {
                cleaner.cleanUp();
            } catch (RuntimeException e) {
                LOG.warnf(e, "network faults: cleaning up after a fault failed in %s", cleaner.getClass().getName());
            }
        }
    }

    /**
     * Ends every fault still active, settling each one.
     */
    void endAll() {
        List<Group> groups;
        synchronized (this) {
            groups = new ArrayList<>(active);
        }
        for (Group g : groups) {
            g.end();
        }
    }

    /**
     * Ends every fault still active on any of {@code targets}, settling each one. A fault on a composite target ends
     * as a whole as soon as one of its members is among them.
     */
    void endOn(List<ProxyTarget> targets) {
        List<Group> groups = new ArrayList<>();
        synchronized (this) {
            for (Group g : active) {
                for (FaultHandle h : g.members) {
                    if (targets.contains(h.target())) {
                        groups.add(g);
                        break;
                    }
                }
            }
        }
        for (Group g : groups) {
            g.end();
        }
    }

    /**
     * How many faults are active right now.
     */
    synchronized int activeCount() {
        return active.size();
    }

    /**
     * The violations recorded since the last call; the list is cleared so the next test starts clean.
     */
    public synchronized List<String> takeViolations() {
        List<String> out = new ArrayList<>(violations);
        violations.clear();
        return out;
    }
}
