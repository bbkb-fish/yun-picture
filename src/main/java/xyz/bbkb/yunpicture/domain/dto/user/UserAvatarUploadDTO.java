package xyz.bbkb.yunpicture.domain.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 通过 URL 上传用户头像的请求参数。
 */
@Data
public class UserAvatarUploadDTO implements Serializable {

    private static final long serialVersionUID = 7964500275693983666L;

    private String fileUrl;
}
