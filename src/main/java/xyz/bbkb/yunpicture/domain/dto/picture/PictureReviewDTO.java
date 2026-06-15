package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;


@Data
public class PictureReviewDTO implements Serializable {
    private static final long serialVersionUID = 890863385140715067L;
    private Long id;
    /**
     * 审核状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

}
