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
import org.springframework.web.multipart.MultipartFile;
import xyz.bbkb.yunpicture.annotation.AuthCheck;
import xyz.bbkb.yunpicture.common.BaseResponse;
import xyz.bbkb.yunpicture.common.DeleteRequest;
import xyz.bbkb.yunpicture.common.ResultUtils;
import xyz.bbkb.yunpicture.constant.UserConstant;
import xyz.bbkb.yunpicture.domain.dto.picture.*;
import xyz.bbkb.yunpicture.domain.entity.Picture;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureTagCategory;
import xyz.bbkb.yunpicture.domain.vo.PictureVO;
import xyz.bbkb.yunpicture.enums.PictureReviewStatusEnum;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.service.PictureService;
import xyz.bbkb.yunpicture.service.UserService;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/picture")
@RequiredArgsConstructor
public class PictureController {
    private final UserService userService;
    private final PictureService pictureService;
    private final StringRedisTemplate redisTemplate;
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024) //  初始容量
            .maximumSize(10_000) // 最大条数
            .expireAfterWrite(Duration.ofMinutes(5)) // 过期时间
            .build();
    @PostMapping("/upload")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file")MultipartFile multipartFile,
            @RequestPart(value = "pictureUploadDTO", required = false) PictureUploadDTO pictureUploadDTO,
            HttpServletRequest request
            ) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadDTO, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过url上传图片
     * @param pictureUploadDTO
     * @param request
     * @return
     */
    @PostMapping("/upload/url")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadDTO pictureUploadDTO,
            HttpServletRequest request
    ) {
        log.info("根据url上传或更新图片：{}", pictureUploadDTO);
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(pictureUploadDTO.getFileUrl(), pictureUploadDTO, loginUser);
        return ResultUtils.success(pictureVO);
    }
    /**
     * 删除图片
     * 1. 用户可以删除自己上传的图片
     * 2. 管理员也可以删除图片
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Long id = deleteRequest.getId();
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture==null, ErrorCode.NOT_FOUNT_EORROR);
        User loginUser = userService.getLoginUser(request);
        // 仅本人或管理员可以删除
        if (picture.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
            // 操作数据库
            boolean result = pictureService.removeById(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
            return ResultUtils.success(true);
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "本人或管理员才能删除");
    }


    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateDTO pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUNT_EORROR);
        User loginUser = userService.getLoginUser(request);
        // 补充审核参数
        pictureService.updateOrCreate(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUNT_EORROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        User loginUser = userService.getLoginUser(request);
        // 如果查到的图片未过申， 并且不是自己的图片， 报错
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUNT_EORROR);
        ThrowUtils.throwIf(
                !userService.isAdmin(loginUser)
                && !picture.getReviewStatus().equals(PictureReviewStatusEnum.ACCEPTED.getStatus())
                && picture.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR);
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryDTO pictureQueryRequest) {
        log.info("管理员分页查询图片：{}", pictureQueryRequest);
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryDTO pictureQueryRequest,
                                                             HttpServletRequest request) {
        log.info("分页查询图片：{}", pictureQueryRequest);
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户只能看审核通过的
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.ACCEPTED.getStatus());
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPagePictureVO(picturePage, request));
    }
    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryDTO pictureQueryRequest,
                                                             HttpServletRequest request) {
        log.info("分页查询图片：{}", pictureQueryRequest);
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户只能看审核通过的
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.ACCEPTED.getStatus());
        // 查询缓存，缓存没有，再查询数据库
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        // 转为MD5， 防止查询条件过长浪费空间
        String hashQuery = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String redisKey = String.format("yunpicture:listPictureVO:%s", hashQuery);
        // 先看本地缓存有没有
        String cacheValue = LOCAL_CACHE.getIfPresent(redisKey);
//        String cacheValue =
        if (cacheValue != null) {
            // 如果缓存命中，缓存结果
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        // 再看redis缓存有没有
        ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
        cacheValue = opsForValue.get(redisKey);
        if (cacheValue != null) {
            // 先存到本地缓存
            LOCAL_CACHE.put(redisKey, cacheValue);
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pagePictureVO = pictureService.getPagePictureVO(picturePage, request);
        // 存进缓存
        String jsonStr = JSONUtil.toJsonStr(pagePictureVO);
        // 写入redis，5-10分钟随机过期，防止缓存血崩
        redisTemplate.opsForValue().set(redisKey, jsonStr, 300 + RandomUtil.randomInt(0, 300), TimeUnit.SECONDS);
        // 写入本地
        LOCAL_CACHE.put(redisKey, jsonStr);
        // 获取封装类
        return ResultUtils.success(pagePictureVO);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditDTO pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        pictureService.validPicture(picture);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUNT_EORROR);
        // 仅本人或管理员可编辑
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 补充审核参数
        pictureService.updateOrCreate(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
        return ResultUtils.success(true);
    }
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }
    @PostMapping("/picture/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewPicture(@RequestBody PictureReviewDTO pictureReviewDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewDTO == null, ErrorCode.PARAMS_ERROR);
        User user = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewDTO, user);
        return ResultUtils.success(Boolean.TRUE);
    }

    /**
     * 批量抓取并创建图片
     * @param pictureLoadByBatchDTO
     * @param request
     * @return
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadBatch(@RequestBody PictureLoadByBatchDTO pictureLoadByBatchDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureLoadByBatchDTO == null, ErrorCode.PARAMS_ERROR);
        log.info("批量抓取图片：{}", pictureLoadByBatchDTO);
        User user = userService.getLoginUser(request);
        Integer picNum = pictureService.uploadPictureByBatch(pictureLoadByBatchDTO, user);
        return ResultUtils.success(picNum);
    }
}
