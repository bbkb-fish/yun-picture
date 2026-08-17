package xyz.bbkb.yunpicture.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片累计统计实体，对应 picture_stat 表。
 */
@Data
@TableName("picture_stat")
public class PictureStat implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 图片 ID，同时也是统计表主键。 */
    @TableId(value = "picture_id", type = IdType.INPUT)
    private Long pictureId;

    @TableField("view_count")
    private Long viewCount;

    @TableField("download_count")
    private Long downloadCount;

    @TableField("like_count")
    private Long likeCount;

    @TableField("favorite_count")
    private Long favoriteCount;

    @TableField("update_time")
    private Date updateTime;
}
