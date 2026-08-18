package xyz.bbkb.yunpicture.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import xyz.bbkb.yunpicture.mapper.PictureMapper;
import xyz.bbkb.yunpicture.service.PictureHotService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    // 热度服务只需要读取图片，直接依赖 Mapper 可以避免与 PictureService 形成循环依赖。
    private final PictureMapper pictureMapper;
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
        changeStat(pictureId, RedisConstant.VIEW_COUNT, 1L);
        incrementRankScore(pictureId, PictureHotConstant.VIEW_SCORE);
    }

    /**
     * 记录一次下载。下载行为不去重，每次成功下载都增加统计和热度。
     * @param pictureId 图片 ID
     */
    @Override
    public void recordDownload(Long pictureId) {
        getPublicPicture(pictureId);
        changeStat(pictureId, RedisConstant.DOWNLOAD_COUNT, 1L);
        incrementRankScore(pictureId, PictureHotConstant.DOWNLOAD_SCORE);
    }

    /** 点赞关系真正新增后，增加点赞数和排行榜热度。 */
    @Override
    public void recordLike(Long pictureId) {
        getPublicPicture(pictureId);
        changeStat(pictureId, RedisConstant.LIKE_COUNT, 1L);
        incrementRankScore(pictureId, PictureHotConstant.LIKE_SCORE);
    }

    /** 点赞关系真正删除后，扣减点赞数和排行榜热度。 */
    @Override
    public void recordUnlike(Long pictureId) {
        getPublicPicture(pictureId);
        changeStat(pictureId, RedisConstant.LIKE_COUNT, -1L);
        incrementRankScore(pictureId, -PictureHotConstant.LIKE_SCORE);
    }

    /** 收藏关系真正新增后，增加收藏数和排行榜热度。 */
    @Override
    public void recordFavorite(Long pictureId) {
        getPublicPicture(pictureId);
        changeStat(pictureId, RedisConstant.FAVORITE_COUNT, 1L);
        incrementRankScore(pictureId, PictureHotConstant.FAVORITE_SCORE);
    }

    /** 收藏关系真正删除后，扣减收藏数和排行榜热度。 */
    @Override
    public void recordUnfavorite(Long pictureId) {
        getPublicPicture(pictureId);
        changeStat(pictureId, RedisConstant.FAVORITE_COUNT, -1L);
        incrementRankScore(pictureId, -PictureHotConstant.FAVORITE_SCORE);
    }

    /**
     * 使用关系表的真实数量修正 Redis 统计。
     * 排行榜不在这里重算：浏览、下载缺少逐条行为记录，无法准确恢复某天或某周的历史分数。
     */
    @Override
    public boolean reconcileInteractionCounts(Long pictureId, long likeCount, long favoriteCount) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0 || likeCount < 0 || favoriteCount < 0,
                ErrorCode.PARAMS_ERROR, "互动校准参数不合法");
        initializeStatIfNecessary(pictureId);

        String statKey = getStatKey(pictureId);
        Map<Object, Object> currentValues = redisTemplate.opsForHash().entries(statKey);
        long currentLikeCount = readLong(currentValues, RedisConstant.LIKE_COUNT);
        long currentFavoriteCount = readLong(currentValues, RedisConstant.FAVORITE_COUNT);
        if (currentLikeCount == likeCount && currentFavoriteCount == favoriteCount) {
            return false;
        }

        // Hash 的单字段覆盖不会影响同时保存的浏览量、下载量。
        Map<String, String> correctedValues = new LinkedHashMap<>();
        correctedValues.put(RedisConstant.LIKE_COUNT, String.valueOf(likeCount));
        correctedValues.put(RedisConstant.FAVORITE_COUNT, String.valueOf(favoriteCount));
        redisTemplate.opsForHash().putAll(statKey, correctedValues);
        redisTemplate.opsForSet().add(RedisConstant.PICTURE_STAT_DIRTY, pictureId.toString());
        return true;
    }

    @Override
    public void removePictureHotData(Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        String member = pictureId.toString();

        // 删除实时 Hash，并避免尚未处理的 dirty 集合再次把旧统计落库。
        redisTemplate.delete(getStatKey(pictureId));
        redisTemplate.opsForSet().remove(RedisConstant.PICTURE_STAT_DIRTY, member);

        // 当前日榜、周榜和总榜移除即可；旧周期榜会按 TTL 自动过期。
        redisTemplate.opsForZSet().remove(getRankKey(PictureHotConstant.PERIOD_DAY), member);
        redisTemplate.opsForZSet().remove(getRankKey(PictureHotConstant.PERIOD_WEEK), member);
        redisTemplate.opsForZSet().remove(RedisConstant.PICTURE_RANK_ALL, member);
    }

    /**
     * 获取指定周期的热门图片。
     *
     * @param period 排行周期：day、week 或 all
     * @param limit 返回数量，范围 1～60
     * @return 按热度从高到低排列的图片
     */
    @Override
    public List<HotPictureVO> getHotPictures(String period, int limit) {
        ThrowUtils.throwIf(limit <= 0 || limit > PictureHotConstant.MAX_HOT_PICTURE_LIMIT,
                ErrorCode.PARAMS_ERROR, "排行榜数量必须在 1 到 60 之间");

        // 冷启动补位：日榜不足时依次使用周榜、总榜，相同图片只保留第一次出现的位置。
        LinkedHashMap<Long, RankCandidate> candidates = new LinkedHashMap<>();
        for (RankTier rankTier : getRankTiers(period)) {
            appendRankCandidates(candidates, rankTier, limit);
        }

        List<Long> pictureIds = new ArrayList<>(candidates.keySet());

        // 批量查询数据库，并过滤已经删除、转为私有或审核状态发生变化的图片。
        Map<Long, Picture> pictureMap = pictureIds.isEmpty()
                ? Collections.emptyMap()
                : pictureMapper.selectByIds(pictureIds).stream()
                    .filter(this::isPublicPicture)
                    .collect(Collectors.toMap(Picture::getId, picture -> picture));

        // MySQL 的 IN 查询不保证顺序，必须按照 Redis 返回的 ID 顺序重新组装。
        List<HotPictureVO> result = new ArrayList<>(pictureIds.size());
        for (Long pictureId : pictureIds) {
            Picture picture = pictureMap.get(pictureId);
            if (picture != null) {
                RankCandidate candidate = candidates.get(pictureId);
                PictureStatVO stat = readPictureStat(pictureId);
                result.add(new HotPictureVO(
                        PictureVO.objToVO(picture),
                        result.size() + 1,
                        candidate.score,
                        candidate.source,
                        stat.getView_count(),
                        stat.getDownload_count(),
                        stat.getLike_count(),
                        stat.getFavorite_count()
                ));
                if (result.size() >= limit) {
                    return result;
                }
            }
        }

        // Redis 榜单仍不足时，用最新审核通过的公开图片补齐，不写回榜单、不伪造热度。
        int missingCount = limit - result.size();
        if (missingCount > 0) {
            Set<Long> existingIds = result.stream()
                    .map(item -> item.getPicture().getId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            QueryWrapper<Picture> fallbackQuery = new QueryWrapper<Picture>()
                    .isNull("spaceId")
                    .eq("reviewStatus", PictureReviewStatusEnum.ACCEPTED.getStatus())
                    .orderByDesc("createTime")
                    .last("LIMIT " + missingCount);
            if (!existingIds.isEmpty()) {
                fallbackQuery.notIn("id", existingIds);
            }
            for (Picture picture : pictureMapper.selectList(fallbackQuery)) {
                if (!isPublicPicture(picture) || existingIds.contains(picture.getId())) {
                    continue;
                }
                PictureStatVO stat = readPictureStat(picture.getId());
                result.add(new HotPictureVO(
                        PictureVO.objToVO(picture),
                        result.size() + 1,
                        0D,
                        "latest",
                        stat.getView_count(),
                        stat.getDownload_count(),
                        stat.getLike_count(),
                        stat.getFavorite_count()
                ));
                existingIds.add(picture.getId());
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private void appendRankCandidates(Map<Long, RankCandidate> candidates, RankTier rankTier, int limit) {
        Set<ZSetOperations.TypedTuple<String>> rankedItems = redisTemplate.opsForZSet()
                .reverseRangeWithScores(rankTier.key, 0, limit - 1L);
        if (rankedItems == null) {
            return;
        }
        for (ZSetOperations.TypedTuple<String> rankedItem : rankedItems) {
            Long pictureId = parsePictureId(rankedItem.getValue());
            if (pictureId != null) {
                double score = rankedItem.getScore() == null ? 0D : rankedItem.getScore();
                candidates.putIfAbsent(pictureId, new RankCandidate(score, rankTier.source));
            }
        }
    }

    private List<RankTier> getRankTiers(String period) {
        String normalizedPeriod = period == null ? null : period.toLowerCase(Locale.ROOT);
        List<RankTier> tiers = new ArrayList<>();
        tiers.add(new RankTier(getRankKey(period), normalizePeriodSource(normalizedPeriod)));
        if (PictureHotConstant.PERIOD_DAY.equals(normalizedPeriod)
                || PictureHotConstant.PERIOD_DAILY.equals(normalizedPeriod)) {
            tiers.add(new RankTier(getRankKey(PictureHotConstant.PERIOD_WEEK), PictureHotConstant.PERIOD_WEEK));
            tiers.add(new RankTier(getRankKey(PictureHotConstant.PERIOD_ALL), PictureHotConstant.PERIOD_ALL));
        } else if (PictureHotConstant.PERIOD_WEEK.equals(normalizedPeriod)
                || PictureHotConstant.PERIOD_WEEKLY.equals(normalizedPeriod)) {
            tiers.add(new RankTier(getRankKey(PictureHotConstant.PERIOD_ALL), PictureHotConstant.PERIOD_ALL));
        }
        return tiers;
    }

    private String normalizePeriodSource(String period) {
        if (PictureHotConstant.PERIOD_DAILY.equals(period)) {
            return PictureHotConstant.PERIOD_DAY;
        }
        if (PictureHotConstant.PERIOD_WEEKLY.equals(period)) {
            return PictureHotConstant.PERIOD_WEEK;
        }
        return period;
    }

    private static class RankTier {
        private final String key;
        private final String source;

        private RankTier(String key, String source) {
            this.key = key;
            this.source = source;
        }
    }

    private static class RankCandidate {
        private final double score;
        private final String source;

        private RankCandidate(double score, String source) {
            this.score = score;
            this.source = source;
        }
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
     * 原子增减指定统计字段，并将图片标记为待同步。
     * 是否真的发生点赞或收藏变化由 MySQL 关系表的影响行数保证，
     * 因此重复请求不会重复增加或扣减这里的计数。
     */
    private void changeStat(Long pictureId, String field, long delta) {
        initializeStatIfNecessary(pictureId);
        // Redis HINCRBY 是原子操作，并发请求不会互相覆盖。
        redisTemplate.opsForHash().increment(getStatKey(pictureId), field, delta);
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
        Picture picture = pictureMapper.selectById(pictureId);
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
