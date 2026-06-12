package xyz.bbkb.yunpicture.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureTagCategory implements Serializable {
    /**
     * 标签
     */
    private List<String> tagList;
    /**
     * 分类
     */
    private List<String> categoryList;
}
