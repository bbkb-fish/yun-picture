package xyz.bbkb.yunpicture.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum SpaceLevelEnum {

    COMMON("普通版", 0, 100, 100L * 1024 * 1024, 5),
    PROFESSIONAL("专业版", 1, 1000, 1000L * 1024 * 1024, 100),
    FLAGSHIP("旗舰版", 2, 10000, 10000L * 1024 * 1024, -1);

    private final String text;

    private final int value;

    private final long maxCount;

    private final long maxSize;

    /** 每日原图下载上限；-1 表示不限量。 */
    private final int originalDownloadDailyLimit;


    /**
     * @param text 文本
     * @param value 值
     * @param maxCount 最大图片总数量
     * @param maxSize 最大图片总大小
     * @param originalDownloadDailyLimit 每日原图下载上限，-1 表示不限量
     */
    SpaceLevelEnum(String text, int value, long maxCount, long maxSize,
                   int originalDownloadDailyLimit) {
        this.text = text;
        this.value = value;
        this.maxCount = maxCount;
        this.maxSize = maxSize;
        this.originalDownloadDailyLimit = originalDownloadDailyLimit;
    }

    /**
     * 根据 value 获取枚举
     */
    public static SpaceLevelEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (SpaceLevelEnum spaceLevelEnum : SpaceLevelEnum.values()) {
            if (spaceLevelEnum.value == value) {
                return spaceLevelEnum;
            }
        }
        return null;
    }
}

