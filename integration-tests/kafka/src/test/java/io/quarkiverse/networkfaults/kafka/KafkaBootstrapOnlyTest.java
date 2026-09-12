package io.quarkiverse.networkfaults.kafka;

import static io.restassured.RestAssured.when;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.junit.jupiter.api.Test;

import io.quarkiverse.networkfaults.ActiveFault;
import io.quarkiverse.networkfaults.NetworkFaults;
import io.quarkiverse.networkfaults.Target;
import io.quarkiverse.networkfaults.junit.NetworkFaultsProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.MutinyEmitter;

/**
 * Kafka today: the bootstrap address is proxied, but the broker advertises its real address in the metadata
 * response and the client connects there directly, so faults on the proxy do not reach it.
 * <p>
 * These tests pin that limitation. Once Kafka Dev Services can advertise a reserved proxy port
 * (phase 2 of the plan), {@link #faultsBypassTheProxyOnceTheClientKnowsTheBroker} is the test that must flip.
 */
@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
public class KafkaBootstrapOnlyTest {

    @Inject
    NetworkFaults faults;

    @Test
    public void bootstrapConnectionGoesThroughTheProxy() {
        when()
                .post("/events?event=one")
                .then()
                .statusCode(202);
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(
                () -> when()
                        .get("/events")
                        .then()
                        .statusCode(200)
                        .body("$", hasItem("one")));
        Target kafka = faults.target(MutinyEmitter.class, channel("orders-out"));
        assertTrue(kafka.traffic().connectionsSeen() >= 1, "no connection went through the Kafka proxy " + kafka.name());
    }

    @Test
    public void faultsBypassTheProxyOnceTheClientKnowsTheBroker() {
        when()
                .post("/events?event=warm")
                .then()
                .statusCode(202);
        try (ActiveFault f = faults.target(MutinyEmitter.class, channel("orders-out")).unavailable()) {
            // LIMITATION: the producer already talks to the broker's advertised address, not the proxy
            when()
                    .post("/events?event=during-fault")
                    .then()
                    .statusCode(202);
        }
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(
                () -> when()
                        .get("/events")
                        .then()
                        .statusCode(200)
                        .body("$", hasItem("during-fault")));
    }

    /**
     * The qualifier of the application's emitter; MicroProfile ships no literal for it.
     */
    private static Channel channel(String name) {
        final class Literal extends AnnotationLiteral<Channel> implements Channel {
            @Override
            public String value() {
                return name;
            }
        }
        return new Literal();
    }
}
