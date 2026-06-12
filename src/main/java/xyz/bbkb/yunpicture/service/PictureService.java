package xyz.bbkb.yunpicture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;
import xyz.bbkb.yunpicture.domain.dto.picture.PictureQueryDTO;
import xyz.bbkb.yunpicture.domain.dto.picture.PictureUploadDTO;
import xyz.bbkb.yunpicture.domain.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author dearSmile
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-06-12 16:40:34
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传或更新
     * @param multipartFile
     * @param pictureUploadDTO
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadDTO pictureUploadDTO, User loginUser);


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
}
