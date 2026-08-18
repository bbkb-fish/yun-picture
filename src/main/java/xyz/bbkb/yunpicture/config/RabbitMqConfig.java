package xyz.bbkb.yunpicture.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.bbkb.yunpicture.constant.RabbitMqConstant;

/** 通知消息的持久化队列、重试失败死信队列及 JSON 序列化配置。 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(RabbitMqConstant.NOTIFICATION_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange notificationDeadLetterExchange() {
        return new TopicExchange(RabbitMqConstant.NOTIFICATION_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationPushQueue() {
        return QueueBuilder.durable(RabbitMqConstant.NOTIFICATION_PUSH_QUEUE)
                .deadLetterExchange(RabbitMqConstant.NOTIFICATION_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstant.NOTIFICATION_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMqConstant.NOTIFICATION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding notificationPushBinding(Queue notificationPushQueue,
                                           TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationPushQueue)
                .to(notificationExchange)
                .with(RabbitMqConstant.NOTIFICATION_PUSH_ROUTING_KEY);
    }

    @Bean
    public Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue,
                                                  TopicExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(RabbitMqConstant.NOTIFICATION_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
