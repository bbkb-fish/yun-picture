package xyz.bbkb.yunpicture.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginVO implements Serializable {

    private static final long serialVersionUID = 7630023003624698612L;

    private String userName;
    private Long id;
    private String userAccount;
    private Date createTime;
    private String userAvatar;
    private String userRole;
    private Date editTime;
    private Date updateTime;
}
