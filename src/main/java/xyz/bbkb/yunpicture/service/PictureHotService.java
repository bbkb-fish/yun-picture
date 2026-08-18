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

    List<HotPictureVO> getHotPictures(String period, int limit);

    PictureStatVO getPictureStat(Long pictureId);
}
