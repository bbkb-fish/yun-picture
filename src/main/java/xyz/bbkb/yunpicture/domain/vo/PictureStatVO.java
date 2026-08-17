package xyz.bbkb.yunpicture.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value ="picture_stat")
public class PictureStatVO implements Serializable {
    /**
     *
     */
    @TableId
    private Long picture_id;

    /**
     *
     */
    private Long view_count;

    /**
     *
     */
    private Long download_count;

    /**
     *
     */
    private Long like_count;

    /**
     *
     */
    private Long favorite_count;

    /**
     *
     */
    private Date update_time;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
