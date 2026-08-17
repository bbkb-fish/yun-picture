package xyz.bbkb.yunpicture.domain.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户修改个人资料的请求参数。
 */
@Data
public class UserProfileUpdateDTO implements Serializable {

    private static final long serialVersionUID = 232615004397051462L;

    private String userName;
    private String userProfile;
}
