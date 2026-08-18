package xyz.bbkb.yunpicture.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.bbkb.yunpicture.domain.dto.notification.NotificationQueryDTO;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.entity.UserNotification;
import xyz.bbkb.yunpicture.domain.vo.UserNotificationVO;
import xyz.bbkb.yunpicture.enums.NotificationTypeEnum;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.mapper.UserNotificationMapper;
import xyz.bbkb.yunpicture.mq.NotificationOutboxPublisher;
import xyz.bbkb.yunpicture.service.NotificationService;

import java.util.List;

/** 通知先持久化，事务提交后发送 RabbitMQ，由消费者执行 SSE 推送。 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 20;
    private final UserNotificationMapper notificationMapper;
    private final NotificationOutboxPublisher notificationOutboxPublisher;

    @Override
    public Long createNotification(Long userId,
                                   NotificationTypeEnum type,
                                   String title,
                                   String content,
                                   String bizType,
                                   Long bizId,
                                   String dedupeKey) {
        if (userId == null || userId <= 0 || type == null) {
            return null;
        }
        String safeTitle = truncate(title, 128);
        String safeContent = truncate(content, 500);
        Long notificationId = IdWorker.getId();
        int inserted = notificationMapper.insertIgnore(notificationId, userId, type.name(),
                safeTitle, safeContent, bizType, bizId, dedupeKey);
        if (inserted != 1) {
            return null;
        }

        // 不再直接调用 SSE：实时通知必须先被 RabbitMQ 消费。
        Runnable pushTask = () -> notificationOutboxPublisher.publish(notificationId, userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pushTask.run();
                }
            });
        } else {
            pushTask.run();
        }
        return notificationId;
    }

    @Override
    public Page<UserNotificationVO> listMyNotifications(NotificationQueryDTO queryDTO,
                                                        User loginUser) {
        validateLoginUser(loginUser);
        ThrowUtils.throwIf(queryDTO == null || queryDTO.getCurrent() <= 0
                        || queryDTO.getPageSize() <= 0
                        || queryDTO.getPageSize() > MAX_PAGE_SIZE,
                ErrorCode.PARAMS_ERROR, "分页参数不合法，单页最多 20 条");
        IPage<UserNotification> source = notificationMapper.selectNotificationPage(
                new Page<>(queryDTO.getCurrent(), queryDTO.getPageSize()),
                loginUser.getId(), queryDTO.getUnreadOnly());
        List<UserNotificationVO> records = source.getRecords().stream().map(notification -> {
            UserNotificationVO vo = new UserNotificationVO();
            BeanUtils.copyProperties(notification, vo);
            vo.setRead(Integer.valueOf(1).equals(notification.getIsRead()));
            return vo;
        }).toList();
        Page<UserNotificationVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(records);
        return target;
    }

    @Override
    public long countUnread(User loginUser) {
        validateLoginUser(loginUser);
        return notificationMapper.countUnread(loginUser.getId());
    }

    @Override
    public boolean markRead(Long notificationId, User loginUser) {
        validateLoginUser(loginUser);
        ThrowUtils.throwIf(notificationId == null || notificationId <= 0,
                ErrorCode.PARAMS_ERROR, "通知 ID 不合法");
        // 限定 user_id，防止用户通过枚举通知 ID 修改他人的通知。
        notificationMapper.markRead(notificationId, loginUser.getId());
        return true;
    }

    @Override
    public boolean markAllRead(User loginUser) {
        validateLoginUser(loginUser);
        notificationMapper.markAllRead(loginUser.getId());
        return true;
    }

    private void validateLoginUser(User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);
    }

    private String truncate(String value, int maxLength) {
        String safeValue = value == null ? "" : value.trim();
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }
}
