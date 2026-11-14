package com.swiftdrop.merchant.application;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import com.swiftdrop.merchant.domain.MerchantEventAnomaly;

/**
 * Counters of the merchant event consumer (ADR 0004 §10). Tags have a fixed set of values only; parcel
 * and event IDs never become tags.
 */
@Component
public class MerchantEventMetrics {

    private final Counter applied;
    private final Counter duplicates;
    private final Counter deferred;
    private final Counter stuck;
    private final Map<MerchantEventAnomaly.Kind, Counter> anomalies = new EnumMap<>(MerchantEventAnomaly.Kind.class);

    public MerchantEventMetrics(MeterRegistry registry) {
        applied = Counter.builder("swiftdrop.merchant.events.applied")
                .description("Merchant parcel events applied in sequence").register(registry);
        duplicates = Counter.builder("swiftdrop.merchant.events.duplicates")
                .description("Redelivered events recognized and skipped").register(registry);
        deferred = Counter.builder("swiftdrop.merchant.events.pending")
                .description("Events stored as pending because an earlier sequence was missing").register(registry);
        stuck = Counter.builder("swiftdrop.merchant.events.stuck")
                .description("Parcels marked stuck after reconciliation kept failing").register(registry);
        for (MerchantEventAnomaly.Kind kind : MerchantEventAnomaly.Kind.values()) {
            anomalies.put(kind, Counter.builder("swiftdrop.merchant.events.anomalies").tag("kind", kind.name())
                    .description("Events quarantined because they contradict stored events").register(registry));
        }
    }

    void applied() {
        applied.increment();
    }

    void duplicate() {
        duplicates.increment();
    }

    void deferred() {
        deferred.increment();
    }

    void stuck() {
        stuck.increment();
    }

    void anomaly(MerchantEventAnomaly.Kind kind) {
        anomalies.get(kind).increment();
    }
}
