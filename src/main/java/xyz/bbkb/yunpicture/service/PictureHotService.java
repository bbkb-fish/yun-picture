package xyz.bbkb.yunpicture.service;

import xyz.bbkb.yunpicture.domain.vo.HotPictureVO;
import xyz.bbkb.yunpicture.domain.vo.PictureStatVO;

import java.util.List;

public interface PictureHotService {
    void recordView(Long pictureId, String viewerId);

    void recordDownload(Long pictureId);

    /** 记录一次真实发生的点赞。 */
    void recordLike(Long pictureId);

    /** 记录一次真实发生的取消点赞。 */
    void recordUnlike(Long pictureId);

    /** 记录一次真实发生的收藏。 */
    void recordFavorite(Long pictureId);

    /** 记录一次真实发生的取消收藏。 */
    void recordUnfavorite(Long pictureId);

    /**
     * 使用关系表聚合值校准 Redis 中的点赞数、收藏数。
     *
     * @return 计数发生修正时返回 true
     */
    boolean reconcileInteractionCounts(Long pictureId, long likeCount, long favoriteCount);

    /** 图片删除后移除 Redis 实时统计及当前排行榜成员。 */
    void removePictureHotData(Long pictureId);

    List<HotPictureVO> getHotPictures(String period, int limit);

    PictureStatVO getPictureStat(Long pictureId);
}
