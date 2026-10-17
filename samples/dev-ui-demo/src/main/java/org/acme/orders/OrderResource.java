package org.acme.orders;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.smallrye.mutiny.Multi;

/**
 * The endpoints to drive while a fault is active: place an order (one inventory call plus one insert), place one
 * with retries, list the orders (the datasource alone), and stream stock updates (an SSE response relayed through
 * the proxy).
 */
@Path("/orders")
public class OrderResource {

    private final InventoryService inventory;
    private final InventoryClient client;

    public OrderResource(InventoryService inventory, @RestClient InventoryClient client) {
        this.inventory = inventory;
        this.client = client;
    }

    @POST
    @Transactional
    public Response place(@QueryParam("item") String item) {
        if (inventory.reserve(item) == null) {
            return Response.status(503).entity("inventory unavailable").build();
        }
        return Response.status(201).entity(save(item)).build();
    }

    @POST
    @Path("/retrying")
    @Transactional
    public Response placeWithRetry(@QueryParam("item") String item) {
        try {
            inventory.reserveWithRetry(item);
        } catch (RuntimeException e) {
            return Response.status(503).entity("inventory unavailable after retries: " + e.getMessage()).build();
        }
        return Response.status(201).entity(save(item)).build();
    }

    /**
     * A database that cannot be reached is an outage the caller should see as such, not as a stack trace.
     */
    @GET
    public Response list() {
        try {
            List<Order> orders = Order.listAll();
            return Response.ok(orders).build();
        } catch (PersistenceException e) {
            return Response.status(503).entity("orders unavailable: " + e.getMessage()).build();
        }
    }

    /**
     * Relays the inventory's stream; a stream the inventory cuts short ends with a last event saying so, rather than
     * cutting the caller's stream too.
     */
    @GET
    @Path("/stock-updates")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> stockUpdates() {
        return client.stockUpdates().onFailure().recoverWithItem("stream ended early");
    }

    private Order save(String item) {
        Order order = new Order();
        order.item = item;
        order.placedAt = Instant.now();
        order.persist();
        return order;
    }
}
