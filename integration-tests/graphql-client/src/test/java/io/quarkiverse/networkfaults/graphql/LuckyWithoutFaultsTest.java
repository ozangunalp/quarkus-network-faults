package io.quarkiverse.networkfaults.graphql;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.networkfaults.NetworkFaults;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class LuckyWithoutFaultsTest {

    @Inject
    NetworkFaults faults;

    @Test
    public void nothingIsProxied() {
        assertTrue(faults.targetNames().isEmpty(), faults.targetNames().toString());
    }

    @Test
    public void bothClientsAnswer() {
        when()
                .get("/lucky/typesafe")
                .then()
                .statusCode(200)
                .body(is("12"));

        when()
                .get("/lucky/dynamic")
                .then()
                .statusCode(200)
                .body(is("12"));
    }
}
