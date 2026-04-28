package com.saga.sattolux.core.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseConnectionManager {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserSeq = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userSeq, String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByUserSeq.computeIfAbsent(userSeq, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userSeq, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(userSeq, emitter);
        });
        emitter.onError(throwable -> removeEmitter(userSeq, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(new SsePayload("connected", "SSE connection established", LocalDateTime.now(), userId)));
        } catch (IOException e) {
            removeEmitter(userSeq, emitter);
            throw new IllegalStateException("Failed to establish SSE connection.", e);
        }

        return emitter;
    }

    public void disconnectAll(Long userSeq) {
        List<SseEmitter> emitters = emittersByUserSeq.remove(userSeq);
        if (emitters == null) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
    }

    public void sendToUser(Long userSeq, String eventName, Object payload) {
        List<SseEmitter> emitters = emittersByUserSeq.get(userSeq);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.debug("Removing broken SSE emitter while sending event={} to userSeq={}", eventName, userSeq, e);
                removeEmitter(userSeq, emitter);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void heartbeat() {
        emittersByUserSeq.forEach((userSeq, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(new SsePayload("heartbeat", "ping", LocalDateTime.now(), null)));
                } catch (IOException e) {
                    log.debug("Removing broken SSE emitter for userSeq={}", userSeq, e);
                    removeEmitter(userSeq, emitter);
                }
            }
        });
    }

    private void removeEmitter(Long userSeq, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUserSeq.get(userSeq);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUserSeq.remove(userSeq);
        }
    }

    public record SsePayload(
            String type,
            String message,
            LocalDateTime timestamp,
            String userId
    ) {
    }
}
