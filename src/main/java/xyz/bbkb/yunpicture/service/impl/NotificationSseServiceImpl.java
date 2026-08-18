package xyz.bbkb.yunpicture.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.bbkb.yunpicture.domain.vo.UserNotificationVO;
import xyz.bbkb.yunpicture.service.NotificationSseService;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** 单机 SSE 连接中心；一个用户可同时拥有多个浏览器标签页连接。 */
@Slf4j
@Service
public class NotificationSseServiceImpl implements NotificationSseService {

    private static final long SSE_TIMEOUT = 30L * 60 * 1000;
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<SseEmitter>> connections =
            new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        connections.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("ok")
                    .reconnectTime(3000));
        } catch (IOException exception) {
            remove(userId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @Override
    public void push(Long userId, UserNotificationVO notification) {
        Set<SseEmitter> emitters = connections.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.info("[SSE-PUSH] notificationId={}, userId={}, result=OFFLINE",
                    notification.getId(), userId);
            return;
        }
        int successCount = 0;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.getId()))
                        .name("notification")
                        .data(notification)
                        .reconnectTime(3000));
                successCount++;
            } catch (Exception exception) {
                remove(userId, emitter);
                emitter.complete();
            }
        }
        log.info("[SSE-PUSH] notificationId={}, userId={}, connections={}",
                notification.getId(), userId, successCount);
    }

    /** 心跳避免浏览器、网关或反向代理关闭空闲连接。 */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        connections.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                } catch (Exception exception) {
                    remove(userId, emitter);
                    emitter.complete();
                }
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        CopyOnWriteArraySet<SseEmitter> emitters = connections.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            connections.remove(userId, emitters);
        }
    }
}
