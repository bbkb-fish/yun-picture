package xyz.bbkb.yunpicture.common;

import lombok.Data;

/**
 * 通用的分类请求
 */
@Data
public class PageRequest {
    private int current = 1;
    private int pageSize = 10;
    private String sortField;
    private String sortOrder = "descend";
}
