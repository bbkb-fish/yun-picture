package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.bbkb.yunpicture.common.PageRequest;

import java.io.Serializable;
import java.util.Date;
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
    /**
     * 审核状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 ID
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private Date reviewTime;
    /**
     * 空间id
     */
    private Long spaceId;
    /**
     * 是否查公共图库
     */
    private boolean nullSpaceId;
}
