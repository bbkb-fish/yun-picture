package xyz.bbkb.yunpicture.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;
import xyz.bbkb.yunpicture.annotation.AuthCheck;
import xyz.bbkb.yunpicture.common.BaseResponse;
import xyz.bbkb.yunpicture.common.DeleteRequest;
import xyz.bbkb.yunpicture.common.ResultUtils;
import xyz.bbkb.yunpicture.constant.UserConstant;
import xyz.bbkb.yunpicture.domain.dto.user.*;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.UserLoginVO;
import xyz.bbkb.yunpicture.domain.vo.UserVO;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.service.UserService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    @PostMapping("/register")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterDTO userRegisterDTO) {
        log.info("用户注册：{}", userRegisterDTO);
        return ResultUtils.success(userService.userRegister(userRegisterDTO));
    }

    /**
     * 用户登录
     * @param userLoginDTO
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> userLogin(@RequestBody UserLoginDTO userLoginDTO, HttpServletRequest request) {
        log.info("用户登录: {}", userLoginDTO);
        return ResultUtils.success(userService.userLogin(userLoginDTO, request));
    }

    /**
     * 查询当前用户
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<UserLoginVO> getLoginUser(HttpServletRequest request) {
        log.info("查询当前用户");
        User user = userService.getLoginUser(request);
        return ResultUtils.success(BeanUtil.copyProperties(user, UserLoginVO.class));
    }

    /**
     * 登出
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<String> userLogout(HttpServletRequest request) {
        Boolean result = userService.userLogout(request);
        return ResultUtils.success("登出成功");
    }

    /**
     * 创建用户
     * @param userAddDTO
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddDTO userAddDTO) {
        log.info("添加用户{}",userAddDTO);
        User user = BeanUtil.copyProperties(userAddDTO, User.class);
        String password = userService.getEncryptPassword(UserConstant.DEFAULT_PASSWORD);
        user.setUserPassword(password);

        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
        return ResultUtils.success(user.getId());
    }

    /**
     * 管理员查询用户
     * @param id
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        log.info("根据id查询用户: {}", id);
        User user = userService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUNT_EORROR);
        return ResultUtils.success(user);
    }

    /**
     * 非管理员获取用户信息
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        log.info("根据id查询用户: {}", id);
        User user = userService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUNT_EORROR);
        return ResultUtils.success(BeanUtil.copyProperties(user, UserVO.class));
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateDTO userUpdateDTO) {
        if (userUpdateDTO == null || userUpdateDTO.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateDTO, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
        return ResultUtils.success(true);
    }

    @PostMapping("/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserPageQuery(@RequestBody UserQueryDTO userQueryDTO) {
        ThrowUtils.throwIf(BeanUtil.isEmpty(userQueryDTO), ErrorCode.PARAMS_ERROR);
        long current = userQueryDTO.getCurrent();
        long pageSize = userQueryDTO.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize), userService.getPageQueryWrapper(userQueryDTO));
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }
}
