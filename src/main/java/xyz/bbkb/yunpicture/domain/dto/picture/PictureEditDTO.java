package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片编辑
 */
@Data
public class PictureEditDTO implements Serializable {

    private static final long serialVersionUID = 1481565723051911144L;
    private Long id;
    private String name;
    private String introduction;
    private String category;
    private List<String> tags;

}
