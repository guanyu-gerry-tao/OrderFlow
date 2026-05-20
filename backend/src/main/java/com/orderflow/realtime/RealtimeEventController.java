package com.orderflow.realtime;

import com.orderflow.config.ConditionalOnRuntimeRole;
import com.orderflow.config.RuntimeRole;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Provides Server-Sent Events for the operations console.
 */
@RestController
@RequestMapping("/api/realtime")
@ConditionalOnRuntimeRole(RuntimeRole.API)
public class RealtimeEventController {

    private final RealtimeEventService realtimeEventService;

    /**
     * Creates the realtime event controller.
     *
     * @param realtimeEventService realtime event service
     */
    public RealtimeEventController(RealtimeEventService realtimeEventService) {
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * Streams live operations snapshots to the browser.
     *
     * @return SSE emitter
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return realtimeEventService.subscribe();
    }
}
