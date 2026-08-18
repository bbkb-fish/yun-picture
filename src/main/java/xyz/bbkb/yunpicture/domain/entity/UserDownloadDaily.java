package xyz.bbkb.yunpicture.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/** 用户每天的原图下载用量，对应 user_download_daily 表。 */
@Data
@TableName("user_download_daily")
public class UserDownloadDaily implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("original_download_count")
    private Integer originalDownloadCount;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
