package xyz.bbkb.yunpicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import xyz.bbkb.yunpicture.domain.dto.notification.NotificationQueryDTO;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.UserNotificationVO;
import xyz.bbkb.yunpicture.enums.NotificationTypeEnum;

/** 持久化通知业务。 */
public interface NotificationService {
    Long createNotification(Long userId,
                            NotificationTypeEnum type,
                            String title,
                            String content,
                            String bizType,
                            Long bizId,
                            String dedupeKey);

    Page<UserNotificationVO> listMyNotifications(NotificationQueryDTO queryDTO, User loginUser);
    long countUnread(User loginUser);
    boolean markRead(Long notificationId, User loginUser);
    boolean markAllRead(User loginUser);
}
