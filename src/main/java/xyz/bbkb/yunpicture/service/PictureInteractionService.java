package xyz.bbkb.yunpicture.service;

import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureInteractionVO;

import java.util.Collection;
import java.util.Map;

/** 图片点赞与收藏业务。 */
public interface PictureInteractionService {

    boolean likePicture(Long pictureId, User loginUser);

    boolean unlikePicture(Long pictureId, User loginUser);

    boolean favoritePicture(Long pictureId, User loginUser);

    boolean unfavoritePicture(Long pictureId, User loginUser);

    /** 图片删除后清理点赞关系和热度数据；收藏关系作为删除历史保留。 */
    void handlePictureDeleted(Long pictureId);

    PictureInteractionVO getInteraction(Long pictureId, Long userId);

    /**
     * 批量查询当前用户对多张图片的状态，避免列表页逐张访问数据库。
     */
    Map<Long, PictureInteractionVO> getInteractionMap(Collection<Long> pictureIds, Long userId);
}
