package xyz.bbkb.yunpicture.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.bbkb.yunpicture.config.CosClientConfig;
import xyz.bbkb.yunpicture.domain.dto.file.UploadPictureFileDTO;
import xyz.bbkb.yunpicture.manager.CosManager;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * 图片上传模板
 */
@Slf4j
@RequiredArgsConstructor
public abstract class PictureUploadTemplate {
    private final CosManager cosManager;
    private final CosClientConfig cosClientConfig;

    public UploadPictureFileDTO uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 校验图片
        validPicture(inputSource);
        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginalFilename(inputSource);
        // 加时间戳 日期
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originalFilename));
        // 最终上传路径
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        // 解析结果
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            processFile(inputSource, file);
            PutObjectResult putObjectResult = cosManager.putAndAnalysisObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 获取到图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if(CollUtil.isNotEmpty(objectList)) {
                // 获取压缩之后的文件信息
                CIObject ciObject = objectList.get(0);
                CIObject thumbnailCiObject = ciObject;
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1);
                }
                return buildResult(originalFilename, uploadPath, ciObject, thumbnailCiObject, file, imageInfo);
            }
            // 封装返回结果
            return buildResult(originalFilename, file, uploadPath, imageInfo);
        } catch (IOException e) {
            log.info("file upload to COS error, filePath = {}, error = {}", uploadPath, e.getMessage());
            throw new RuntimeException(e);
        } finally {
            deleteTemplateFile(file);
        }
    }

    protected abstract void processFile(Object inputSource, File file) throws IOException;

    protected abstract String getOriginalFilename(Object inputSource);

    protected abstract void validPicture(Object inputSource);
    /**
     * 清除临时文件
     * @param file
     */
    private void deleteTemplateFile(File file) {
        if (file != null) {
            // 删除临时文件
            boolean ifDelete = file.delete();
            if(!ifDelete) {
                log.error("file delete error, filePath = {}", file.getAbsoluteFile());
            }
        }
    }
    /**
     * 封装返回结果
     * @param imageInfo
     * @param originalFilename
     * @param file
     * @param uploadPath
     * @return
     */
    private UploadPictureFileDTO buildResult(String originalFilename, File file, String uploadPath, ImageInfo imageInfo) {
        UploadPictureFileDTO uploadPictureDTO = new UploadPictureFileDTO();
        uploadPictureDTO.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        uploadPictureDTO.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureDTO.setPicSize(FileUtil.size(file));
        uploadPictureDTO.setPicFormat(imageInfo.getFormat());
        uploadPictureDTO.setPicHeight(imageInfo.getHeight());
        uploadPictureDTO.setPicWidth(imageInfo.getWidth());
        uploadPictureDTO.setPicColor(imageInfo.getAve()); // 图片主色调
        uploadPictureDTO.setPicScale(NumberUtil.round(1.0 * imageInfo.getWidth()/imageInfo.getHeight(), 2).doubleValue());
        return uploadPictureDTO;
    }
    /**
     * 封装返回结果(压缩后的)
     * @param originalFilename
     * @param uploadPath
     * @param ciObject
     * @param imageInfo 图片信息
     * @return
     */
    private UploadPictureFileDTO buildResult(String originalFilename, String uploadPath, CIObject ciObject, CIObject thumbnailObject, File file, ImageInfo imageInfo) {
        UploadPictureFileDTO uploadPictureDTO = new UploadPictureFileDTO();
        uploadPictureDTO.setUrl(cosClientConfig.getHost() + "/" + ciObject.getKey());
        uploadPictureDTO.setOriginUrl(cosClientConfig.getHost() + "/" +uploadPath);
        uploadPictureDTO.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailObject.getKey());
        uploadPictureDTO.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureDTO.setPicSize(FileUtil.size(file));
        uploadPictureDTO.setPicFormat(ciObject.getFormat());
        uploadPictureDTO.setPicHeight(ciObject.getHeight());
        uploadPictureDTO.setPicWidth(ciObject.getWidth());
        uploadPictureDTO.setPicColor(imageInfo.getAve()); // 图片主色调
        uploadPictureDTO.setPicScale(NumberUtil.round(1.0 * ciObject.getWidth()/ciObject.getHeight(), 2).doubleValue());
        return uploadPictureDTO;
    }

}
