package de.minecraftgilde.prometheus;

import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-safe exporter health and readiness inputs shared with HTTP handlers. */
public final class ExporterLifecycleState {

    private final AtomicBoolean registryAvailable = new AtomicBoolean();
    private final AtomicBoolean metricsCoreStarted = new AtomicBoolean();
    private final AtomicBoolean httpServerRunning = new AtomicBoolean();
    private final AtomicBoolean initializationComplete = new AtomicBoolean();
    private final AtomicBoolean fundamentallyHealthy = new AtomicBoolean(true);

    public boolean isHealthy() {
        return httpServerRunning.get() && fundamentallyHealthy.get();
    }

    public boolean isReady() {
        return fundamentallyHealthy.get()
            && registryAvailable.get()
            && metricsCoreStarted.get()
            && httpServerRunning.get()
            && initializationComplete.get();
    }

    public void setRegistryAvailable(boolean available) {
        registryAvailable.set(available);
    }

    public void setMetricsCoreStarted(boolean running) {
        metricsCoreStarted.set(running);
    }

    public void setHttpServerRunning(boolean running) {
        httpServerRunning.set(running);
    }

    public void setInitializationComplete(boolean complete) {
        initializationComplete.set(complete);
    }

    public void markFundamentallyUnhealthy() {
        fundamentallyHealthy.set(false);
    }
}
