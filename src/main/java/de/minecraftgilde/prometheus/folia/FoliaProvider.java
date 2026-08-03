package de.minecraftgilde.prometheus.folia;

/** Neutral lifecycle boundary implemented only by the isolated Folia source set. */
public interface FoliaProvider {

    void start() throws Exception;

    void stop() throws Exception;
}
