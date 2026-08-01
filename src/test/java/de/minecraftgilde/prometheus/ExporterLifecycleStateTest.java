package de.minecraftgilde.prometheus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExporterLifecycleStateTest {

    @Test
    void readinessRequiresEveryMandatoryComponent() {
        ExporterLifecycleState state = new ExporterLifecycleState();

        assertFalse(state.isReady());
        state.setRegistryAvailable(true);
        state.setMetricsCoreStarted(true);
        state.setHttpServerRunning(true);
        assertTrue(state.isHealthy());
        assertFalse(state.isReady());

        state.setInitializationComplete(true);
        assertTrue(state.isReady());

        state.setHttpServerRunning(false);
        assertFalse(state.isHealthy());
        assertFalse(state.isReady());
    }

    @Test
    void fundamentalDamageOverridesHealthAndReadiness() {
        ExporterLifecycleState state = new ExporterLifecycleState();
        state.setRegistryAvailable(true);
        state.setMetricsCoreStarted(true);
        state.setHttpServerRunning(true);
        state.setInitializationComplete(true);

        state.markFundamentallyUnhealthy();

        assertFalse(state.isHealthy());
        assertFalse(state.isReady());
    }
}
