package xyz.bbkb.yunpicture.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片点赞关系，对应 picture_like 表。
 * 表中的一条记录表示一个用户已经点赞了一张图片。
 */
@Data
@TableName("picture_like")
public class PictureLike implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 点赞用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 被点赞图片 ID。 */
    @TableField("picture_id")
    private Long pictureId;

    /** 首次点赞时间。 */
    @TableField("create_time")
    private Date createTime;
}
