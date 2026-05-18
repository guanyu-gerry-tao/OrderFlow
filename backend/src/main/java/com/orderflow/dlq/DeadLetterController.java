package com.orderflow.dlq;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides DLQ inspection and manual retry APIs.
 */
@RestController
@RequestMapping("/api/dlq")
public class DeadLetterController {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final DeadLetterReplayService deadLetterReplayService;

    /**
     * Creates the DLQ controller.
     *
     * @param deadLetterEventRepository DLQ repository
     * @param deadLetterReplayService replay service
     */
    public DeadLetterController(
            DeadLetterEventRepository deadLetterEventRepository,
            DeadLetterReplayService deadLetterReplayService
    ) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.deadLetterReplayService = deadLetterReplayService;
    }

    /**
     * Lists all DLQ events.
     *
     * @return DLQ event responses
     */
    @GetMapping
    public List<DeadLetterEventResponse> list() {
        return deadLetterEventRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Replays one DLQ event.
     *
     * @param deadLetterEventId DLQ id
     * @return accepted response
     */
    @PostMapping("/{deadLetterEventId}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID deadLetterEventId) {
        deadLetterReplayService.retry(deadLetterEventId);
        return ResponseEntity.accepted().build();
    }

    private DeadLetterEventResponse toResponse(DeadLetterEvent event) {
        return new DeadLetterEventResponse(
                event.getId(),
                event.getOutboxEventId(),
                event.getAggregateId(),
                event.getEventType(),
                event.getRetryCount(),
                event.getLastError(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getReplayedAt()
        );
    }
}
