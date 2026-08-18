package xyz.bbkb.yunpicture.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片收藏关系，对应 picture_favorite 表。
 * 表中的一条记录表示一个用户已经收藏了一张图片。
 */
@Data
@TableName("picture_favorite")
public class PictureFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 收藏用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 被收藏图片 ID。 */
    @TableField("picture_id")
    private Long pictureId;

    /** 首次收藏时间。 */
    @TableField("favorite_time")
    private Date favoriteTime;
}
