package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureDownloadDTO implements Serializable {
    private static final long serialVersionUID = -1899420770284526935L;
    private Long id;
    private String fileName;
}
