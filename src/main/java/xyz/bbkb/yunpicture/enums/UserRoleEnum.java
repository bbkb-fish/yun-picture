package xyz.bbkb.yunpicture.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum UserRoleEnum {
    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     * @param value
     * @return
     */
    public static UserRoleEnum getUserRoleEnum(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
//        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
//            if (userRoleEnum.value.equals(value)) {
//                return userRoleEnum;
//            }
//        }
//        // 没找到
//        return null;
//        优化1：stream流， 在数据量大的时候更快
        return Arrays.stream(values())
                .filter(userRoleEnum -> userRoleEnum.value.equals(value))
                .findFirst()
                .orElse(null);
//        优化2：map （更快）
    }

}
