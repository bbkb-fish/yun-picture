package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PictureUploadDTO implements Serializable {
    private static final long serialVersionUID = 2033265656999359461L;
    private Long id;
}
