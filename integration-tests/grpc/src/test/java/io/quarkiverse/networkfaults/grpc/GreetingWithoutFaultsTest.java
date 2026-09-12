package io.quarkiverse.networkfaults.grpc;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.networkfaults.NetworkFaults;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The application as an ordinary test sees it: no profile, so the gRPC service is reached directly.
 */
@QuarkusTest
public class GreetingWithoutFaultsTest {

    @Inject
    NetworkFaults faults;

    @Test
    public void nothingIsProxied() {
        assertTrue(faults.targetNames().isEmpty(), faults.targetNames().toString());
    }

    @Test
    public void theGreeterAnswers() {
        when()
                .get("/greet?name=plain")
                .then()
                .statusCode(200)
                .body(is("hello plain"));
    }
}
