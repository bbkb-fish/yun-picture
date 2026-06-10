package xyz.bbkb.yunpicture.aop;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xyz.bbkb.yunpicture.annotation.AuthCheck;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.enums.UserRoleEnum;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.service.UserService;

import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
@RequiredArgsConstructor
public class AuthAspect {
    private final UserService userService;

    /**
     * 拦截
     * @param pjp
     * @param authCheck
     * @return
     */
    @Around("@annotation(authCheck)")
    public Object doAspect(ProceedingJoinPoint pjp, AuthCheck authCheck) throws Throwable {
        String role = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取当前登录用户
        User user = userService.getLoginUser(request);
        UserRoleEnum mustRole = UserRoleEnum.getUserRoleEnum(role);
        // 无需权限
        if (mustRole == null) {
            return pjp.proceed();
        }
        // 必须有权限
        UserRoleEnum userRole = UserRoleEnum.getUserRoleEnum(user.getUserRole());
        if (userRole == null) {
            return new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 要求管理员权限
        ThrowUtils.throwIf(UserRoleEnum.ADMIN.equals(mustRole) && UserRoleEnum.USER.equals(userRole), ErrorCode.NO_AUTH_ERROR);
        return pjp.proceed();
    }
}
