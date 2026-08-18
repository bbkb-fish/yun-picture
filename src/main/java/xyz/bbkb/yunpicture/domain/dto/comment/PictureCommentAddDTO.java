package xyz.bbkb.yunpicture.domain.dto.comment;

import lombok.Data;

import java.io.Serializable;

/** 新增图片评论请求。parentId 为空或 0 时发布根评论。 */
@Data
public class PictureCommentAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long pictureId;
    private Long parentId;
    private String content;
}
