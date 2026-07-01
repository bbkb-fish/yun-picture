package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 根据颜色主色调查询图片
 */
@Data
public class SearchPictureByColorDTO implements Serializable {
    /**
     *
     */
    private String picColor;
    private Long spaceId;
    private static final long serialVersionUID = 5589709615635848454L;
}
