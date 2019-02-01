package io.axoniq.axonserver.metric;

import java.util.Collections;

/**
 * @author Marc Gathier
 */
public class DefaultMetricCollector implements MetricCollector {

    @Override
    public Iterable<AxonServerMetric> getAll() {
        return Collections.emptyList();
    }

    @Override
    public ClusterMetric apply(String s) {
        return new CounterMetric(s, () -> 0L);
    }
}
