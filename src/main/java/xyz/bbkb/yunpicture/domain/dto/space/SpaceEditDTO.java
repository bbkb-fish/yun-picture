package xyz.bbkb.yunpicture.domain.dto.space;

import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceEditDTO implements Serializable {
    private Long id;
    /**
     * 空间名称
     */
    private String spaceName;
    private static final long serialVersionUID = -5284275252104958531L;
}
