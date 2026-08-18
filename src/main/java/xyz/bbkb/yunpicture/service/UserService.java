package xyz.bbkb.yunpicture.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import xyz.bbkb.yunpicture.domain.dto.user.UserLoginDTO;
import xyz.bbkb.yunpicture.domain.dto.user.UserQueryDTO;
import xyz.bbkb.yunpicture.domain.dto.user.UserRegisterDTO;
import xyz.bbkb.yunpicture.domain.entity.User;
import com.baomidou.mybatisplus.spring.service.IService;
import xyz.bbkb.yunpicture.domain.vo.UserLoginVO;
import xyz.bbkb.yunpicture.domain.vo.UserVO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author dearSmile
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-06-10 14:14:28
*/
public interface UserService extends IService<User> {
    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    long userRegister(UserRegisterDTO userRegisterDTO);

    /**
     * 用户登录
     * @param userLoginDTO
     * @return
     */
    UserLoginVO userLogin(UserLoginDTO userLoginDTO, HttpServletRequest request);

    /**
     * 获取当前登录的用户
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取当前登录用户；未登录时返回 null，适用于公开页面补充个性化状态。
     */
    User getLoginUserPermitNull(HttpServletRequest request);

    /**
     * 登出
     * @param request
     * @return
     */
    Boolean userLogout(HttpServletRequest request);

    /**
     * 对密码加密
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    Wrapper<User> getPageQueryWrapper(UserQueryDTO userQueryDTO);

    List<UserVO> getUserVOList(List<User> userList) ;
    UserVO getUserVO(User user) ;
    /**
     * 判断用户是否是管理员
     */
    boolean isAdmin(User user) ;

    /**
     * 上传并更新当前用户的头像。
     *
     * @param inputSource MultipartFile 或图片 URL
     * @param loginUser 当前登录用户
     * @return 新头像地址
     */
    String updateUserAvatar(Object inputSource, User loginUser);
}
