package xyz.bbkb.yunpicture.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.bbkb.yunpicture.domain.entity.Picture;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureInteractionVO;
import xyz.bbkb.yunpicture.enums.PictureReviewStatusEnum;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.mapper.PictureFavoriteMapper;
import xyz.bbkb.yunpicture.mapper.PictureLikeMapper;
import xyz.bbkb.yunpicture.mapper.PictureMapper;
import xyz.bbkb.yunpicture.service.PictureHotService;
import xyz.bbkb.yunpicture.service.PictureInteractionService;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片点赞与收藏实现。
 * MySQL 关系表是用户状态的最终依据，Redis 只保存可以重新计算的数量和热度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PictureInteractionServiceImpl implements PictureInteractionService {

    private final PictureMapper pictureMapper;
    private final PictureLikeMapper pictureLikeMapper;
    private final PictureFavoriteMapper pictureFavoriteMapper;
    private final PictureHotService pictureHotService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean likePicture(Long pictureId, User loginUser) {
        validateLoginUser(loginUser);
        validatePublicPicture(pictureId);

        // INSERT IGNORE 配合联合唯一索引实现幂等：重复点赞时 affectedRows 为 0。
        int affectedRows = pictureLikeMapper.insertIgnore(
                IdWorker.getId(), loginUser.getId(), pictureId);
        if (affectedRows == 1) {
            updateHotStatAfterCommit(pictureId, InteractionAction.LIKE);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlikePicture(Long pictureId, User loginUser) {
        validateLoginUser(loginUser);
        validatePictureId(pictureId);

        // 只有真正删除了一条关系才扣减统计，重复取消不会产生负数计数。
        int affectedRows = pictureLikeMapper.deleteRelation(loginUser.getId(), pictureId);
        if (affectedRows == 1) {
            updateHotStatAfterCommit(pictureId, InteractionAction.UNLIKE);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean favoritePicture(Long pictureId, User loginUser) {
        validateLoginUser(loginUser);
        validatePublicPicture(pictureId);

        // 收藏关系同样依靠联合唯一索引保证同一用户只能收藏一次。
        int affectedRows = pictureFavoriteMapper.insertIgnore(
                IdWorker.getId(), loginUser.getId(), pictureId);
        if (affectedRows == 1) {
            updateHotStatAfterCommit(pictureId, InteractionAction.FAVORITE);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfavoritePicture(Long pictureId, User loginUser) {
        validateLoginUser(loginUser);
        validatePictureId(pictureId);

        int affectedRows = pictureFavoriteMapper.deleteRelation(loginUser.getId(), pictureId);
        // 已删除图片的 Redis 热度在删除流程中已经清理，墓碑取消收藏只需删除关系。
        if (affectedRows == 1 && pictureMapper.selectById(pictureId) != null) {
            updateHotStatAfterCommit(pictureId, InteractionAction.UNFAVORITE);
        }
        return true;
    }

    @Override
    public void handlePictureDeleted(Long pictureId) {
        validatePictureId(pictureId);
        // 收藏关系特意保留，用于在“我的收藏”中显示已删除墓碑；点赞关系没有保留价值。
        pictureLikeMapper.deleteByPictureId(pictureId);

        Runnable cleanupTask = () -> {
            try {
                pictureHotService.removePictureHotData(pictureId);
            } catch (Exception exception) {
                // Redis 清理失败不回滚图片删除，排行榜查询仍会过滤已删除图片。
                log.warn("图片 {} 已删除，但 Redis 热度数据清理失败", pictureId, exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupTask.run();
                }
            });
        } else {
            cleanupTask.run();
        }
    }

    @Override
    public PictureInteractionVO getInteraction(Long pictureId, Long userId) {
        validatePictureId(pictureId);
        if (userId == null) {
            return new PictureInteractionVO(pictureId, false, false);
        }
        return getInteractionMap(Collections.singleton(pictureId), userId).get(pictureId);
    }

    @Override
    public Map<Long, PictureInteractionVO> getInteractionMap(Collection<Long> pictureIds, Long userId) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 去重并过滤空 ID，缩短后续 IN 查询参数列表。
        Set<Long> distinctPictureIds = pictureIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctPictureIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, PictureInteractionVO> result = new LinkedHashMap<>();
        for (Long pictureId : distinctPictureIds) {
            result.put(pictureId, new PictureInteractionVO(pictureId, false, false));
        }
        if (userId == null) {
            return result;
        }

        // 一页图片只执行一次点赞查询和一次收藏查询，避免 N+1 查询。
        List<Long> likedPictureIds = pictureLikeMapper.selectLikedPictureIds(userId, distinctPictureIds);
        for (Long likedPictureId : likedPictureIds) {
            PictureInteractionVO interaction = result.get(likedPictureId);
            if (interaction != null) {
                interaction.setLiked(true);
            }
        }

        List<Long> favoritedPictureIds = pictureFavoriteMapper.selectFavoritedPictureIds(userId, distinctPictureIds);
        for (Long favoritedPictureId : favoritedPictureIds) {
            PictureInteractionVO interaction = result.get(favoritedPictureId);
            if (interaction != null) {
                interaction.setFavorited(true);
            }
        }
        return result;
    }

    /** 点赞和收藏只允许作用于审核通过的公共图片。 */
    private void validatePublicPicture(Long pictureId) {
        validatePictureId(pictureId);
        Picture picture = pictureMapper.selectById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(picture.getSpaceId() != null
                        || !Objects.equals(picture.getReviewStatus(), PictureReviewStatusEnum.ACCEPTED.getStatus()),
                ErrorCode.NO_AUTH_ERROR, "只能点赞或收藏审核通过的公开图片");
    }

    private void validatePictureId(Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
    }

    private void validateLoginUser(User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);
    }

    /**
     * 数据库事务提交成功后再更新 Redis，避免数据库回滚但热度已经增加。
     * Redis 是派生统计，偶发失败不应撤销已经成功的用户操作，后续可以由校准任务修复。
     */
    private void updateHotStatAfterCommit(Long pictureId, InteractionAction action) {
        Runnable updateTask = () -> {
            try {
                switch (action) {
                    case LIKE:
                        pictureHotService.recordLike(pictureId);
                        break;
                    case UNLIKE:
                        pictureHotService.recordUnlike(pictureId);
                        break;
                    case FAVORITE:
                        pictureHotService.recordFavorite(pictureId);
                        break;
                    case UNFAVORITE:
                        pictureHotService.recordUnfavorite(pictureId);
                        break;
                    default:
                        break;
                }
            } catch (Exception exception) {
                log.warn("图片 {} 的互动关系已保存，但 Redis 热度更新失败，action={}",
                        pictureId, action, exception);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    updateTask.run();
                }
            });
        } else {
            updateTask.run();
        }
    }

    private enum InteractionAction {
        LIKE,
        UNLIKE,
        FAVORITE,
        UNFAVORITE
    }
}
