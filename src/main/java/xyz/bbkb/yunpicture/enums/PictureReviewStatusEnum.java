package xyz.bbkb.yunpicture.enums;

import cn.hutool.core.util.ObjUtil;
import io.swagger.models.auth.In;
import lombok.Data;
import lombok.Getter;

/**
 * 审核状态
 */
@Getter
public enum PictureReviewStatusEnum {
    REVIEWING("待审核", 0),
    ACCEPTED("通过", 1),
    REJECT("拒绝", 2);
    private final String text;
    private final Integer status;
    PictureReviewStatusEnum(String text, Integer status) {
        this.text = text;
        this.status = status;
    }

    /**
     * 根据状态来获取值
     * @param status
     * @return
     */
    public static PictureReviewStatusEnum getEnumByValue(Integer status) {
        if (ObjUtil.isEmpty(status))  {
            return null;
        }
        for (PictureReviewStatusEnum pictureReviewStatusEnum: PictureReviewStatusEnum.values()) {
            if (pictureReviewStatusEnum.status.equals(status)) {
                return pictureReviewStatusEnum;
            }
        }
        return null;
    }
}
