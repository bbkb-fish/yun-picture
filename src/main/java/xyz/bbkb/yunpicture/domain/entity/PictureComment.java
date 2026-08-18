package xyz.bbkb.yunpicture.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片评论，对应 picture_comment 表。
 * rootId 为 0 时表示根评论，否则表示所属根评论；parentId 表示直接回复的评论。
 */
@Data
@TableName("picture_comment")
public class PictureComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("picture_id")
    private Long pictureId;

    @TableField("user_id")
    private Long userId;

    @TableField("root_id")
    private Long rootId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("reply_user_id")
    private Long replyUserId;

    private String content;

    @TableField("reply_count")
    private Integer replyCount;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    @TableLogic
    @TableField("is_delete")
    private Integer isDelete;
}
