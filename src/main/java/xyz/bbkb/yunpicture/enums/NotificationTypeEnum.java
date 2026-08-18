package xyz.bbkb.yunpicture.enums;

import lombok.Getter;

/** 用户通知类型。 */
@Getter
public enum NotificationTypeEnum {
    COMMENT("评论通知"),
    PICTURE_REVIEW("审核通知"),
    SPACE_UPGRADE("空间升级"),
    PAYMENT_SUCCESS("支付成功"),
    SYSTEM("系统通知");

    private final String text;

    NotificationTypeEnum(String text) {
        this.text = text;
    }
}
