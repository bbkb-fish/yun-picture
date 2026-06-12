package xyz.bbkb.yunpicture.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import xyz.bbkb.yunpicture.config.CosClientConfig;
import xyz.bbkb.yunpicture.domain.dto.file.UploadPictureFileDTO;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileManger {
    private final CosManager cosManager;
    private final CosClientConfig cosClientConfig;
    private final COSClient cosClient;

    public UploadPictureFileDTO uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 校验图片
        validPicture(multipartFile);
        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = multipartFile.getOriginalFilename();
        // 加时间戳 日期
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originalFilename));
        // 最终上传路径
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        // 解析结果
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            PutObjectResult putObjectResult = cosManager.putAndAnalysisObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            UploadPictureFileDTO uploadPictureDTO = new UploadPictureFileDTO();
            uploadPictureDTO.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            uploadPictureDTO.setPicName(FileUtil.mainName(originalFilename));
            uploadPictureDTO.setPicSize(FileUtil.size(file));
            uploadPictureDTO.setPicFormat(imageInfo.getFormat());
            uploadPictureDTO.setPicHeight(imageInfo.getHeight());
            uploadPictureDTO.setPicWidth(imageInfo.getWidth());
            uploadPictureDTO.setPicScale(NumberUtil.round(1.0 * imageInfo.getWidth()/imageInfo.getHeight(), 2).doubleValue());

            return uploadPictureDTO;
        } catch (IOException e) {
            log.info("file upload to COS error, filePath = {}, error = {}", uploadPath, e.getMessage());
            throw new RuntimeException(e);
        } finally {
            deleteTemplateFile(file);
        }
    }

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
     * 校验文件
     * @param multipartFile
     * @return
     */
    private void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile==null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1. 校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过2M");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许上传的文件后最
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "png", "jpg", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "不能上传该类型的文件");
    }
}
