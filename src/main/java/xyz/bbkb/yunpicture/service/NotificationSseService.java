package xyz.bbkb.yunpicture.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.bbkb.yunpicture.domain.vo.UserNotificationVO;

/** 在线用户 SSE 连接管理。 */
public interface NotificationSseService {
    SseEmitter subscribe(Long userId);
    void push(Long userId, UserNotificationVO notification);
}
