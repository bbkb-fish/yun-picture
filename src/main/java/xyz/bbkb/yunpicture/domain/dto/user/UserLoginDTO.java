package xyz.bbkb.yunpicture.domain.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginDTO implements Serializable {

    private static final long serialVersionUID = -4234437055321543895L;
    private String userAccount;
    private String userPassword;
}
