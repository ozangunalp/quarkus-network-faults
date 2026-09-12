package io.quarkiverse.networkfaults.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.enterprise.inject.Any;

import org.junit.jupiter.api.Test;

class NetworkFaultsImplTest {

    @Test
    void duplicateQualifiersDoNotSurfaceAsADuplicateElementError() {
        NetworkFaultsImpl faults = new NetworkFaultsImpl(true, List.of(), List.of());
        Any any = Any.Literal.INSTANCE;
        // No resolver matches String here, so resolution still fails - but with a helpful "no extension
        // recognises" message, not the bare "duplicate element" IllegalArgumentException that Set.of(qualifiers)
        // used to raise before reaching resolution.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> faults.target(String.class, any, any));
        assertFalse(e.getMessage().toLowerCase().contains("duplicate"),
                "duplicate qualifiers should not produce a 'duplicate element' error: " + e.getMessage());
    }

    @Test
    void aDisabledInstanceSaysSoBeforeLookingForTheBean() {
        NetworkFaultsImpl faults = new NetworkFaultsImpl(false, List.of(), List.of());
        // not "no extension recognises String": which resolvers exist says nothing while fault injection is off
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> faults.target(String.class));
        assertTrue(e.getMessage().contains("not enabled"), e.getMessage());
    }
}
