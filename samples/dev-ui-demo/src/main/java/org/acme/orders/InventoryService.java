package org.acme.orders;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * The resilience policy around the inventory service: the part of the application a fault test, or an agent, is
 * meant to verify. The annotations here are what the Dev MCP inventory resource lists.
 */
@ApplicationScoped
public class InventoryService {

    private final InventoryClient client;

    public InventoryService(@RestClient InventoryClient client) {
        this.client = client;
    }

    /**
     * Reserves an item, or answers with the fallback when the inventory service is slow, down or failing. The
     * breaker opens after two failures in a row and stays open for ten seconds, so the Dev UI shows calls that never
     * reach the proxy while it is open.
     */
    @Timeout(2000)
    @CircuitBreaker(requestVolumeThreshold = 2, failureRatio = 1.0, delay = 10_000)
    @Fallback(fallbackMethod = "unavailable")
    public String reserve(String item) {
        return client.reserve(item);
    }

    String unavailable(String item, Throwable cause) {
        return null;
    }

    /**
     * Reserves an item, retrying a few times: a transient failure a {@code forFirst(n)} fault produces is absorbed,
     * one that outlasts the retries is not.
     */
    @Retry(maxRetries = 3, delay = 200, jitter = 0)
    public String reserveWithRetry(String item) {
        return client.reserve(item);
    }
}
