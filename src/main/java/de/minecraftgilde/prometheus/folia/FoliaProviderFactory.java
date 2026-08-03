package de.minecraftgilde.prometheus.folia;

/** Creates the isolated provider only after the public capability was confirmed. */
@FunctionalInterface
public interface FoliaProviderFactory {

    FoliaProvider create(FoliaProviderContext context) throws Exception;
}
