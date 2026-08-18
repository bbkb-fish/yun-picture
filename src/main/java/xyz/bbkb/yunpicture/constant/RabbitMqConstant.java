package xyz.bbkb.yunpicture.constant;

/** RabbitMQ 交换机、队列和路由键统一定义。 */
public interface RabbitMqConstant {
    String NOTIFICATION_EXCHANGE = "yun.notification.exchange";
    String NOTIFICATION_PUSH_QUEUE = "yun.notification.push.queue";
    String NOTIFICATION_PUSH_ROUTING_KEY = "notification.push";

    String NOTIFICATION_DEAD_LETTER_EXCHANGE = "yun.notification.dlx";
    String NOTIFICATION_DEAD_LETTER_QUEUE = "yun.notification.push.dlq";
    String NOTIFICATION_DEAD_LETTER_ROUTING_KEY = "notification.push.dead";
}
