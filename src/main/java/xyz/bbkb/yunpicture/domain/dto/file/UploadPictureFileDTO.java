package xyz.bbkb.yunpicture.domain.dto.file;

import lombok.Data;

/**
 * 上传图片的结果
 */
@Data
public class UploadPictureFileDTO {
    private String picName;
    private Long picSize;
    private int picWidth;
    private int picHeight;
    private Double picScale;
    private String url;
    private String originUrl;
    /**
     * 缩略图url
     */
    private String thumbnailUrl;
    private String picFormat;
}
