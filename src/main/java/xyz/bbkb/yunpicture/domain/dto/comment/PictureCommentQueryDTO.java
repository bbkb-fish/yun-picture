package xyz.bbkb.yunpicture.domain.dto.comment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.bbkb.yunpicture.common.PageRequest;

import java.io.Serializable;

/** 图片评论分页请求。查询根评论时传 pictureId，查询回复时传 rootId。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PictureCommentQueryDTO extends PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long pictureId;
    private Long rootId;
}
