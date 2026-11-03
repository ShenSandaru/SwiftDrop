package com.swiftdrop.tracking.application;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.PublicTrackingView;

/**
 * Live tracking streams (ADR 0007): one public Server-Sent Events stream per tracking number. Sends
 * a {@code snapshot} on connect and a {@code status} event after each committed change, then closes
 * when the parcel is delivered or failed. Keeps streams in memory only; stores nothing.
 */
@Service
public class LiveTracking {

    static final Duration TIMEOUT = Duration.ofMinutes(30);
    static final Duration HEARTBEAT = Duration.ofSeconds(25);
    static final int MAX_STREAMS_PER_PARCEL = 20;
    static final int MAX_STREAMS = 1_000;

    private static final Logger log = LoggerFactory.getLogger(LiveTracking.class);

    private final ParcelQueries queries;
    private final Supplier<SseEmitter> emitters;
    private final ScheduledExecutorService heartbeat;
    private final Map<String, Set<SseEmitter>> streams = new ConcurrentHashMap<>();
    private int openStreams;

    @Autowired
    public LiveTracking(ParcelQueries queries) {
        this(queries, () -> new SseEmitter(TIMEOUT.toMillis()), Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tracking-heartbeat");
            thread.setDaemon(true);
            return thread;
        }));
        heartbeat.scheduleAtFixedRate(this::sendHeartbeats, HEARTBEAT.toSeconds(), HEARTBEAT.toSeconds(),
                TimeUnit.SECONDS);
    }

    /** For tests: emitters can be replaced and no heartbeat runs unless an executor is given. */
    LiveTracking(ParcelQueries queries, Supplier<SseEmitter> emitters, ScheduledExecutorService heartbeat) {
        this.queries = queries;
        this.emitters = emitters;
        this.heartbeat = heartbeat;
    }

    /**
     * Opens a stream. Registers it before reading the snapshot, so a change committed in between is
     * still pushed; clients ignore updates whose sequence their snapshot already contains.
     *
     * @throws UnknownTrackingNumberException if no parcel has this number
     * @throws TooManyStreamsException if a stream limit is reached
     */
    public SseEmitter subscribe(String trackingNumber) {
        // Cheap existence check first, so unknown numbers never take a stream slot
        String key = queries.findPublicTrackingView(trackingNumber)
                .map(PublicTrackingView::trackingNumber)
                .orElseThrow(UnknownTrackingNumberException::new);
        SseEmitter emitter = emitters.get();
        register(key, emitter);
        emitter.onCompletion(() -> unregister(key, emitter));
        emitter.onTimeout(() -> unregister(key, emitter));
        emitter.onError(error -> unregister(key, emitter));

        Optional<PublicTrackingView> snapshot = queries.findPublicTrackingView(key);
        if (snapshot.isEmpty()) {
            unregister(key, emitter);
            throw new UnknownTrackingNumberException();
        }
        if (send(key, emitter, SseEmitter.event().name("snapshot").data(snapshot.get()))
                && isFinal(snapshot.get().status())) {
            // Nothing will change any more
            close(key, emitter);
        }
        return emitter;
    }

    /** Pushes a committed change to the parcel's open streams; never runs for rolled-back changes. */
    @TransactionalEventListener
    public void onStatusChanged(ParcelStatusChanged event) {
        String key = event.data().trackingNumber();
        Set<SseEmitter> open = streams.get(key);
        if (open == null) {
            return;
        }
        StatusUpdate update = StatusUpdate.from(event);
        boolean last = isFinal(update.status());
        for (SseEmitter emitter : List.copyOf(open)) {
            if (send(key, emitter, SseEmitter.event().name("status").id(Integer.toString(update.sequence()))
                    .data(update))
                    && last) {
                close(key, emitter);
            }
        }
    }

    /** Comment lines keep proxies from closing idle connections and detect dead clients. */
    void sendHeartbeats() {
        for (Map.Entry<String, Set<SseEmitter>> parcel : List.copyOf(streams.entrySet())) {
            for (SseEmitter emitter : List.copyOf(parcel.getValue())) {
                send(parcel.getKey(), emitter, SseEmitter.event().comment("heartbeat"));
            }
        }
    }

    int openStreams() {
        synchronized (streams) {
            return openStreams;
        }
    }

    private void register(String key, SseEmitter emitter) {
        synchronized (streams) {
            Set<SseEmitter> forParcel = streams.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
            if (openStreams >= MAX_STREAMS || forParcel.size() >= MAX_STREAMS_PER_PARCEL) {
                if (forParcel.isEmpty()) {
                    streams.remove(key);
                }
                throw new TooManyStreamsException();
            }
            forParcel.add(emitter);
            openStreams++;
        }
    }

    private void unregister(String key, SseEmitter emitter) {
        synchronized (streams) {
            Set<SseEmitter> forParcel = streams.get(key);
            if (forParcel != null && forParcel.remove(emitter)) {
                openStreams--;
                if (forParcel.isEmpty()) {
                    streams.remove(key);
                }
            }
        }
    }

    private void close(String key, SseEmitter emitter) {
        unregister(key, emitter);
        emitter.complete();
    }

    /** False when the client is gone; the stream is then unregistered and closed. */
    private boolean send(String key, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            // A disconnected client is normal for streams; no stack trace, no tracking number
            log.debug("Live tracking stream closed while sending");
            unregister(key, emitter);
            try {
                emitter.completeWithError(e);
            } catch (IllegalStateException alreadyClosed) {
                // Nothing left to close
            }
            return false;
        }
    }

    private static boolean isFinal(ParcelStatus status) {
        return status == ParcelStatus.DELIVERED || status == ParcelStatus.FAILED;
    }

    @PreDestroy
    void shutdown() {
        if (heartbeat != null) {
            heartbeat.shutdownNow();
        }
    }
}
