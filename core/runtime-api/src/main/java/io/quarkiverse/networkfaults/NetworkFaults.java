package io.quarkiverse.networkfaults;

import java.lang.annotation.Annotation;
import java.util.Set;

import jakarta.enterprise.inject.Instance;

/**
 * Entry point for injecting network faults into the outbound connections of the application under test.
 * <p>
 * Inject it into a test class that uses {@code @TestProfile(NetworkFaultsProfile.class)}:
 *
 * <pre>
 * &#64;Inject
 * NetworkFaults faults;
 *
 * &#64;Test
 * void inventoryHangs() {
 *     try (var ignored = faults.target(InventoryClient.class).hangs()) {
 *         when().post("/orders").then().statusCode(503);
 *     }
 * }
 * </pre>
 *
 * A target is addressed by the CDI bean the application uses to reach it: a type and qualifiers
 * ({@code target(DataSource.class)}, {@code target(DataSource.class, new DataSource.DataSourceLiteral("users"))},
 * {@code target(RedisDataSource.class, RedisClientName.Literal.of("cache"))}) or an injected {@code Instance}. The
 * extension owning the bean maps it to the connection; a REST Client interface needs no qualifier. A target cannot be
 * selected by its registry name, such as {@code datasource:<default>:jdbc}; the names show up in
 * {@link #targetNames()}, in error messages and in the Dev UI.
 * <p>
 * The requests one method of a bean sends are addressed by a method reference, {@code target(InventoryClient::reserve)},
 * and make an {@link Endpoint}: faults on it leave the rest of the target alone.
 */
public interface NetworkFaults {

    /**
     * The build-time property that turns fault injection on; {@code NetworkFaultsProfile} sets it.
     */
    String ENABLED_PROPERTY = "quarkus.network-faults.enabled";

    /**
     * The target behind the bean of this type and qualifiers, as the application would inject it.
     *
     * @throws IllegalArgumentException if no extension recognises the bean, or nothing matches
     */
    Target target(Class<?> type, Annotation... qualifiers);

    /**
     * The target behind the bean an {@code Instance} resolves to, for example an injected
     * {@code @RestClient Instance<InventoryClient>}.
     *
     * @throws IllegalArgumentException if no extension recognises the bean, or nothing matches
     */
    Target target(Instance<?> instance);

    /**
     * The requests a call to one method of a bean sends, named by a method reference: {@code target(InventoryClient::reserve)}
     * for a REST Client method, whose HTTP method and path come from its {@code @POST} and {@code @Path}. Faults
     * applied to the endpoint act on those requests only; the other methods of the client keep working.
     *
     * @throws IllegalArgumentException if the argument is not a method reference of the form {@code Type::method}, or
     *         no extension recognises the method as one that sends requests
     */
    <B> Endpoint target(MethodReference.Method0<B> method);

    /**
     * Like {@link #target(MethodReference.Method0)}, for a method with one parameter.
     */
    <B, P1> Endpoint target(MethodReference.Method1<B, P1> method);

    /**
     * Like {@link #target(MethodReference.Method0)}, for a method with two parameters.
     */
    <B, P1, P2> Endpoint target(MethodReference.Method2<B, P1, P2> method);

    /**
     * Like {@link #target(MethodReference.Method0)}, for a method with three parameters.
     */
    <B, P1, P2, P3> Endpoint target(MethodReference.Method3<B, P1, P2, P3> method);

    /**
     * Like {@link #target(MethodReference.Method0)}, for a method with four parameters.
     */
    <B, P1, P2, P3, P4> Endpoint target(MethodReference.Method4<B, P1, P2, P3, P4> method);

    /**
     * Like {@link #target(MethodReference.Method0)}, for a method with five parameters.
     */
    <B, P1, P2, P3, P4, P5> Endpoint target(MethodReference.Method5<B, P1, P2, P3, P4, P5> method);

    /**
     * Every known target at once, for "everything is down" tests.
     */
    Target all();

    /**
     * Names of every target discovered so far. Targets appear when their configuration is first read.
     */
    Set<String> targetNames();

    /**
     * Ends every active fault and cleans up its consequences (pools, circuit breakers).
     */
    void resetAll();
}
