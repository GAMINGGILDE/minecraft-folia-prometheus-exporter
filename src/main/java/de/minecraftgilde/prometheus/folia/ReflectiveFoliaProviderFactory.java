package de.minecraftgilde.prometheus.folia;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/** Loads the plugin-owned provider class without a static provider reference. */
public final class ReflectiveFoliaProviderFactory implements FoliaProviderFactory {

    public static final String PROVIDER_CLASS_NAME =
        "de.minecraftgilde.prometheus.folia.provider.FoliaRegionProvider";

    private final ClassLoader classLoader;
    private final String providerClassName;

    public ReflectiveFoliaProviderFactory(ClassLoader classLoader) {
        this(classLoader, PROVIDER_CLASS_NAME);
    }

    ReflectiveFoliaProviderFactory(
        ClassLoader classLoader,
        String providerClassName
    ) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.providerClassName = Objects.requireNonNull(
            providerClassName,
            "providerClassName"
        );
    }

    @Override
    public FoliaProvider create(FoliaProviderContext context) throws Exception {
        Objects.requireNonNull(context, "context");
        try {
            Class<?> rawType = Class.forName(
                providerClassName,
                true,
                classLoader
            );
            Class<? extends FoliaProvider> providerType = rawType.asSubclass(
                FoliaProvider.class
            );
            Constructor<? extends FoliaProvider> constructor = providerType
                .getConstructor(FoliaProviderContext.class);
            return constructor.newInstance(context);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                "Folia provider construction failed",
                cause
            );
        } catch (
            ClassNotFoundException
                | ClassCastException
                | NoSuchMethodException
                | InstantiationException
                | IllegalAccessException exception
        ) {
            throw new IllegalStateException(
                "The isolated Folia provider could not be loaded",
                exception
            );
        }
    }
}
