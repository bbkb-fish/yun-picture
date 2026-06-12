package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.bbkb.yunpicture.common.PageRequest;

import java.io.Serializable;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PictureQueryDTO extends PageRequest implements Serializable {

    private static final long serialVersionUID = 5469619701861263536L;
    private Long id;
    private String name;
    private String introduction;
    private String category;
    private List<String> tags;
    private Integer picWidth;
    private Integer picHeight;
    private Long picSize;
    private Double picScale;
    private String picFormat;
    private String searchText;
    private Long userId;

}
