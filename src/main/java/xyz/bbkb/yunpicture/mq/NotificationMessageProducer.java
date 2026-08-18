package xyz.bbkb.yunpicture.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import xyz.bbkb.yunpicture.constant.RabbitMqConstant;
import xyz.bbkb.yunpicture.domain.message.NotificationPushMessage;

import java.util.concurrent.TimeUnit;

/** 通知消息生产者；通过 Publisher Confirm 确认消息已到达交换机。 */
@Component
@RequiredArgsConstructor
public class NotificationMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(NotificationPushMessage message) {
        CorrelationData correlationData = new CorrelationData(
                String.valueOf(message.notificationId()));
        rabbitTemplate.convertAndSend(
                RabbitMqConstant.NOTIFICATION_EXCHANGE,
                RabbitMqConstant.NOTIFICATION_PUSH_ROUTING_KEY,
                message,
                correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(2, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new AmqpException("RabbitMQ 拒绝通知消息：" + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new AmqpException("RabbitMQ 通知消息没有路由到队列："
                        + correlationData.getReturned().getReplyText());
            }
        } catch (AmqpException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AmqpException("等待 RabbitMQ 确认通知消息失败", exception);
        }
    }
}
