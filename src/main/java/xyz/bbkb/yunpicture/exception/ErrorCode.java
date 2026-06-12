package xyz.bbkb.yunpicture.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    success(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUNT_EORROR(40400, "请求的数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION(50001, "操作异常"),
    OPERATION_ERROR(50001, "操作异常"),
    NOT_FOUND_ERROR(40400, "请求的数据不存在")
    ;
    public final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
