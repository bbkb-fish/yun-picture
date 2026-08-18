package xyz.bbkb.yunpicture.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 返回给前端的评论信息，不暴露数据库逻辑删除字段。 */
@Data
public class PictureCommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pictureId;
    private Long userId;
    private Long rootId;
    private Long parentId;
    private Long replyUserId;
    private String content;
    private Integer replyCount;
    private Date createTime;
    private Boolean deleted;
    private Boolean canDelete;
    private UserVO user;
    private UserVO replyUser;
}
