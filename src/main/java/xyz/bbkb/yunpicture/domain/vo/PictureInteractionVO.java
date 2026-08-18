package xyz.bbkb.yunpicture.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 当前用户对图片的点赞、收藏状态。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureInteractionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long pictureId;

    /** 当前用户是否已点赞。 */
    private Boolean liked;

    /** 当前用户是否已收藏。 */
    private Boolean favorited;
}
