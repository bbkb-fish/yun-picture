package xyz.bbkb.yunpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.bbkb.yunpicture.domain.dto.file.UploadPictureFileDTO;
import xyz.bbkb.yunpicture.common.PageRequest;
import xyz.bbkb.yunpicture.domain.dto.picture.*;
import xyz.bbkb.yunpicture.domain.entity.Picture;
import xyz.bbkb.yunpicture.domain.entity.Space;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureVO;
import xyz.bbkb.yunpicture.domain.vo.PictureInteractionVO;
import xyz.bbkb.yunpicture.domain.vo.UserVO;
import xyz.bbkb.yunpicture.enums.PictureReviewStatusEnum;
import xyz.bbkb.yunpicture.enums.NotificationTypeEnum;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.manager.CosManager;
import xyz.bbkb.yunpicture.manager.upload.FilePictureUpload;
import xyz.bbkb.yunpicture.manager.upload.PictureUploadTemplate;
import xyz.bbkb.yunpicture.manager.upload.UrlPictureUpload;
import xyz.bbkb.yunpicture.service.PictureService;
import xyz.bbkb.yunpicture.service.PictureInteractionService;
import xyz.bbkb.yunpicture.service.NotificationService;
import xyz.bbkb.yunpicture.mapper.PictureMapper;
import xyz.bbkb.yunpicture.mapper.PictureFavoriteMapper;
import org.springframework.stereotype.Service;
import xyz.bbkb.yunpicture.service.UserService;
import xyz.bbkb.yunpicture.utils.PictureUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static xyz.bbkb.yunpicture.utils.PictureUtil.calculateSimilarity;


/**
* @author dearSmile
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-06-12 16:40:34
*/
@Service
@Slf4j
@RequiredArgsConstructor
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{
//    private final FileManger fileManger;
    private final UserService userService;
    private final FilePictureUpload filePictureUpload;
    private final UrlPictureUpload urlPictureUpload;
    private final CosManager cosManager;
    private final SpaceServiceImpl spaceService;
    private final PictureInteractionService pictureInteractionService;
    private final PictureFavoriteMapper pictureFavoriteMapper;
    private final NotificationService notificationService;

    /**
     * 数据校验
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doPictureReview(PictureReviewDTO pictureReviewDTO, User user) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewDTO == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewDTO.getId();
        PictureReviewStatusEnum pictureReviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(pictureReviewDTO.getReviewStatus());
        ThrowUtils.throwIf(pictureReviewStatusEnum == null || id == null || PictureReviewStatusEnum.REVIEWING.equals(pictureReviewStatusEnum), ErrorCode.PARAMS_ERROR);
        // 2. 图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.PARAMS_ERROR);
        // 3. 校验审核状态是否存在
        ThrowUtils.throwIf(Objects.equals(oldPicture.getReviewStatus(), pictureReviewStatusEnum.getStatus()), ErrorCode.PARAMS_ERROR, "审核状态重复");
        // 4. 操作数据库
        Picture picture = BeanUtil.copyProperties(pictureReviewDTO, Picture.class);

        picture.setReviewerId(user.getId());
        picture.setReviewTime(new Date());
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        if (!Objects.equals(oldPicture.getUserId(), user.getId())) {
            boolean accepted = PictureReviewStatusEnum.ACCEPTED.equals(pictureReviewStatusEnum);
            String content = accepted
                    ? "你的图片《" + oldPicture.getName() + "》已通过审核"
                    : "你的图片《" + oldPicture.getName() + "》未通过审核"
                    + (StrUtil.isBlank(pictureReviewDTO.getReviewMessage())
                    ? "" : "：" + pictureReviewDTO.getReviewMessage());
            notificationService.createNotification(
                    oldPicture.getUserId(),
                    NotificationTypeEnum.PICTURE_REVIEW,
                    accepted ? "图片审核通过" : "图片审核未通过",
                    content,
                    "PICTURE",
                    oldPicture.getId(),
                    "PICTURE_REVIEW:" + oldPicture.getId() + ":" + pictureReviewStatusEnum.getStatus());
        }
    }

    @Override
    @Transactional
    public PictureVO uploadPicture(Object inputSource, PictureUploadDTO pictureUploadDTO, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        if (pictureUploadDTO == null) {
            pictureUploadDTO = new PictureUploadDTO();
        }

        Long pictureId = pictureUploadDTO.getId();
        Long spaceId = pictureUploadDTO.getSpaceId();
        Picture oldPicture = null;

        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUNT_EORROR, "图片不存在");
            this.checkPictureAuth(loginUser, oldPicture);
            if (spaceId == null) {
                spaceId = oldPicture.getSpaceId();
            } else if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能修改图片所属空间");
            }
        }

        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            ThrowUtils.throwIf(!Objects.equals(loginUser.getId(), space.getUserId()),
                    ErrorCode.NO_AUTH_ERROR, "无空间权限");
        }

        String uploadPathPrefix = spaceId == null
                ? String.format("public/%s", loginUser.getId())
                : String.format("space/%s", spaceId);
        PictureUploadTemplate pictureUploadTemplate = inputSource instanceof String
                ? urlPictureUpload : filePictureUpload;
        UploadPictureFileDTO uploadPictureFileInfo = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        Picture picture = BeanUtil.copyProperties(uploadPictureFileInfo, Picture.class);
        picture.setUserId(loginUser.getId());
        String picName = uploadPictureFileInfo.getPicName();
        if (StrUtil.isNotBlank(pictureUploadDTO.getPicName())) {
            picName = pictureUploadDTO.getPicName();
        }
        picture.setName(picName);
        if (CollUtil.isNotEmpty(pictureUploadDTO.getTags())) {
            picture.setTags(JSONUtil.toJsonStr(pictureUploadDTO.getTags()));
        }
        picture.setSpaceId(spaceId);
        this.updateOrCreate(picture, loginUser);

        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        try {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION, "数据库出错，图片上传失败");

            if (spaceId != null) {
                long oldSize = oldPicture == null || oldPicture.getPicSize() == null ? 0L : oldPicture.getPicSize();
                long newSize = picture.getPicSize() == null ? 0L : picture.getPicSize();
                updateSpaceUsage(spaceId, newSize - oldSize, oldPicture == null ? 1L : 0L);
            }
        } catch (RuntimeException e) {
            // COS 不参与数据库事务，数据库操作失败时主动清理本次新上传的文件。
            deletePictureFiles(picture);
            throw e;
        }

        if (oldPicture != null) {
            clearPictureFileAfterCommit(oldPicture);
        }
        return PictureVO.objToVO(picture);
    }

    private void updateSpaceUsage(Long spaceId, long sizeDelta, long countDelta) {
        if (spaceId == null || (sizeDelta == 0 && countDelta == 0)) {
            return;
        }
        var update = spaceService.lambdaUpdate().eq(Space::getId, spaceId);
        if (sizeDelta > 0) {
            update.apply("totalSize + {0} <= maxSize", sizeDelta);
        } else if (sizeDelta < 0) {
            update.ge(Space::getTotalSize, -sizeDelta);
        }
        if (countDelta > 0) {
            update.apply("totalCount + {0} <= maxCount", countDelta);
        } else if (countDelta < 0) {
            update.ge(Space::getTotalCount, -countDelta);
        }
        if (sizeDelta != 0) {
            update.setSql("totalSize = totalSize + (" + sizeDelta + ")");
        }
        if (countDelta != 0) {
            update.setSql("totalCount = totalCount + (" + countDelta + ")");
        }
        ThrowUtils.throwIf(!update.update(), ErrorCode.OPERATION_ERROR, "空间额度不足或空间不存在");
    }


    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryDTO pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();

        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId"); // 公共图库
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // 必须是审核通过的才可以让普通用户看到
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus),"reviewStatus", reviewStatus);
        // 审核人查询
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        PictureVO pictureVO = PictureVO.objToVO(picture);
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        // 详情页通过联合索引读取当前用户状态；未登录用户直接得到 false。
        User loginUser = userService.getLoginUserPermitNull(request);
        PictureInteractionVO interaction = pictureInteractionService.getInteraction(
                picture.getId(), loginUser == null ? null : loginUser.getId());
        pictureVO.setLiked(interaction.getLiked());
        pictureVO.setFavorited(interaction.getFavorited());
        return pictureVO;
    }

    @Override
    public Page<PictureVO> getPagePictureVO(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(picture -> {
            PictureVO pictureVO = PictureVO.objToVO(picture);
            boolean deleted = Objects.equals(picture.getIsDelete(), 1);
            pictureVO.setDeleted(deleted);
            if (deleted) {
                // COS 文件会在图片删除后清理，墓碑响应不得继续暴露失效文件地址。
                pictureVO.setUrl(null);
                pictureVO.setThumbnailUrl(null);
            }
            return pictureVO;
        }).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        // 对整页图片批量查询两张关系表，固定为两次查询，避免逐张查询造成 N+1。
        User loginUser = userService.getLoginUserPermitNull(request);
        Map<Long, PictureInteractionVO> interactionMap = pictureInteractionService.getInteractionMap(
                pictureList.stream().map(Picture::getId).collect(Collectors.toList()),
                loginUser == null ? null : loginUser.getId());
        pictureVOList.forEach(pictureVO -> {
            PictureInteractionVO interaction = interactionMap.get(pictureVO.getId());
            if (interaction != null) {
                pictureVO.setLiked(interaction.getLiked());
                pictureVO.setFavorited(interaction.getFavorited());
            }
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public Page<PictureVO> listMyFavoritePictures(PageRequest pageRequest,
                                                  User loginUser,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(pageRequest == null, ErrorCode.PARAMS_ERROR, "分页参数不能为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);
        int current = pageRequest.getCurrent();
        int pageSize = pageRequest.getPageSize();
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0 || pageSize > 20,
                ErrorCode.PARAMS_ERROR, "每页数量必须在 1 到 20 之间");

        // 联表查询已经完成可见性过滤和收藏时间排序，再复用统一 VO 封装补充作者、点赞状态。
        Page<Picture> favoritePage = new Page<>(current, pageSize);
        pictureFavoriteMapper.selectFavoritePicturePage(
                favoritePage,
                loginUser.getId(),
                PictureReviewStatusEnum.ACCEPTED.getStatus());
        return getPagePictureVO(favoritePage, request);
    }

    @Override
    public void updateOrCreate(Picture picture, User loginUser) {
        String message = null;
        PictureReviewStatusEnum pictureReviewStatusEnum = PictureReviewStatusEnum.REVIEWING;
        if (userService.isAdmin(loginUser)) {
            message = "管理员自动过审";
            pictureReviewStatusEnum = PictureReviewStatusEnum.ACCEPTED;
        }
        this.fillReviewParams(picture, loginUser, pictureReviewStatusEnum, message);
    }

    /**
     * 填充审核参数
     * @param picture
     * @param loginUser
     * @param pictureReviewStatusEnum
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser, PictureReviewStatusEnum pictureReviewStatusEnum, String message) {
        if (userService.isAdmin(loginUser)) {
            picture.setReviewStatus(pictureReviewStatusEnum.getStatus());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage(message);
            picture.setReviewTime(new Date());
            return;
        }
        ThrowUtils.throwIf(!PictureReviewStatusEnum.REVIEWING.equals(pictureReviewStatusEnum),
                ErrorCode.NO_AUTH_ERROR);
        picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getStatus());
    }

    @Override
    public Integer uploadPictureByBatch(PictureLoadByBatchDTO pictureLoadByBatchDTO, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(pictureLoadByBatchDTO.getCount() > 30, ErrorCode.PARAMS_ERROR, "最多传30张");

        // 抓取内容
        String fetchUrl = pictureLoadByBatchDTO.getUrlByBing();
        Document document = null;
        try {
            document = Jsoup.connect(fetchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();
        } catch (IOException e) {
            log.error("获取网络页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取网络页面失败");
        }

        // 根据 initPic 的值选择不同的解析方式
        // initPic = 0: 获取缩略图
        // initPic = 1: 获取高清原图
        boolean isHighQuality = pictureLoadByBatchDTO.getInitPic() == 1;

        int uploadCount = 0;
        int requestCount = 0;

        if (isHighQuality) {
            // ========== 高清原图模式 ==========
            uploadCount = uploadHighQualityImages(document, pictureLoadByBatchDTO, loginUser, requestCount, uploadCount);
        } else {
            // ========== 缩略图模式（原有逻辑）==========
            uploadCount = uploadThumbnailImages(document, pictureLoadByBatchDTO, loginUser, requestCount, uploadCount);
        }

        log.info("批量上传完成，共上传 {} 张图片，共请求 {} 次，模式：{}",
                uploadCount, requestCount, isHighQuality ? "高清原图" : "缩略图");
        return uploadCount;
    }

    /**
     * 上传高清原图
     */
    private Integer uploadHighQualityImages(Document document, PictureLoadByBatchDTO pictureLoadByBatchDTO,
                                            User loginUser, int requestCount, int uploadCount) {
        // 解析内容 - 通过 .iusc 的 m 属性获取原图地址
        Elements imageItems = document.select(".iusc");
        if (imageItems.isEmpty()) {
            // 备选选择器
            imageItems = document.select(".imgpt .iusc");
        }

        for (Element item : imageItems) {
            requestCount++;
            if (requestCount > pictureLoadByBatchDTO.getCount() * 2) break;

            String fileUrl = null;

            // 获取原始图片URL - 从 m 属性中解析
            String mAttr = item.attr("m");
            if (StrUtil.isNotBlank(mAttr)) {
                try {
                    // m 属性是 JSON 格式，包含 murl（原图地址）
                    com.alibaba.fastjson2.JSONObject jsonObj = com.alibaba.fastjson2.JSON.parseObject(mAttr);
                    fileUrl = jsonObj.getString("murl");
                    if (StrUtil.isBlank(fileUrl)) {
                        // 如果 murl 没有，尝试取 turl
                        fileUrl = jsonObj.getString("turl");
                    }
                } catch (Exception e) {
                    log.error("解析 m 属性失败: {}", mAttr, e);
                }
            }

            // 如果通过 m 属性没有获取到，尝试获取 src 属性
            if (StrUtil.isBlank(fileUrl)) {
                Element img = item.select("img").first();
                if (img != null) {
                    fileUrl = img.attr("src");
                }
            }

            if (StrUtil.isBlank(fileUrl)) {
                log.info("获取高清图URL失败，跳过第 {} 个", requestCount);
                continue;
            }

            // 上传图片
            try {
                PictureUploadDTO pictureUploadDTO = new PictureUploadDTO();
                pictureUploadDTO.setFileUrl(fileUrl);
                pictureUploadDTO.setPicName(pictureLoadByBatchDTO.getSearchText());
                pictureUploadDTO.setTags(Collections.singletonList(pictureLoadByBatchDTO.getSearchText()));
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadDTO, loginUser);
                log.info("高清图片上传成功：{}，原图地址：{}", pictureVO, fileUrl);
                uploadCount++;
            } catch (Exception e) {
                log.error("高清图片上传失败，URL: {}", fileUrl, e);
                continue;
            }

            if (uploadCount >= pictureLoadByBatchDTO.getCount()) break;
        }

        return uploadCount;
    }

    /**
     * 上传缩略图（原有逻辑）
     */
    private Integer uploadThumbnailImages(Document document, PictureLoadByBatchDTO pictureLoadByBatchDTO,
                                          User loginUser, int requestCount, int uploadCount) {
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }

        Elements lists = div.select("img.mimg");

        for (Element list : lists) {
            requestCount++;
            if (requestCount > pictureLoadByBatchDTO.getCount() * 2) break;

            String fileUrl = list.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("获取缩略图URL失败");
                continue;
            }

            // 处理地址：去除 URL 后面的参数
            int questionIndex = fileUrl.indexOf("?");
            if (questionIndex > -1) {
                fileUrl = fileUrl.substring(0, questionIndex);
            }

            // 上传图片
            try {
                PictureUploadDTO pictureUploadDTO = new PictureUploadDTO();
                pictureUploadDTO.setFileUrl(fileUrl);
                pictureUploadDTO.setPicName(pictureLoadByBatchDTO.getSearchText());
                pictureUploadDTO.setTags(Collections.singletonList(pictureLoadByBatchDTO.getSearchText()));
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadDTO, loginUser);
                log.info("缩略图上传成功：{}", pictureVO);
                uploadCount++;
            } catch (Exception e) {
                log.error("缩略图上传失败", e);
                continue;
            }

            if (uploadCount >= pictureLoadByBatchDTO.getCount()) break;
        }

        return uploadCount;
    }
    public boolean downloadImage(String imageUrl, String filename, HttpServletResponse response) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址不存在");
        }

        HttpURLConnection connection = null;
        try (OutputStream outputStream = response.getOutputStream()) {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                log.error("远程服务器返回错误: {}", statusCode);
                response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                response.getWriter().write("获取图片失败");
                return false;
            }

            // 读取前8个字节用于判断真实格式
            byte[] header = new byte[8];
            String realExtension = ".jpg"; // 默认

            try (InputStream inputStream = connection.getInputStream()) {
                // 读取文件头
                int headerRead = inputStream.read(header, 0, 8);
                if (headerRead > 0) {
                    realExtension = PictureUtil.detectImageFormat(header);
                }

                // 获取Content-Type作为备用
                String contentType = connection.getContentType();
                if (contentType == null || contentType.isEmpty()) {
                    contentType = PictureUtil.guessContentTypeFromUrl(imageUrl);
                }

                // 生成文件名
                String finalFilename = generateFilenameWithRealExtension(filename, realExtension, imageUrl);
                String encodedFilename = URLEncoder.encode(finalFilename, "UTF-8")
                        .replaceAll("\\+", "%20");

                // 设置响应头
                response.setContentType(contentType);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename);
                // 前后端跨域开发时，允许浏览器读取下载文件名响应头。
                response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        HttpHeaders.CONTENT_DISPOSITION);
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

                // 写入文件头（已经读取的前8字节）
                outputStream.write(header, 0, headerRead);

                // 继续传输剩余内容
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = headerRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    if (totalBytes > 100 * 1024 * 1024) {
                        log.info("正在下载大文件: {}, 已传输: {} MB", finalFilename, totalBytes / 1024 / 1024);
                    }
                }
                outputStream.flush();
                log.info("图片下载成功: {}, 大小: {} bytes, 格式: {}", finalFilename, totalBytes, realExtension);
                return true;
            }

        } catch (Exception e) {
            log.error("下载图片失败: {}", e.getMessage(), e);
            try {
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"下载失败: " + e.getMessage() + "\"}");
                }
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载失败");
            }
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String url = oldPicture.getUrl();
        Long count = this.lambdaQuery()
                .eq(Picture::getUrl, url)
                .count();
        // 当前记录更新或逻辑删除后，只要还有记录引用这个 URL 就不能删除。
        if (count > 0) {
            return;
        }
        deletePictureFiles(oldPicture);
    }

    private void deletePictureFiles(Picture picture) {
        for (String fileUrl : Arrays.asList(picture.getUrl(), picture.getOriginUrl(), picture.getThumbnailUrl())
                .stream().filter(StrUtil::isNotBlank).distinct().collect(Collectors.toList())) {
            try {
                cosManager.delObject(fileUrl);
            } catch (Exception e) {
                log.error("清理 COS 文件失败，url={}", fileUrl, e);
            }
        }
    }

    private void clearPictureFileAfterCommit(Picture picture) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clearPictureFile(picture);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clearPictureFile(picture);
            }
        });
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            ThrowUtils.throwIf(!Objects.equals(picture.getUserId(), loginUserId) && !userService.isAdmin(loginUser),
                    ErrorCode.NO_AUTH_ERROR);
            return;
        }
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(!Objects.equals(space.getUserId(), loginUserId), ErrorCode.NO_AUTH_ERROR);
    }

    @Override
    @Transactional
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Picture picture = this.getById(pictureId);
        ThrowUtils.throwIf(picture==null, ErrorCode.NOT_FOUNT_EORROR);

        // 校验权限
        this.checkPictureAuth(loginUser, picture);
        // 操作数据库
        boolean result = this.removeById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION);

        // 与图片删除处于同一数据库事务：清除点赞，保留收藏作为用户的删除历史。
        pictureInteractionService.handlePictureDeleted(pictureId);

        if (picture.getSpaceId() != null) {
            long pictureSize = picture.getPicSize() == null ? 0L : picture.getPicSize();
            updateSpaceUsage(picture.getSpaceId(), -pictureSize, -1L);
        }
        // 数据库事务提交成功后再删除 COS 文件，避免事务回滚造成文件丢失。
        clearPictureFileAfterCommit(picture);
    }

    @Override
    public void editPicture(PictureEditDTO pictureEditDTO, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditDTO, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditDTO.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);

        // 判断是否存在
        long id = pictureEditDTO.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUNT_EORROR);
        // 校验权限
        this.checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.updateOrCreate(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION);
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor) , ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在，快去创建一个吧!");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不能访问别人的空间");
        }
        // 3. 开始查询所有图片，必须有主色调
        List<Picture> picList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 4. 计算相似度并排序
        Color targetColor = Color.decode(picColor);
        List<Picture> pictureList = picList.stream().sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    // 主色调为空的话，直接放最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    return -calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12)// 取前12张
                .collect(Collectors.toList());
        // 5. 返回结果
        return pictureList.stream()
                .map(PictureVO::objToVO)
                .collect(Collectors.toList());
    }

    private String generateFilenameWithRealExtension(String customFilename, String realExtension, String imageUrl) {
        if (StrUtil.isNotBlank(customFilename)) {
            // 去除原有扩展名，使用真实扩展名
            String baseName = FileUtil.mainName(customFilename);
            return baseName + realExtension;
        }
        return "image_" + System.currentTimeMillis() + realExtension;
    }
}




