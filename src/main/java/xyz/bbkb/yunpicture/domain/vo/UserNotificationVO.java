package xyz.bbkb.yunpicture.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 前端通知数据。 */
@Data
public class UserNotificationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String type;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Boolean read;
    private Date readTime;
    private Date createTime;
}
