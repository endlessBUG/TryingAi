package com.ai.trainer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
public class LogBroadcastService {

    private final ConcurrentHashMap<String, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public void subscribe(String taskId, SseEmitter emitter) {
        subscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> unsubscribe(taskId, emitter));
        emitter.onTimeout(() -> unsubscribe(taskId, emitter));
        emitter.onError(e -> unsubscribe(taskId, emitter));
    }

    public void unsubscribe(String taskId, SseEmitter emitter) {
        Set<SseEmitter> emitters = subscribers.get(taskId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                subscribers.remove(taskId);
            }
        }
    }

    public void send(String taskId, String line) {
        Set<SseEmitter> emitters = subscribers.get(taskId);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(line));
            } catch (IOException e) {
                unsubscribe(taskId, emitter);
            }
        }
    }

    public void complete(String taskId) {
        Set<SseEmitter> emitters = subscribers.remove(taskId);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (IOException ignored) {
            }
        }
    }
}
