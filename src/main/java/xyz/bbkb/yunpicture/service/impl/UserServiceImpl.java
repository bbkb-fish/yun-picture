package xyz.bbkb.yunpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.DigestUtils;
import xyz.bbkb.yunpicture.constant.UserConstant;
import xyz.bbkb.yunpicture.domain.dto.user.UserLoginDTO;
import xyz.bbkb.yunpicture.domain.dto.user.UserQueryDTO;
import xyz.bbkb.yunpicture.domain.dto.user.UserRegisterDTO;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.UserLoginVO;
import xyz.bbkb.yunpicture.domain.vo.UserVO;
import xyz.bbkb.yunpicture.enums.UserRoleEnum;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.service.UserService;
import xyz.bbkb.yunpicture.mapper.UserMapper;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author dearSmile
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2026-06-10 14:14:28
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService{

    /**
     *  用户注册
     * @param userRegisterDTO
     * @return
     */
    @Override
    public long userRegister(UserRegisterDTO userRegisterDTO) {
        // 1. 校验参数
        if (StrUtil.isBlank(userRegisterDTO.getUserAccount()) || StrUtil.isBlank(userRegisterDTO.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userRegisterDTO.getUserAccount().length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号长度不能小于4");
        }
        if (userRegisterDTO.getUserPassword().length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于8");
        }
        if (!userRegisterDTO.getUserPassword().equals(userRegisterDTO.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        // 2. 检查用户账号是否喝数据库已有的重复
        Long count = this.baseMapper.selectCount(new QueryWrapper<User>().eq("userAccount", userRegisterDTO.getUserAccount()));
        if(count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已存在");
        }
        // 3. 密码一定要加密
        String encryptPassword = getEncryptPassword(userRegisterDTO.getUserPassword());
        // 4. 插入数据到数据库中
        User user = new User();
        user.setUserAccount(userRegisterDTO.getUserAccount());
        user.setUserPassword(encryptPassword);
        user.setUserName("哈基米");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    /**
     * 用户登录逻辑
     * @param userLoginDTO
     * @return
     */
    @Override
    public UserLoginVO userLogin(UserLoginDTO userLoginDTO, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userLoginDTO.getUserAccount(), userLoginDTO.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userLoginDTO.getUserAccount().length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号长度不能小于4");
        }
        if (userLoginDTO.getUserPassword().length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于8");
        }
        // 2. 加密
        String encryptPassword = getEncryptPassword(userLoginDTO.getUserPassword());
        // 3. 查询数据库的用户是否存在
        User user = this.baseMapper.selectOne(new QueryWrapper<User>().eq("userAccount", userLoginDTO.getUserAccount()).eq("userPassword", encryptPassword));
        // 不存在，抛异常
        if (BeanUtil.isEmpty(user)) {
            log.info("user login failed, userAccount or userPassword is error");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        // 4. 保存用户登录状态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        return BeanUtil.copyProperties(user, UserLoginVO.class);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 判断是否登录
        User user = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        ThrowUtils.throwIf(BeanUtil.isEmpty(user) || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 从数据库中查询，如果追求性能则可以注释
        Long userId = user.getId();
        User currentUser = this.getById(userId);
        if(BeanUtil.isEmpty(currentUser)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public Boolean userLogout(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        ThrowUtils.throwIf(BeanUtil.isEmpty(user) || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取加密后的密码
     * @param userPassword
     * @return
     */
    public String getEncryptPassword(String userPassword) {
        // 加盐, 混淆密码
        final String SALT = "bbkb";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public Wrapper<User> getPageQueryWrapper(UserQueryDTO userQueryDTO) {
        if (userQueryDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryDTO.getId();
        String userAccount = userQueryDTO.getUserAccount();
        String userName = userQueryDTO.getUserName();
        String userProfile = userQueryDTO.getUserProfile();
        String userRole = userQueryDTO.getUserRole();
        String sortField = userQueryDTO.getSortField();
        String sortOrder = userQueryDTO.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

}




