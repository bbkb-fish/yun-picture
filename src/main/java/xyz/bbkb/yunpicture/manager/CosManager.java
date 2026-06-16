package xyz.bbkb.yunpicture.manager;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.bbkb.yunpicture.config.CosClientConfig;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CosManager {
    private final CosClientConfig cosClientConfig;
    private final COSClient cosClient;

    // 将本地文件上传到 COS
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest request = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(request);
    }
    // 删除
    public void delObject(String key) {
        String objectKey = extractObjectKey(key);
        cosClient.deleteObject(cosClientConfig.getBucket(), objectKey);
    }
    /**
     * 从完整URL中提取对象键
     * @param keyOrUrl 完整URL或key
     * @return 对象键
     */
    private String extractObjectKey(String keyOrUrl) {
        // 如果包含 http:// 或 https://，说明是完整URL
        if (keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://")) {
            try {
                URL url = new URL(keyOrUrl);
                String path = url.getPath();
                // 去掉开头的 /
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                return path;
            } catch (MalformedURLException e) {
                log.error("解析URL失败: {}", keyOrUrl, e);
                return keyOrUrl;
            }
        }
        return keyOrUrl;
    }
    // 下载文件到服务器
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }
    // 上传并解析图片
    public PutObjectResult putAndAnalysisObject(String key, File file) {
        PutObjectRequest request = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        PicOperations picOperations = new PicOperations();
        // 1表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 图片处理规则列表
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 图片压缩，转为webp格式
        String webKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webKey);
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setRule("imageMogr2/format/webp");
        rules.add(compressRule);
        // 缩略图
        if (file.length() > 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 256, 256));
            rules.add(thumbnailRule);
        }
        // 设置
        picOperations.setRules(rules);
        request.setPicOperations(picOperations);
        return cosClient.putObject(request);
    }
}
