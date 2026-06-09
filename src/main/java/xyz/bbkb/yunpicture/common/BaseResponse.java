package xyz.bbkb.yunpicture.common;

import lombok.Data;
import xyz.bbkb.yunpicture.exception.ErrorCode;

import java.io.Serializable;

/**
 * 全局响应
 * @param <T>
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(int code) {
        this(code, null, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
