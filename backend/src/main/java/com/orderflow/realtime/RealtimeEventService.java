package com.orderflow.realtime;

import com.orderflow.operations.OperationsHealthService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Publishes lightweight live snapshots to browser subscribers.
 */
@Service
public class RealtimeEventService {

    private final OperationsHealthService operationsHealthService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Creates the realtime event service.
     *
     * @param operationsHealthService health snapshot service
     */
    public RealtimeEventService(OperationsHealthService operationsHealthService) {
        this.operationsHealthService = operationsHealthService;
    }

    /**
     * Opens an SSE subscription and immediately sends the first snapshot.
     *
     * @return SSE emitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        sendSnapshot(emitter);
        return emitter;
    }

    /**
     * Broadcasts snapshots to active subscribers.
     */
    @Scheduled(fixedDelayString = "${orderflow.realtime.snapshot-interval:2000}")
    public void broadcastSnapshot() {
        if (emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            sendSnapshot(emitter);
        }
    }

    private void sendSnapshot(SseEmitter emitter) {
        RealtimeSnapshotResponse snapshot = new RealtimeSnapshotResponse(
                Instant.now(),
                operationsHealthService.getHealth()
        );

        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException exception) {
            emitters.remove(emitter);
        }
    }
}
