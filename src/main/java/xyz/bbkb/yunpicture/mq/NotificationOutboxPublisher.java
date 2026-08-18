package xyz.bbkb.yunpicture.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.bbkb.yunpicture.domain.entity.UserNotification;
import xyz.bbkb.yunpicture.domain.message.NotificationPushMessage;
import xyz.bbkb.yunpicture.mapper.UserNotificationMapper;

import java.util.List;

/**
 * 通知事务发件箱发布器。
 * 应用在数据库提交后、发送 MQ 前宕机时，定时任务会重新扫描并补发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

    private static final int RETRY_BATCH_SIZE = 100;
    private final UserNotificationMapper notificationMapper;
    private final NotificationMessageProducer notificationMessageProducer;

    public boolean publish(Long notificationId, Long userId) {
        if (notificationMapper.claimForPublish(notificationId) != 1) {
            return false;
        }
        try {
            notificationMessageProducer.send(
                    new NotificationPushMessage(notificationId, userId));
            notificationMapper.markMqSent(notificationId);
            log.info("[MQ-PRODUCER] notificationId={}, userId={}, result=ACK", notificationId, userId);
            return true;
        } catch (Exception exception) {
            notificationMapper.markMqPublishFailed(notificationId);
            log.warn("[MQ-PRODUCER] notificationId={}, userId={}, result=FAILED，等待补发",
                    notificationId, userId, exception);
            return false;
        }
    }

    /** 每5秒补发事务已提交但尚未被RabbitMQ确认的通知。 */
    @Scheduled(fixedDelay = 5_000)
    public void retryPendingNotifications() {
        List<UserNotification> pending = notificationMapper
                .selectPendingMqNotifications(RETRY_BATCH_SIZE);
        for (UserNotification notification : pending) {
            publish(notification.getId(), notification.getUserId());
        }
    }
}
