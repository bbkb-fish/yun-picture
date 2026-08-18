package xyz.bbkb.yunpicture.domain.vo;

import lombok.Data;

import java.io.Serializable;

/** 当前登录用户今天的原图下载额度。 */
@Data
public class OriginalDownloadQuotaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long spaceId;
    private Integer spaceLevel;
    private String levelName;
    private Integer dailyLimit;
    private Integer usedCount;
    private Integer remainingCount;
    private Boolean unlimited;
}
