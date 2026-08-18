package xyz.bbkb.yunpicture.constant;

import java.time.Duration;
import java.time.ZoneId;

/**
 * 图片热度业务常量。
 */
public final class PictureHotConstant {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public static final Duration VIEW_DEDUPLICATION_TTL = Duration.ofMinutes(30);
    public static final Duration DAILY_RANK_TTL = Duration.ofDays(8);
    public static final Duration WEEKLY_RANK_TTL = Duration.ofDays(15);

    public static final int MAX_HOT_PICTURE_LIMIT = 60;

    /** 热度统计首次同步延迟 1 分钟，之后每 5 分钟执行一次。 */
    public static final long STAT_SYNC_INITIAL_DELAY_MILLIS = 60 * 1000L;
    public static final long STAT_SYNC_INTERVAL_MILLIS = 5 * 60 * 1000L;

    /** 每天凌晨 3 点在业务低峰校准点赞、收藏计数。 */
    public static final String INTERACTION_RECONCILE_CRON = "0 0 3 * * ?";
    public static final String SCHEDULE_ZONE = "Asia/Shanghai";
    public static final Duration INTERACTION_RECONCILE_LOCK_TTL = Duration.ofMinutes(30);

    public static final double VIEW_SCORE = 1D;
    public static final double DOWNLOAD_SCORE = 5D;
    public static final double LIKE_SCORE = 3D;
    public static final double FAVORITE_SCORE = 8D;

    public static final String PERIOD_DAY = "day";
    public static final String PERIOD_DAILY = "daily";
    public static final String PERIOD_WEEK = "week";
    public static final String PERIOD_WEEKLY = "weekly";
    public static final String PERIOD_ALL = "all";

    private PictureHotConstant() {
    }
}
