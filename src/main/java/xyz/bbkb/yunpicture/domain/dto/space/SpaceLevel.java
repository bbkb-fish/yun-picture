package xyz.bbkb.yunpicture.domain.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpaceLevel {
    private int value;
    private String text;
    private long maxCount;
    private long maxSize;
    /** 每日原图下载上限，-1 表示不限量。 */
    private int originalDownloadDailyLimit;
}
