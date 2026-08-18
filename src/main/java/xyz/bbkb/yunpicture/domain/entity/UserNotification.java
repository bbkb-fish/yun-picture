package xyz.bbkb.yunpicture.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 用户持久化通知，对应 user_notification 表。 */
@Data
@TableName("user_notification")
public class UserNotification implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField("user_id")
    private Long userId;
    private String type;
    private String title;
    private String content;
    @TableField("biz_type")
    private String bizType;
    @TableField("biz_id")
    private Long bizId;
    @TableField("dedupe_key")
    private String dedupeKey;
    @TableField("is_read")
    private Integer isRead;
    @TableField("read_time")
    private Date readTime;
    @TableField("mq_status")
    private Integer mqStatus;
    @TableField("mq_retry_count")
    private Integer mqRetryCount;
    @TableField("mq_next_retry_time")
    private Date mqNextRetryTime;
    @TableField("mq_sent_time")
    private Date mqSentTime;
    @TableField("mq_consumed_time")
    private Date mqConsumedTime;
    @TableField("create_time")
    private Date createTime;
}
