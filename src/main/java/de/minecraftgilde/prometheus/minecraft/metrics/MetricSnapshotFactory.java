package de.minecraftgilde.prometheus.minecraft.metrics;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class MetricSnapshotFactory {

    private MetricSnapshotFactory() {}

    static GaugeSnapshot gauge(
        MetricFamilyDescriptor descriptor,
        Collection<Value> values
    ) {
        List<GaugeSnapshot.GaugeDataPointSnapshot> points = new ArrayList<>(
            values.size()
        );
        for (Value value : values) {
            points.add(
                new GaugeSnapshot.GaugeDataPointSnapshot(
                    value.value(),
                    value.labels(),
                    null
                )
            );
        }
        return new GaugeSnapshot(descriptor.getMetadata(), points);
    }

    static GaugeSnapshot gauge(
        MetricFamilyDescriptor descriptor,
        Double value
    ) {
        return gauge(
            descriptor,
            value == null
                ? List.of()
                : List.of(new Value(value, Labels.EMPTY))
        );
    }

    static InfoSnapshot info(
        MetricFamilyDescriptor descriptor,
        Collection<Labels> labels
    ) {
        List<InfoSnapshot.InfoDataPointSnapshot> points = labels
            .stream()
            .map(InfoSnapshot.InfoDataPointSnapshot::new)
            .toList();
        return new InfoSnapshot(descriptor.getMetadata(), points);
    }

    record Value(double value, Labels labels) {}
}
