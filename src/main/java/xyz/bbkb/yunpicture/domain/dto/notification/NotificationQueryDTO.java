package xyz.bbkb.yunpicture.domain.dto.notification;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.bbkb.yunpicture.common.PageRequest;

import java.io.Serializable;

/** 当前登录用户的通知分页条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQueryDTO extends PageRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 可选：true 只看未读，false 只看已读，null 查看全部。 */
    private Boolean unreadOnly;
}
