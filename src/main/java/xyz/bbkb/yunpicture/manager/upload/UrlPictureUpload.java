package xyz.bbkb.yunpicture.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import org.springframework.stereotype.Component;
import xyz.bbkb.yunpicture.config.CosClientConfig;
import xyz.bbkb.yunpicture.exception.BusinessException;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.manager.CosManager;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Component
public class UrlPictureUpload extends PictureUploadTemplate{
    public UrlPictureUpload(CosManager cosManager, CosClientConfig cosClientConfig) {
        super(cosManager, cosClientConfig);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws IOException {
        HttpUtil.downloadFile((String) inputSource, file);
    }

    @Override
    protected String getOriginalFilename(Object inputSource) {
        return FileUtil.getName((String) inputSource);
    }

    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 非空
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");
        // url格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件格式不正确");
        }
        // 校验url协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"), ErrorCode.PARAMS_ERROR, "仅支持HTTP或HTTPS");
        HttpResponse httpResponse = null;
        try {
            // head请求，验证文件是否存在
            httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            // 可能不支持HEAD请求，先返回不报错
            if (!httpResponse.isOk()){
                return;
            }
            // 文件存在，文件类型的校验
            String contentType = httpResponse.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 文件大小校验
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentSize = Long.parseLong(contentLengthStr);
                    final long ONE_M = 1024L * 1024;
                    ThrowUtils.throwIf(contentSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过2MB");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式异常");
                }
            }
        }finally {
            // 记得释放
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }
}
