package xyz.bbkb.yunpicture.domain.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新空间请求
 */
@Data
public class SpaceUpdateDTO implements Serializable {
    private static final long serialVersionUID = -1160164002558366449L;
    private Long id;
    private String spaceName;
    private Integer spaceLevel;
    private Long maxSize;
    private Long maxCount;
}
