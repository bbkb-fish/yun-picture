package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 从点赞、收藏关系表聚合出的真实计数。
 * 该对象只用于统计校准，不接收前端参数。
 */
@Data
public class PictureInteractionCountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long pictureId;

    private Long likeCount;

    private Long favoriteCount;
}
