package xyz.bbkb.yunpicture.domain.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import xyz.bbkb.yunpicture.common.PageRequest;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserQueryDTO extends PageRequest implements Serializable {
    private static final long serialVersionUID = 7627636700702786280L;
    private Long id;
    private String userName;
    private String userAccount;
    private String userProfile;
    private String userRole;
}
