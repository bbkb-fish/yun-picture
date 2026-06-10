package xyz.bbkb.yunpicture.domain.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * 用户注册请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisterDTO implements Serializable {
    private static final long serialVersionUID = 5553131451886527934L;
    private String userAccount;
    private String userPassword;
    private String checkPassword;
}
