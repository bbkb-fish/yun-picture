package xyz.bbkb.yunpicture.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.bbkb.yunpicture.constant.PictureHotConstant;
import xyz.bbkb.yunpicture.constant.RedisConstant;
import xyz.bbkb.yunpicture.domain.entity.PictureStat;
import xyz.bbkb.yunpicture.mapper.PictureStatMapper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 定时将 Redis 中的图片实时统计同步到 MySQL。
 *
 * 任务开始时先将 dirty 集合原子重命名为本次 processing 集合。
 * 重命名后产生的新统计会进入一个新的 dirty 集合，不会被当前任务误删。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PictureStatSyncTask {

    /**
     * 仅当 dirty Key 存在时执行 RENAME，避免不存在时 Redis 返回错误。
     */
    private static final DefaultRedisScript<Long> MOVE_DIRTY_SET_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('exists', KEYS[1]) == 1 then "
                            + "redis.call('rename', KEYS[1], KEYS[2]); return 1; "
                            + "else return 0; end",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final PictureStatMapper pictureStatMapper;

    /**
     * 首次启动一分钟后执行，之后以上一次执行结束时间为基准，每五分钟执行一次。
     */
    @Scheduled(
            initialDelay = PictureHotConstant.STAT_SYNC_INITIAL_DELAY_MILLIS,
            fixedDelay = PictureHotConstant.STAT_SYNC_INTERVAL_MILLIS
    )
    public void syncPictureStats() {
        //  本次任务专属的key
        String processingKey = RedisConstant.PICTURE_STAT_PROCESSING + UUID.randomUUID();
        // 使用原子转移(lua脚本), 将dirty set的数据转移到processing set
        Long moved = redisTemplate.execute(
                MOVE_DIRTY_SET_SCRIPT,
                java.util.Arrays.asList(RedisConstant.PICTURE_STAT_DIRTY, processingKey)
        );
        // 不存在dirty set数据
        if (!Long.valueOf(1L).equals(moved)) {
            return;
        }
        // 获取processing set 里面所有需要同步的图片id
        Set<String> pictureIds = redisTemplate.opsForSet().members(processingKey);
        // processing set为空,直接删了结束
        if (pictureIds == null || pictureIds.isEmpty()) {
            redisTemplate.delete(processingKey);
            return;
        }

        try {
            // 一张一张更新图片热度
            for (String pictureIdValue : pictureIds) {
                syncOnePicture(Long.valueOf(pictureIdValue));
            }
            // 所有数据落库成功后，才删除本次 processing 集合。
            redisTemplate.delete(processingKey);
            log.info("图片热度统计同步完成，共同步 {} 张图片", pictureIds.size());
        } catch (Exception exception) {
            // 同步失败时放回 dirty，确保下次任务能够重试。
            redisTemplate.opsForSet().add(
                    RedisConstant.PICTURE_STAT_DIRTY,
                    pictureIds.toArray(new String[0])
            );
            redisTemplate.delete(processingKey);
            log.error("图片热度统计同步失败，已重新加入待同步集合", exception);
        }
    }

    /**
     * 读取单张图片在 Redis 中的绝对统计值，并新增或覆盖 MySQL 记录。
     */
    private void syncOnePicture(Long pictureId) {
        // 获取单张图片在redis里面的key
        String statKey = RedisConstant.PICTURE_STATUS + pictureId;
        // 从redis里面获取图片信息
        Map<Object, Object> values = redisTemplate.opsForHash().entries(statKey);
        // 没查到
        if (values.isEmpty()) {
            log.warn("图片 {} 的 Redis 统计不存在，跳过本次同步", pictureId);
            return;
        }
        // 更新数据,没有这个就创建一个新的
        PictureStat pictureStat = new PictureStat();
        pictureStat.setPictureId(pictureId);
        pictureStat.setViewCount(readLong(values, RedisConstant.VIEW_COUNT));
        pictureStat.setDownloadCount(readLong(values, RedisConstant.DOWNLOAD_COUNT));
        pictureStat.setLikeCount(readLong(values, RedisConstant.LIKE_COUNT));
        pictureStat.setFavoriteCount(readLong(values, RedisConstant.FAVORITE_COUNT));
        pictureStatMapper.upsert(pictureStat);
    }

    private long readLong(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            log.warn("Redis 图片统计字段 {} 的值 {} 不是有效整数，按 0 处理", field, value);
            return 0L;
        }
    }
}
