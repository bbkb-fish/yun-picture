package xyz.bbkb.yunpicture.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.bbkb.yunpicture.constant.PictureHotConstant;
import xyz.bbkb.yunpicture.constant.RedisConstant;
import xyz.bbkb.yunpicture.domain.dto.picture.PictureInteractionCountDTO;
import xyz.bbkb.yunpicture.enums.PictureReviewStatusEnum;
import xyz.bbkb.yunpicture.mapper.PictureMapper;
import xyz.bbkb.yunpicture.service.PictureHotService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 点赞、收藏统计校准任务。
 * 关系表是最终事实来源，任务用于修复数据库提交成功但 Redis 更新失败等情况造成的计数偏差。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PictureInteractionReconcileTask {

    /** 仅允许锁持有者删除任务锁，避免误删其他实例后来获得的锁。 */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final PictureMapper pictureMapper;
    private final PictureHotService pictureHotService;

    /** 每天凌晨 3 点执行；分布式锁确保多实例部署时只有一个实例工作。 */
    @Scheduled(
            cron = PictureHotConstant.INTERACTION_RECONCILE_CRON,
            zone = PictureHotConstant.SCHEDULE_ZONE
    )
    public void reconcileInteractionStats() {
        String lockValue = UUID.randomUUID().toString();
        Boolean locked;
        try {
            locked = redisTemplate.opsForValue().setIfAbsent(
                    RedisConstant.PICTURE_INTERACTION_RECONCILE_LOCK,
                    lockValue,
                    PictureHotConstant.INTERACTION_RECONCILE_LOCK_TTL);
        } catch (Exception exception) {
            log.error("获取图片互动统计校准锁失败，本次任务跳过", exception);
            return;
        }
        if (!Boolean.TRUE.equals(locked)) {
            log.info("其他实例正在执行图片互动统计校准，本次任务跳过");
            return;
        }

        try {
            List<PictureInteractionCountDTO> counts = pictureMapper.selectInteractionCounts(
                    PictureReviewStatusEnum.ACCEPTED.getStatus());
            int correctedCount = 0;
            for (PictureInteractionCountDTO count : counts) {
                long likeCount = count.getLikeCount() == null ? 0L : count.getLikeCount();
                long favoriteCount = count.getFavoriteCount() == null ? 0L : count.getFavoriteCount();
                if (pictureHotService.reconcileInteractionCounts(
                        count.getPictureId(), likeCount, favoriteCount)) {
                    correctedCount++;
                }
            }
            log.info("图片互动统计校准完成，检查 {} 张图片，修正 {} 张图片",
                    counts.size(), correctedCount);
        } catch (Exception exception) {
            // 单次失败不影响主业务，等待下一天重试，也可以后续增加管理员手动触发入口。
            log.error("图片互动统计校准失败", exception);
        } finally {
            try {
                redisTemplate.execute(
                        RELEASE_LOCK_SCRIPT,
                        Collections.singletonList(RedisConstant.PICTURE_INTERACTION_RECONCILE_LOCK),
                        lockValue);
            } catch (Exception exception) {
                log.warn("释放图片互动统计校准锁失败，锁会在 TTL 到期后自动失效", exception);
            }
        }
    }
}
