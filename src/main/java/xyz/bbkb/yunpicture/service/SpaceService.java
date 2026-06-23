package xyz.bbkb.yunpicture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import xyz.bbkb.yunpicture.domain.dto.space.SpaceAddDTO;
import xyz.bbkb.yunpicture.domain.dto.space.SpaceQueryDTO;
import xyz.bbkb.yunpicture.domain.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author dearSmile
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-06-16 17:12:08
*/
public interface SpaceService extends IService<Space> {
    /**
     * 用户创建空间
     * @param spaceAddDTO
     * @param loginUser
     * @return
     */
    Long addSpace(SpaceAddDTO spaceAddDTO, User loginUser) ;
    /**
     * 校验空间
     * @param space
     * @param add 是否为创建时校验
     */
    void validSpace(Space space, boolean add);

    /**
     * 获取空间包装类
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取查询条件
     * @param spaceQueryDTO
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryDTO spaceQueryDTO);

    /**
     * 根据空间级别填充空间对象
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 创建空间，事务
     * @param userId
     * @param space
     * @return
     */
    Long tryAddSpace(Long userId, Space space);
}
