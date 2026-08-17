package xyz.bbkb.yunpicture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import xyz.bbkb.yunpicture.domain.dto.picture.*;
import xyz.bbkb.yunpicture.domain.entity.Picture;
import com.baomidou.mybatisplus.spring.service.IService;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureVO;
import xyz.bbkb.yunpicture.enums.PictureReviewStatusEnum;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
* @author dearSmile
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-06-12 16:40:34
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传或更新
     * @param inputSource
     * @param pictureUploadDTO
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadDTO pictureUploadDTO, User loginUser);


    QueryWrapper<Picture> getQueryWrapper(PictureQueryDTO pictureQueryDTO) ;

    /**
     * 转化Picture为 VO
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request) ;

    Page<PictureVO> getPagePictureVO(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 数据校验
     * @param picture
     */
    void validPicture(Picture picture) ;

    /**
     * 审核图片
     * @param pictureReviewDTO
     * @param user
     */
    void doPictureReview(PictureReviewDTO pictureReviewDTO, User user);

    /**
     * 更新或上传图片，审核状态设置
     * @param picture
     * @param loginUser
     */
    void updateOrCreate(Picture picture, User loginUser);
    /**
     * 填充图片状态
     * @param picture
     * @param loginUser
     * @param pictureReviewStatusEnum
     */
    void fillReviewParams(Picture picture, User loginUser, PictureReviewStatusEnum pictureReviewStatusEnum, String message);

    /**
     * 批量抓取图片
     * @param pictureLoadByBatchDTO
     * @param loginUser
     * @return
     */
    Integer uploadPictureByBatch(PictureLoadByBatchDTO pictureLoadByBatchDTO, User loginUser);

    /**
     * 流式下载图片
     * @param imageUrl
     * @param filename
     * @param response
     */
    void downloadImage(String imageUrl, String filename, HttpServletResponse response);

    /**
     * 清除COS中的文件
     * @param oldPicture
     */
    void clearPictureFile(Picture oldPicture);

    /**
     * 校验空间的权限
     * @param loginUser
     * @param picture
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 删除图片
     * @param pictureId
     * @param loginUser
     */
    void deletePicture(long pictureId, User loginUser);

    /**
     * 编辑图片
     * @param pictureEditDTO
     * @param loginUser
     */
    void editPicture(PictureEditDTO pictureEditDTO, User loginUser);

    /**
     * 根据主色调查询图片
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);
}
