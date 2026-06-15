package xyz.bbkb.yunpicture.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量导入图片请求
 */
@Data
public class PictureLoadByBatchDTO implements Serializable {
    private static final long serialVersionUID = 4491098210166409698L;
    private String searchText;
    private Integer count = 10;
    public String getUrlByBing() {
        return "https://cn.bing.com/images/async?q=" + searchText + "&mmasync=1";
    }

}
