package xyz.bbkb.yunpicture.domain.dto.space;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.bbkb.yunpicture.common.PageRequest;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceQueryDTO extends PageRequest implements Serializable {
    private Long id;
    private Long userId;
    private String spaceName;
    private Integer spaceLevel;
    private static final long serialVersionUID = -6835724908110923348L;
}
