package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureUpdateDTO implements Serializable {

    private static final long serialVersionUID = 5164790513696390969L;
    private Long id;
    private String introduction;
    private String category;
    private List<String> tags;
}
