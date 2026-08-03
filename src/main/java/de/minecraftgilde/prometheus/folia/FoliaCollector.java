package de.minecraftgilde.prometheus.folia;

import de.minecraftgilde.prometheus.collector.AbstractCollector;
import de.minecraftgilde.prometheus.collector.UnsupportedCollectorException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Managed common-side gate that never loads the provider before capability success. */
public final class FoliaCollector extends AbstractCollector {

    public static final String UNSUPPORTED_WARNING =
        "The Folia collector is enabled, but the public Server#getRegionTPS(World,int,int) capability is unavailable; the collector is unsupported and no Folia metrics will be registered.";

    private final BooleanSupplier capability;
    private final FoliaProviderFactory providerFactory;
    private final FoliaProviderContext context;
    private final Consumer<String> warningListener;
    private final AtomicBoolean warned = new AtomicBoolean();
    private FoliaProvider provider;

    public FoliaCollector(
        boolean enabled,
        BooleanSupplier capability,
        FoliaProviderFactory providerFactory,
        FoliaProviderContext context,
        Consumer<String> warningListener
    ) {
        super("folia", enabled);
        this.capability = Objects.requireNonNull(capability, "capability");
        this.providerFactory = Objects.requireNonNull(
            providerFactory,
            "providerFactory"
        );
        this.context = Objects.requireNonNull(context, "context");
        this.warningListener = Objects.requireNonNull(
            warningListener,
            "warningListener"
        );
    }

    @Override
    protected void initialize() throws Exception {
        if (!capability.getAsBoolean()) {
            if (warned.compareAndSet(false, true)) {
                warningListener.accept(UNSUPPORTED_WARNING);
            }
            throw new UnsupportedCollectorException(UNSUPPORTED_WARNING);
        }
        provider = providerFactory.create(context);
    }

    @Override
    protected void startCollector() throws Exception {
        provider.start();
    }

    @Override
    protected void stopCollector() throws Exception {
        FoliaProvider current = provider;
        if (current != null) {
            current.stop();
        }
    }
}
