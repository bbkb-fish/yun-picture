package xyz.bbkb.yunpicture.constant;

/**
 * 存放在REDIS中的格式
 */
public interface RedisConstant {
    String prefix = "yunpicture:";

    // 存储到Redis中的热度统计
    String PICTURE_STATUS = prefix +  "picture:stat:";

    // 浏览去重：pictureId:viewerId，30 分钟内只统计一次
    String PICTURE_VIEW_DEDUPLICATION = prefix + "picture:view:dedupe:";

    // 发生过统计变化的图片，用于后续批量同步到 MySQL
    String PICTURE_STAT_DIRTY = prefix + "picture:stat:dirty";
    String PICTURE_STAT_PROCESSING = prefix + "picture:stat:processing:";

    // 点赞收藏校准任务锁，防止多个应用实例同时执行全量校准
    String PICTURE_INTERACTION_RECONCILE_LOCK = prefix + "picture:interaction:reconcile:lock";

    // 图片热度排行榜
    String PICTURE_RANK_DAY = prefix + "picture:rank:day:";
    String PICTURE_RANK_WEEK = prefix + "picture:rank:week:";
    String PICTURE_RANK_ALL = prefix + "picture:rank:all";

    // 统计 Hash 字段
    String VIEW_COUNT = "viewCount";
    String DOWNLOAD_COUNT = "downloadCount";
    String LIKE_COUNT = "likeCount";
    String FAVORITE_COUNT = "favoriteCount";
}
