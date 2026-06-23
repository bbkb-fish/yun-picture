package xyz.bbkb.yunpicture.domain.dto.space;

import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceAddDTO implements Serializable {
    /**
     * 创建的空间名
     */
    private String spaceName;
    /**
     * 创建的空间版本
     * 1- 普通版，2-专业版 3-高级版
     */
    private Integer spaceLevel;
    private static final long serialVersionUID = -1654697135411201271L;
}
