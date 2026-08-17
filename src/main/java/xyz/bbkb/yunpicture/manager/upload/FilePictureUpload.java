package xyz.bbkb.yunpicture.manager.upload;

import cn.hutool.core.io.FileUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import xyz.bbkb.yunpicture.config.CosClientConfig;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.manager.CosManager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 文件上传
 */
@Component
public class FilePictureUpload extends PictureUploadTemplate{


    public FilePictureUpload(CosManager cosManager, CosClientConfig cosClientConfig) {
        super(cosManager, cosClientConfig);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws IOException {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }

    @Override
    protected String getOriginalFilename(Object inputSource) {
        return  ((MultipartFile) inputSource).getOriginalFilename();
    }

    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1. 校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过2M");
        String originalFilename = multipartFile.getOriginalFilename();
        ThrowUtils.throwIf(originalFilename == null, ErrorCode.PARAMS_ERROR, "文件名不能为空");
        String suffix = FileUtil.getSuffix(originalFilename).toLowerCase(Locale.ROOT);
        // 允许上传的文件后最
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "png", "jpg", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "不能上传该类型的文件");
    }
}
