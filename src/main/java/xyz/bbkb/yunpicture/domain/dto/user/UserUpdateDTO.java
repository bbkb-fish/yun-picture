package xyz.bbkb.yunpicture.domain.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserUpdateDTO implements Serializable {

    private static final long serialVersionUID = 5649515989240838865L;
    private Long id;
    private String userName;
    private String userAccount;
    /**
     * 头像
     */
    private String userAvatar;
    /**
     * 用户简介
     */
    private String userProfile;
    private String userRole;
}
