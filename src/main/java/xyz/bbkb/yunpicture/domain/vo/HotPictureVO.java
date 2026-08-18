package xyz.bbkb.yunpicture.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 热门图片展示对象。
 *
 * 同时包含图片基础信息、排行榜位置、热度分数和实时统计数据，
 * 前端不需要再为热门列表中的每张图片单独请求统计接口。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotPictureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 图片基础信息。 */
    private PictureVO picture;

    /** 当前排行榜中的名次，从 1 开始。 */
    private Integer rank;

    /** Redis ZSet 中用于排行榜排序的热度分数。 */
    private Double hotScore;

    /** 数据来源：day、week、all 或 latest。 */
    private String rankSource;

    /** 累计浏览次数。 */
    private Long viewCount;

    /** 累计下载次数。 */
    private Long downloadCount;

    /** 累计点赞次数。 */
    private Long likeCount;

    /** 累计收藏次数。 */
    private Long favoriteCount;
}
