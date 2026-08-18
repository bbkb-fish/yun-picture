package xyz.bbkb.yunpicture.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import xyz.bbkb.yunpicture.constant.RabbitMqConstant;
import xyz.bbkb.yunpicture.domain.entity.UserNotification;
import xyz.bbkb.yunpicture.domain.message.NotificationPushMessage;
import xyz.bbkb.yunpicture.domain.vo.UserNotificationVO;
import xyz.bbkb.yunpicture.mapper.UserNotificationMapper;
import xyz.bbkb.yunpicture.service.NotificationSseService;

import java.util.Objects;

/** RabbitMQ 消费者：读取数据库最终状态，再推送给当前在线用户。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMessageConsumer {

    private final UserNotificationMapper notificationMapper;
    private final NotificationSseService notificationSseService;

    @RabbitListener(queues = RabbitMqConstant.NOTIFICATION_PUSH_QUEUE)
    public void consume(NotificationPushMessage message) {
        if (message == null || message.notificationId() == null || message.userId() == null) {
            return;
        }
        UserNotification notification = notificationMapper.selectNotificationById(
                message.notificationId());
        if (notification == null || !Objects.equals(notification.getUserId(), message.userId())) {
            return;
        }
        if (notification.getMqConsumedTime() != null) {
            log.info("[MQ-CONSUMER] notificationId={} 已消费，忽略重复投递",
                    message.notificationId());
            return;
        }
        UserNotificationVO notificationVO = new UserNotificationVO();
        BeanUtils.copyProperties(notification, notificationVO);
        notificationVO.setRead(Integer.valueOf(1).equals(notification.getIsRead()));
        notificationSseService.push(message.userId(), notificationVO);
        notificationMapper.markMqConsumed(message.notificationId());
        log.info("[MQ-CONSUMER] notificationId={}, userId={}, queue={}, result=ACK",
                message.notificationId(), message.userId(),
                RabbitMqConstant.NOTIFICATION_PUSH_QUEUE);
    }
}
