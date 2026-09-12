package io.quarkiverse.networkfaults.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.networkfaults.ActiveFault;
import io.quarkiverse.networkfaults.NetworkFaults;
import io.quarkiverse.networkfaults.Target;
import io.quarkiverse.networkfaults.junit.NetworkFaultsProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
@QuarkusTestResource(InventoryServer.class)
public class AnnotationBaseUriTest {

    @Inject
    NetworkFaults faults;

    @Test
    public void annotationBaseUriIsProxied() {
        when()
                .get("/orders/price")
                .then()
                .statusCode(200)
                .body(is("9.99"));

        Target pricing = faults.target(PricingClient.class);
        assertTrue(pricing.traffic().connectionsSeen() >= 1, "the call did not go through the proxy");

        try (ActiveFault f = pricing.unavailable()) {
            when()
                    .get("/orders/price")
                    .then()
                    .statusCode(500);
        }

        when()
                .get("/orders/price")
                .then()
                .statusCode(200);
    }
}
