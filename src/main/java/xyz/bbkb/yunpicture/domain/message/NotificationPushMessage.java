package xyz.bbkb.yunpicture.domain.message;

import java.io.Serializable;

/** RabbitMQ 只传递定位通知所需的信息，正文以 MySQL 中的数据为准。 */
public record NotificationPushMessage(Long notificationId,
                                      Long userId) implements Serializable {
}
