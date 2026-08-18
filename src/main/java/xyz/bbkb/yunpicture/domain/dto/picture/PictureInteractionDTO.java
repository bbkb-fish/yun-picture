package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;

/** 图片点赞、收藏操作请求。 */
@Data
public class PictureInteractionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 要操作的图片 ID。 */
    private Long pictureId;
}
