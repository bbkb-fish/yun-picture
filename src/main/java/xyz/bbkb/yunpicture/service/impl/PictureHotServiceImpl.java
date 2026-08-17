package xyz.bbkb.yunpicture.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import xyz.bbkb.yunpicture.constant.PictureHotConstant;
import xyz.bbkb.yunpicture.constant.RedisConstant;
import xyz.bbkb.yunpicture.domain.entity.Picture;
import xyz.bbkb.yunpicture.domain.entity.PictureStat;
import xyz.bbkb.yunpicture.domain.vo.HotPictureVO;
import xyz.bbkb.yunpicture.domain.vo.PictureStatVO;
import xyz.bbkb.yunpicture.domain.vo.PictureVO;
import xyz.bbkb.yunpicture.enums.PictureReviewStatusEnum;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.mapper.PictureStatMapper;
import xyz.bbkb.yunpicture.service.PictureHotService;
import xyz.bbkb.yunpicture.service.PictureService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片热度服务实现。
 * 职责分为两部分：
 *   使用 Redis Hash 保存图片的实时浏览、下载、点赞和收藏数量。
 *   使用 Redis ZSet 保存日榜、周榜和总榜的热度分数。
 *   MySQL 中的 picture_stat 是持久数据来源；Redis 没有对应统计时，
 *   会先从 MySQL 加载一次。发生变化的图片 ID 会加入 dirty 集合，
 *   供后续定时任务批量同步回 MySQL。
 */
@Service // 注册为 Spring Bean，其他组件可以注入 PictureHotService 使用
@RequiredArgsConstructor // 为下面所有 final 字段生成构造器，实现构造器注入
public class PictureHotServiceImpl implements PictureHotService {

    private final StringRedisTemplate redisTemplate;
    private final PictureService pictureService;
    private final PictureStatMapper pictureStatMapper;

    /**
     * 记录一次有效浏览。
     *
     * @param pictureId 图片 ID
     * @param viewerId 浏览者唯一标识，登录用户可使用 userId，游客可使用 sessionId
     */
    @Override
    public void recordView(Long pictureId, String viewerId) {
        // 热度榜只统计审核通过的公共图片
        getPublicPicture(pictureId);
        ThrowUtils.throwIf(StrUtil.isBlank(viewerId), ErrorCode.PARAMS_ERROR, "浏览者标识不能为空");

        // 使用 SET NX + 过期时间实现浏览去重：同一用户在 30 分钟内只计算一次。
        String deduplicationKey = RedisConstant.PICTURE_VIEW_DEDUPLICATION + pictureId + ":" + viewerId;
        Boolean firstView = redisTemplate.opsForValue().setIfAbsent(deduplicationKey, "1", PictureHotConstant.VIEW_DEDUPLICATION_TTL);
        if (!Boolean.TRUE.equals(firstView)) {
            return;
        }

        // 同时增加实时浏览量和各周期排行榜的热度分。
        incrementStat(pictureId, RedisConstant.VIEW_COUNT);
        incrementRankScore(pictureId, PictureHotConstant.VIEW_SCORE);
    }

    /**
     * 记录一次下载。下载行为不去重，每次成功下载都增加统计和热度。
     * @param pictureId 图片 ID
     */
    @Override
    public void recordDownload(Long pictureId) {
        getPublicPicture(pictureId);
        incrementStat(pictureId, RedisConstant.DOWNLOAD_COUNT);
        incrementRankScore(pictureId, PictureHotConstant.DOWNLOAD_SCORE);
    }

    /**
     * 获取指定周期的热门图片。
     *
     * @param period 排行周期：day、week 或 all
     * @param limit 返回数量，范围 1～100
     * @return 按热度从高到低排列的图片
     */
    @Override
    public List<HotPictureVO> getHotPictures(String period, int limit) {
        ThrowUtils.throwIf(limit <= 0 || limit > PictureHotConstant.MAX_HOT_PICTURE_LIMIT,
                ErrorCode.PARAMS_ERROR, "排行榜数量必须在 1 到 100 之间");

        String rankKey = getRankKey(period);
        // 同时读取成员和 score，避免为了取得热度分数再逐张查询 ZSet。
        Set<ZSetOperations.TypedTuple<String>> rankedItems = redisTemplate.opsForZSet()
                .reverseRangeWithScores(rankKey, 0, limit - 1L);
        if (rankedItems == null || rankedItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> pictureIds = new ArrayList<>(rankedItems.size());
        Map<Long, Double> scoreMap = new java.util.LinkedHashMap<>();
        for (ZSetOperations.TypedTuple<String> rankedItem : rankedItems) {
            Long pictureId = parsePictureId(rankedItem.getValue());
            if (pictureId != null) {
                pictureIds.add(pictureId);
                scoreMap.put(pictureId, rankedItem.getScore());
            }
        }
        if (pictureIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询数据库，并过滤已经删除、转为私有或审核状态发生变化的图片。
        Map<Long, Picture> pictureMap = pictureService.listByIds(pictureIds).stream()
                .filter(this::isPublicPicture)
                .collect(Collectors.toMap(Picture::getId, picture -> picture));

        // MySQL 的 IN 查询不保证顺序，必须按照 Redis 返回的 ID 顺序重新组装。
        List<HotPictureVO> result = new ArrayList<>(pictureIds.size());
        for (Long pictureId : pictureIds) {
            Picture picture = pictureMap.get(pictureId);
            if (picture != null) {
                PictureStatVO stat = readPictureStat(pictureId);
                result.add(new HotPictureVO(
                        PictureVO.objToVO(picture),
                        result.size() + 1,
                        scoreMap.getOrDefault(pictureId, 0D),
                        stat.getView_count(),
                        stat.getDownload_count(),
                        stat.getLike_count(),
                        stat.getFavorite_count()
                ));
            }
        }
        return result;
    }

    /**
     * 获取单张图片的实时统计，优先读取 Redis。
     *
     * @param pictureId 图片 ID
     * @return 图片实时统计
     */
    @Override
    public PictureStatVO getPictureStat(Long pictureId) {
        getPublicPicture(pictureId);
        return readPictureStat(pictureId);
    }

    /**
     * 从 Redis 读取图片实时统计；缓存不存在时先使用 MySQL 数据初始化。
     */
    private PictureStatVO readPictureStat(Long pictureId) {
        initializeStatIfNecessary(pictureId);

        Map<Object, Object> values = redisTemplate.opsForHash()
                .entries(getStatKey(pictureId));
        return new PictureStatVO(
                pictureId,
                readLong(values, RedisConstant.VIEW_COUNT),
                readLong(values, RedisConstant.DOWNLOAD_COUNT),
                readLong(values, RedisConstant.LIKE_COUNT),
                readLong(values, RedisConstant.FAVORITE_COUNT),
                null
        );
    }

    /**
     * 将指定统计字段原子加一，并将图片标记为待同步。
     */
    private void incrementStat(Long pictureId, String field) {
        initializeStatIfNecessary(pictureId);
        // Redis HINCRBY 是原子操作，并发请求不会互相覆盖。
        redisTemplate.opsForHash().increment(getStatKey(pictureId), field, 1L);
        redisTemplate.opsForSet().add(RedisConstant.PICTURE_STAT_DIRTY, pictureId.toString());
    }

    /**
     * Redis 中没有统计时，从 MySQL 加载初始值。
     * putIfAbsent 可以防止并发初始化覆盖已经产生的计数。
     */
    private void initializeStatIfNecessary(Long pictureId) {
        String statKey = getStatKey(pictureId);
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        if (Boolean.TRUE.equals(hashOperations.hasKey(statKey, RedisConstant.VIEW_COUNT))) {
            return;
        }
        PictureStat pictureStat = pictureStatMapper.selectById(pictureId);

        hashOperations.putIfAbsent(statKey, RedisConstant.VIEW_COUNT,
                toString(pictureStat == null ? null : pictureStat.getViewCount()));
        hashOperations.putIfAbsent(statKey, RedisConstant.DOWNLOAD_COUNT,
                toString(pictureStat == null ? null : pictureStat.getDownloadCount()));
        hashOperations.putIfAbsent(statKey, RedisConstant.LIKE_COUNT,
                toString(pictureStat == null ? null : pictureStat.getLikeCount()));
        hashOperations.putIfAbsent(statKey, RedisConstant.FAVORITE_COUNT,
                toString(pictureStat == null ? null : pictureStat.getFavoriteCount()));
    }

    /**
     * 给当天、当周和总榜同时增加热度分，并设置周期榜过期时间。
     */
    private void incrementRankScore(Long pictureId, double score) {
        LocalDate today = LocalDate.now(PictureHotConstant.BUSINESS_ZONE);
        String member = pictureId.toString();
        String dayKey = RedisConstant.PICTURE_RANK_DAY
                + today.format(DateTimeFormatter.BASIC_ISO_DATE);
        String weekKey = RedisConstant.PICTURE_RANK_WEEK
                + today.get(IsoFields.WEEK_BASED_YEAR)
                + "W"
                + String.format("%02d", today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));

        redisTemplate.opsForZSet().incrementScore(dayKey, member, score);
        redisTemplate.opsForZSet().incrementScore(weekKey, member, score);
        redisTemplate.opsForZSet().incrementScore(RedisConstant.PICTURE_RANK_ALL, member, score);
        redisTemplate.expire(dayKey, PictureHotConstant.DAILY_RANK_TTL);
        redisTemplate.expire(weekKey, PictureHotConstant.WEEKLY_RANK_TTL);
    }

    /**
     * 将接口传入的周期转换成当天实际使用的 Redis Key。
     */
    private String getRankKey(String period) {
        ThrowUtils.throwIf(StrUtil.isBlank(period), ErrorCode.PARAMS_ERROR, "排行榜周期不能为空");
        LocalDate today = LocalDate.now(PictureHotConstant.BUSINESS_ZONE);
        String normalizedPeriod = period.toLowerCase(Locale.ROOT);
        if (PictureHotConstant.PERIOD_DAY.equals(normalizedPeriod)
                || PictureHotConstant.PERIOD_DAILY.equals(normalizedPeriod)) {
            return RedisConstant.PICTURE_RANK_DAY
                    + today.format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        if (PictureHotConstant.PERIOD_WEEK.equals(normalizedPeriod)
                || PictureHotConstant.PERIOD_WEEKLY.equals(normalizedPeriod)) {
            return RedisConstant.PICTURE_RANK_WEEK
                    + today.get(IsoFields.WEEK_BASED_YEAR)
                    + "W"
                    + String.format("%02d", today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        }
        if (PictureHotConstant.PERIOD_ALL.equals(normalizedPeriod)) {
            return RedisConstant.PICTURE_RANK_ALL;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR,
                "排行榜周期仅支持 day、week、all");
    }

    /**
     * 查询并校验图片，确保只有审核通过的公共图片参与统计。
     */
    private Picture getPublicPicture(Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(!isPublicPicture(picture), ErrorCode.NO_AUTH_ERROR,
                "仅公开且审核通过的图片参与热度统计");
        return picture;
    }

    /** 判断图片是否属于审核通过的公共图库。 */
    private boolean isPublicPicture(Picture picture) {
        return picture != null
                && picture.getSpaceId() == null
                && Objects.equals(picture.getReviewStatus(),
                PictureReviewStatusEnum.ACCEPTED.getStatus());
    }

    /** 构造单张图片的 Redis Hash Key。 */
    private String getStatKey(Long pictureId) {
        return RedisConstant.PICTURE_STATUS + pictureId;
    }

    /** 将 Redis 中的字符串 ID 安全转换为 Long，非法成员会被忽略。 */
    private Long parsePictureId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 安全读取 Redis Hash 中的数值字段。 */
    private long readLong(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /** 将可能为空的数据库统计值转换成 Redis 字符串，空值按 0 处理。 */
    private String toString(Long value) {
        return String.valueOf(value == null ? 0L : value);
    }
}
