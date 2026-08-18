package xyz.bbkb.yunpicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xyz.bbkb.yunpicture.domain.entity.UserNotification;

/** 用户通知数据库操作。 */
public interface UserNotificationMapper extends BaseMapper<UserNotification> {

    @Select("""
            SELECT id, user_id AS userId, type, title, content,
                   biz_type AS bizType, biz_id AS bizId,
                   dedupe_key AS dedupeKey, is_read AS isRead,
                   read_time AS readTime, mq_status AS mqStatus,
                   mq_retry_count AS mqRetryCount,
                   mq_next_retry_time AS mqNextRetryTime,
                   mq_sent_time AS mqSentTime,
                   mq_consumed_time AS mqConsumedTime,
                   create_time AS createTime
            FROM user_notification
            WHERE id = #{notificationId}
            LIMIT 1
            """)
    UserNotification selectNotificationById(@Param("notificationId") Long notificationId);

    /** INSERT IGNORE 配合幂等键，防止同一业务事件重复通知。 */
    @Insert("""
            INSERT IGNORE INTO user_notification
                (id, user_id, type, title, content, biz_type, biz_id, dedupe_key,
                 is_read, mq_status, mq_retry_count, mq_next_retry_time, create_time)
            VALUES
                (#{id}, #{userId}, #{type}, #{title}, #{content}, #{bizType},
                 #{bizId}, #{dedupeKey}, 0, 0, 0, NOW(), NOW())
            """)
    int insertIgnore(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("type") String type,
                     @Param("title") String title,
                     @Param("content") String content,
                     @Param("bizType") String bizType,
                     @Param("bizId") Long bizId,
                     @Param("dedupeKey") String dedupeKey);

    @Select("""
            <script>
            SELECT id, user_id AS userId, type, title, content,
                   biz_type AS bizType, biz_id AS bizId,
                   dedupe_key AS dedupeKey, is_read AS isRead,
                   read_time AS readTime, create_time AS createTime
            FROM user_notification
            WHERE user_id = #{userId}
            <if test="unreadOnly != null">
                AND is_read = <choose><when test="unreadOnly">0</when><otherwise>1</otherwise></choose>
            </if>
            ORDER BY create_time DESC, id DESC
            </script>
            """)
    IPage<UserNotification> selectNotificationPage(Page<UserNotification> page,
                                                    @Param("userId") Long userId,
                                                    @Param("unreadOnly") Boolean unreadOnly);

    @Select("SELECT COUNT(*) FROM user_notification WHERE user_id = #{userId} AND is_read = 0")
    long countUnread(@Param("userId") Long userId);

    @Update("""
            UPDATE user_notification
            SET is_read = 1, read_time = NOW()
            WHERE id = #{notificationId} AND user_id = #{userId} AND is_read = 0
            """)
    int markRead(@Param("notificationId") Long notificationId,
                 @Param("userId") Long userId);

    @Update("""
            UPDATE user_notification
            SET is_read = 1, read_time = NOW()
            WHERE user_id = #{userId} AND is_read = 0
            """)
    int markAllRead(@Param("userId") Long userId);

    /** 查询到期的待发送记录；发送前还要通过 claimForPublish 原子抢占。 */
    @Select("""
            SELECT id, user_id AS userId, mq_status AS mqStatus,
                   mq_retry_count AS mqRetryCount,
                   mq_next_retry_time AS mqNextRetryTime
            FROM user_notification
            WHERE (mq_status = 0 AND (mq_next_retry_time IS NULL OR mq_next_retry_time <= NOW()))
               OR (mq_status = 2 AND mq_next_retry_time <= NOW())
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    java.util.List<UserNotification> selectPendingMqNotifications(@Param("limit") int limit);

    /** 多实例下只有一个发布器能把记录从待发送改为发送中。发送中超时后允许重新抢占。 */
    @Update("""
            UPDATE user_notification
            SET mq_status = 2,
                mq_next_retry_time = DATE_ADD(NOW(), INTERVAL 1 MINUTE)
            WHERE id = #{notificationId}
              AND (
                    (mq_status = 0 AND (mq_next_retry_time IS NULL OR mq_next_retry_time <= NOW()))
                    OR (mq_status = 2 AND mq_next_retry_time <= NOW())
                  )
            """)
    int claimForPublish(@Param("notificationId") Long notificationId);

    @Update("""
            UPDATE user_notification
            SET mq_status = 1, mq_sent_time = NOW(), mq_next_retry_time = NULL
            WHERE id = #{notificationId} AND mq_status = 2
            """)
    int markMqSent(@Param("notificationId") Long notificationId);

    @Update("""
            UPDATE user_notification
            SET mq_status = 0,
                mq_retry_count = mq_retry_count + 1,
                mq_next_retry_time = DATE_ADD(NOW(), INTERVAL 10 SECOND)
            WHERE id = #{notificationId} AND mq_status = 2
            """)
    int markMqPublishFailed(@Param("notificationId") Long notificationId);

    @Update("""
            UPDATE user_notification
            SET mq_consumed_time = COALESCE(mq_consumed_time, NOW())
            WHERE id = #{notificationId}
            """)
    int markMqConsumed(@Param("notificationId") Long notificationId);

    /** 仅供RabbitMQ手动集成压测核对本批通知总数。 */
    @Select("""
            SELECT COUNT(*)
            FROM user_notification
            WHERE user_id = #{userId}
              AND dedupe_key LIKE CONCAT(#{dedupePrefix}, '%')
            """)
    long countLoadTestNotifications(@Param("userId") Long userId,
                                    @Param("dedupePrefix") String dedupePrefix);

    /** mq_consumed_time 不为空，证明该通知已被RabbitMQ消费者实际处理。 */
    @Select("""
            SELECT COUNT(*)
            FROM user_notification
            WHERE user_id = #{userId}
              AND dedupe_key LIKE CONCAT(#{dedupePrefix}, '%')
              AND mq_consumed_time IS NOT NULL
            """)
    long countConsumedLoadTestNotifications(@Param("userId") Long userId,
                                            @Param("dedupePrefix") String dedupePrefix);

    /** 可选清理本次压测数据，不会影响真实业务通知。 */
    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM user_notification
            WHERE user_id = #{userId}
              AND dedupe_key LIKE CONCAT(#{dedupePrefix}, '%')
            """)
    int deleteLoadTestNotifications(@Param("userId") Long userId,
                                    @Param("dedupePrefix") String dedupePrefix);
}
