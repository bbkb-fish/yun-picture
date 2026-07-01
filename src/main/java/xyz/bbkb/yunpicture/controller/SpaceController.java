package xyz.bbkb.yunpicture.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import xyz.bbkb.yunpicture.annotation.AuthCheck;
import xyz.bbkb.yunpicture.common.BaseResponse;
import xyz.bbkb.yunpicture.common.DeleteRequest;
import xyz.bbkb.yunpicture.common.ResultUtils;
import xyz.bbkb.yunpicture.constant.UserConstant;
import xyz.bbkb.yunpicture.domain.dto.space.*;
import xyz.bbkb.yunpicture.domain.entity.Space;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.SpaceVO;
import xyz.bbkb.yunpicture.enums.SpaceLevelEnum;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.service.SpaceService;
import xyz.bbkb.yunpicture.service.UserService;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
public class SpaceController {
    private final UserService userService;
    private final SpaceService spaceService;
//    private final StringRedisTemplate redisTemplate;
//    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
//            .initialCapacity(1024) //  初始容量
//            .maximumSize(10_000) // 最大条数
//            .expireAfterWrite(Duration.ofMinutes(5)) // 过期时间
//            .build();

    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddDTO spaceAddDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddDTO == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long spaceId = spaceService.addSpace(spaceAddDTO, loginUser);
        return ResultUtils.success(spaceId);
    }
    /**
     * 删除空间
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Long id = deleteRequest.getId();
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space==null, ErrorCode.NOT_FOUNT_EORROR);
        User loginUser = userService.getLoginUser(request);
        // 仅本人或管理员可以删除
        if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
            // 操作数据库
            boolean result = spaceService.removeById(space);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
            return ResultUtils.success(true);
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "本人或管理员才能删除");
    }


    /**
     * 更新空间
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateDTO spaceUpdateRequest, HttpServletRequest request) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验
        spaceService.validSpace(space, false); // false代表不是创建工作
        // 判断是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUNT_EORROR);
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取空间（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUNT_EORROR);
        // 获取封装类
        return ResultUtils.success(space);
    }
    /**
     * 根据 id 获取空间（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        User loginUser = userService.getLoginUser(request);
        // 如果查到的不是自己的空间， 报错
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUNT_EORROR);
        // 权限控制，除了自己，其他人应该都不能查到自己的空间数据
        ThrowUtils.throwIf(!Objects.equals(space.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVO(space, request));
    }

    /**
     * 分页获取空间列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryDTO spaceQueryRequest) {
        log.info("管理员分页查询空间：{}", spaceQueryRequest);
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页获取空间列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryDTO spaceQueryRequest,
                                                             HttpServletRequest request) {
        log.info("分页查询空间：{}", spaceQueryRequest);
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }
//    /**
//     * 分页获取空间列表（封装类）
//     */
//    @PostMapping("/list/page/vo/cache")
//    public BaseResponse<Page<SpaceVO>> listSpaceVOByPageWithCache(@RequestBody SpaceQueryDTO spaceQueryRequest,
//                                                             HttpServletRequest request) {
//        log.info("分页查询空间：{}", spaceQueryRequest);
//        long current = spaceQueryRequest.getCurrent();
//        long size = spaceQueryRequest.getPageSize();
//        // 限制爬虫
//        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
//        // 普通用户只能看审核通过的
//        spaceQueryRequest.setReviewStatus(SpaceReviewStatusEnum.ACCEPTED.getStatus());
//        // 查询缓存，缓存没有，再查询数据库
//        String queryCondition = JSONUtil.toJsonStr(spaceQueryRequest);
//        // 转为MD5， 防止查询条件过长浪费空间
//        String hashQuery = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
//        String redisKey = String.format("yunspace:listSpaceVO:%s", hashQuery);
//        // 先看本地缓存有没有
//        String cacheValue = LOCAL_CACHE.getIfPresent(redisKey);
////        String cacheValue =
//        if (cacheValue != null) {
//            // 如果缓存命中，缓存结果
//            Page<SpaceVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
//            return ResultUtils.success(cachePage);
//        }
//        // 再看redis缓存有没有
//        ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
//        cacheValue = opsForValue.get(redisKey);
//        if (cacheValue != null) {
//            // 先存到本地缓存
//            LOCAL_CACHE.put(redisKey, cacheValue);
//            Page<SpaceVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
//            return ResultUtils.success(cachePage);
//        }
//        // 查询数据库
//        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
//                spaceService.getQueryWrapper(spaceQueryRequest));
//        Page<SpaceVO> pageSpaceVO = spaceService.getPageSpaceVO(spacePage, request);
//        // 存进缓存
//        String jsonStr = JSONUtil.toJsonStr(pageSpaceVO);
//        // 写入redis，5-10分钟随机过期，防止缓存血崩
//        redisTemplate.opsForValue().set(redisKey, jsonStr, 300 + RandomUtil.randomInt(0, 300), TimeUnit.SECONDS);
//        // 写入本地
//        LOCAL_CACHE.put(redisKey, jsonStr);
//        // 获取封装类
//        return ResultUtils.success(pageSpaceVO);
//    }

    /**
     * 编辑空间（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditDTO spaceEditRequest, HttpServletRequest request) {
        if (spaceEditRequest == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 自动填充数据
//        spaceService.fillSpaceBySpaceLevel(space);
        // 设置编辑时间
        space.setEditTime(new Date());
        // 数据校验
        spaceService.validSpace(space, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUNT_EORROR);
        // 仅本人或管理员可编辑
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
        return ResultUtils.success(true);
    }

    /**
     * 获取空间级别，便于前端展示
     * @return
     */
    @GetMapping("/list/Level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum ->
                        new SpaceLevel(
                                spaceLevelEnum.getValue(),
                                spaceLevelEnum.getText(),
                                spaceLevelEnum.getMaxCount(),
                                spaceLevelEnum.getMaxSize())
                ).collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }

}
