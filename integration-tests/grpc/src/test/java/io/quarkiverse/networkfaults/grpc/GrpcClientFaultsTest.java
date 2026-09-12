package io.quarkiverse.networkfaults.grpc;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.networkfaults.ActiveFault;
import io.quarkiverse.networkfaults.NetworkFaults;
import io.quarkiverse.networkfaults.Target;
import io.quarkiverse.networkfaults.grpc.proto.Greeter;
import io.quarkiverse.networkfaults.junit.NetworkFaultsProfile;
import io.quarkiverse.networkfaults.junit.Stopwatch;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The gRPC client calls this application's own service through the proxy; addressed by its type and name.
 */
@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
public class GrpcClientFaultsTest {

    @Inject
    NetworkFaults faults;

    private Target greeter() {
        return faults.target(Greeter.class, GrpcClient.Literal.of("greeter"));
    }

    @Test
    public void greeterUnavailable() {
        when()
                .get("/greet?name=a")
                .then()
                .statusCode(200)
                .body(is("hello a"));

        greeter().dropConnections(); // the channel keeps its connection: force a new connect
        try (ActiveFault f = greeter().unavailable()) {
            when()
                    .get("/greet?name=a")
                    .then()
                    .statusCode(503);
        }

        when()
                .get("/greet?name=a")
                .then()
                .statusCode(200)
                .body(is("hello a"));
    }

    @Test
    public void greeterHangsIsBoundedByTheCallersTimeout() {
        when()
                .get("/greet?name=b")
                .then()
                .statusCode(200);

        try (ActiveFault f = greeter().hangs()) {
            long ms = Stopwatch.millis(() -> when()
                    .get("/greet?name=b")
                    .then()
                    .statusCode(503));
            assertTrue(ms >= 1_500 && ms < 4_000, "expected the caller's 2s await to bound the call, took " + ms + "ms");
        }

        when()
                .get("/greet?name=b")
                .then()
                .statusCode(200);
    }

    @Test
    public void greeterDiesMidRequest() {
        when()
                .get("/greet?name=c")
                .then()
                .statusCode(200);

        try (ActiveFault f = greeter().diesMidRequest()) {
            when()
                    .get("/greet?name=c")
                    .then()
                    .statusCode(503);
        }

        when()
                .get("/greet?name=c")
                .then()
                .statusCode(200);
    }
}
