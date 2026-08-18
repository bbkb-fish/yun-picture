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
import org.springframework.http.MediaType;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.bbkb.yunpicture.annotation.AuthCheck;
import xyz.bbkb.yunpicture.common.BaseResponse;
import xyz.bbkb.yunpicture.common.DeleteRequest;
import xyz.bbkb.yunpicture.common.ResultUtils;
import xyz.bbkb.yunpicture.common.PageRequest;
import xyz.bbkb.yunpicture.constant.UserConstant;
import xyz.bbkb.yunpicture.domain.dto.picture.*;
import xyz.bbkb.yunpicture.domain.entity.Picture;
import xyz.bbkb.yunpicture.domain.entity.Space;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.HotPictureVO;
import xyz.bbkb.yunpicture.domain.vo.OriginalDownloadQuotaVO;
import xyz.bbkb.yunpicture.domain.vo.PictureStatVO;
import xyz.bbkb.yunpicture.domain.vo.PictureTagCategory;
import xyz.bbkb.yunpicture.domain.vo.PictureVO;
import xyz.bbkb.yunpicture.enums.PictureReviewStatusEnum;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.service.PictureHotService;
import xyz.bbkb.yunpicture.service.PictureInteractionService;
import xyz.bbkb.yunpicture.service.OriginalDownloadQuotaService;
import xyz.bbkb.yunpicture.service.PictureService;
import xyz.bbkb.yunpicture.service.SpaceService;
import xyz.bbkb.yunpicture.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/picture")
@RequiredArgsConstructor
public class PictureController {
    private final UserService userService;
    private final PictureService pictureService;
    private final StringRedisTemplate redisTemplate;
    private final SpaceService spaceService;
    private final PictureHotService pictureHotService;
    private final PictureInteractionService pictureInteractionService;
    private final OriginalDownloadQuotaService originalDownloadQuotaService;
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024) //  初始容量
            .maximumSize(10_000) // 最大条数
            .expireAfterWrite(Duration.ofMinutes(5)) // 过期时间
            .build();

    /**
     * 获取热门图片列表。
     *
     * @param period 排行周期：day、week、all
     * @param limit 返回数量，范围 1～60
     * @return 按热度从高到低排列的图片、排名、热度分数及实时统计
     */
    @GetMapping("/hot")
    public BaseResponse<List<HotPictureVO>> listHotPictures(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(defaultValue = "20") int limit) {
        List<HotPictureVO> hotPictures = pictureHotService.getHotPictures(period, limit);
        return ResultUtils.success(hotPictures);
    }

    /**
     * 获取单张公开图片的实时统计数据。
     *
     * @param pictureId 图片 ID
     */
    @GetMapping("/stat")
    public BaseResponse<PictureStatVO> getPictureStat(@RequestParam Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        return ResultUtils.success(pictureHotService.getPictureStat(pictureId));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
        pictureService.deletePicture(picture.getId(), loginUser);
        return ResultUtils.success(true);
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
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        User loginUser = userService.getLoginUser(request);
        // 如果查到的图片未过申， 并且不是自己的图片， 报错
//        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUNT_EORROR);
//        ThrowUtils.throwIf(
//                !userService.isAdmin(loginUser)
//                && !picture.getReviewStatus().equals(PictureReviewStatusEnum.ACCEPTED.getStatus())
//                && picture.getUserId().equals(loginUser.getId()),
//                ErrorCode.NO_AUTH_ERROR);
        Long spaceId = picture.getSpaceId();
        if(spaceId != null) {
            pictureService.checkPictureAuth(loginUser, picture);
        }
        recordPublicPictureView(picture, loginUser);
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /** 点赞公开图片；重复调用不会重复增加点赞数。 */
    @PostMapping("/like")
    public BaseResponse<Boolean> likePicture(@RequestBody PictureInteractionDTO interactionDTO,
                                             HttpServletRequest request) {
        Long pictureId = getInteractionPictureId(interactionDTO);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.likePicture(pictureId, loginUser));
    }

    /** 取消点赞；原本未点赞时也按成功处理，保证接口幂等。 */
    @PostMapping("/unlike")
    public BaseResponse<Boolean> unlikePicture(@RequestBody PictureInteractionDTO interactionDTO,
                                               HttpServletRequest request) {
        Long pictureId = getInteractionPictureId(interactionDTO);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.unlikePicture(pictureId, loginUser));
    }

    /** 收藏公开图片；重复调用不会重复增加收藏数。 */
    @PostMapping("/favorite")
    public BaseResponse<Boolean> favoritePicture(@RequestBody PictureInteractionDTO interactionDTO,
                                                 HttpServletRequest request) {
        Long pictureId = getInteractionPictureId(interactionDTO);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.favoritePicture(pictureId, loginUser));
    }

    /** 取消收藏；原本未收藏时也按成功处理，保证接口幂等。 */
    @PostMapping("/unfavorite")
    public BaseResponse<Boolean> unfavoritePicture(@RequestBody PictureInteractionDTO interactionDTO,
                                                   HttpServletRequest request) {
        Long pictureId = getInteractionPictureId(interactionDTO);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.unfavoritePicture(pictureId, loginUser));
    }

    /**
     * 分页获取“我的收藏”。用户 ID 始终从登录态获取，不能由前端指定。
     */
    @PostMapping("/favorite/list/page")
    public BaseResponse<Page<PictureVO>> listMyFavoritePictures(@RequestBody PageRequest pageRequest,
                                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(
                pictureService.listMyFavoritePictures(pageRequest, loginUser, request));
    }

    /** 统一校验互动接口请求中的图片 ID。 */
    private Long getInteractionPictureId(PictureInteractionDTO interactionDTO) {
        ThrowUtils.throwIf(interactionDTO == null || interactionDTO.getPictureId() == null
                        || interactionDTO.getPictureId() <= 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        return interactionDTO.getPictureId();
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
        // 兼容旧前端使用 -1 表示公共图库；Objects.equals 避免 null 自动拆箱导致 NPE。
        if (Objects.equals(pictureQueryRequest.getSpaceId(), -1L)) {
            pictureQueryRequest.setSpaceId(null);
            pictureQueryRequest.setNullSpaceId(true);
        }
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

        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库
            // 普通用户只能看审核通过的
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.ACCEPTED.getStatus());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User user = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!user.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPagePictureVO(picturePage, request));
    }
    /**
     * 分页获取图片列表（封装类）
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryDTO pictureQueryRequest,
                                                             HttpServletRequest request) {
        log.info("分页查询图片：{}", pictureQueryRequest);
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户只能看审核通过的
//        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.ACCEPTED.getStatus());
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
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库
            // 普通用户只能看审核通过的
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.ACCEPTED.getStatus());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User user = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!user.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
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
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditDTO pictureEditDTO, HttpServletRequest request) {
        if (pictureEditDTO == null || pictureEditDTO.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditDTO, loginUser);
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


    /**
     * 下载普通图片（优化版）
     */
    @PostMapping("/download/normal")
    public void downloadRemoteImage(@RequestBody PictureDownloadDTO pictureDownloadPictureDTO,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        ThrowUtils.throwIf(pictureDownloadPictureDTO == null, ErrorCode.PARAMS_ERROR);
        log.info("下载普通图片：{}", pictureDownloadPictureDTO);

        Picture picture = pictureService.getById(pictureDownloadPictureDTO.getId());
        if (picture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }

        // 私有空间图片仅允许空间所有者下载，公共图片保持原有访问规则。
        if (picture.getSpaceId() != null) {
            User loginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(loginUser, picture);
        }

        String imageUrl = picture.getUrl();
        String filename = pictureDownloadPictureDTO.getFileName();

        if (pictureService.downloadImage(imageUrl, filename, response)) {
            recordPublicPictureDownload(picture);
        }
    }

    /**
     * 下载高清图片（优化版）
     */
    @PostMapping("/download/high")
    public void downloadRemoteHighDefinitionImage(@RequestBody PictureDownloadDTO pictureDownloadPictureDTO,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        ThrowUtils.throwIf(pictureDownloadPictureDTO == null, ErrorCode.PARAMS_ERROR);
        log.info("下载高清图片：{}", pictureDownloadPictureDTO);

        Picture picture = pictureService.getById(pictureDownloadPictureDTO.getId());
        if (picture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }

        User loginUser = userService.getLoginUser(request);
        // 私有空间原图始终只能由空间所有者下载，空间等级不会放宽图片本身的权限。
        if (picture.getSpaceId() != null) {
            pictureService.checkPictureAuth(loginUser, picture);
        }

        // 优先使用原图地址
        String imageUrl = (picture.getOriginUrl() != null && !picture.getOriginUrl().isEmpty())
                ? picture.getOriginUrl()
                : picture.getUrl();
        String filename = pictureDownloadPictureDTO.getFileName();

        // 写响应前先原子预占额度；远程文件下载失败时立即归还。
        originalDownloadQuotaService.reserveQuota(loginUser);
        boolean downloadSuccess = false;
        try {
            downloadSuccess = pictureService.downloadImage(imageUrl, filename, response);
            if (downloadSuccess) {
                recordPublicPictureDownload(picture);
            }
        } finally {
            // 包括地址为空、远程连接失败和传输中断在内，任何失败都不消耗用户额度。
            if (!downloadSuccess) {
                originalDownloadQuotaService.releaseQuota(loginUser);
            }
        }
    }

    /** 获取当前用户今天的原图下载用量和剩余额度。 */
    @GetMapping("/download/quota")
    public BaseResponse<OriginalDownloadQuotaVO> getOriginalDownloadQuota(
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(originalDownloadQuotaService.getQuota(loginUser));
    }

    /**
     * 根据颜色查询图片
     * @param searchDTO
     * @return
     */
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorDTO searchDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(searchDTO == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchDTO.getPicColor();
        Long spaceId = searchDTO.getSpaceId();
        User user = userService.getLoginUser(request);
        List<PictureVO> pictureVOS = pictureService.searchPictureByColor(spaceId, picColor, user);
        return ResultUtils.success(pictureVOS);
    }

    /**
     * 公开且审核通过的图片才参与热度统计。统计属于辅助能力，
     * Redis 暂时不可用时不能影响图片详情和下载主流程。
     */
    private void recordPublicPictureView(Picture picture, User loginUser) {
        if (!isPublicAcceptedPicture(picture)) {
            return;
        }
        try {
            pictureHotService.recordView(picture.getId(), "user:" + loginUser.getId());
        } catch (Exception exception) {
            log.warn("记录图片 {} 浏览热度失败", picture.getId(), exception);
        }
    }

    private void recordPublicPictureDownload(Picture picture) {
        if (!isPublicAcceptedPicture(picture)) {
            return;
        }
        try {
            pictureHotService.recordDownload(picture.getId());
        } catch (Exception exception) {
            log.warn("记录图片 {} 下载热度失败", picture.getId(), exception);
        }
    }

    private boolean isPublicAcceptedPicture(Picture picture) {
        return picture != null
                && picture.getSpaceId() == null
                && Objects.equals(picture.getReviewStatus(),
                PictureReviewStatusEnum.ACCEPTED.getStatus());
    }

}
