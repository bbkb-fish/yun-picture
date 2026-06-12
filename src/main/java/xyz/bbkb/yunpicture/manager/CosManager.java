package xyz.bbkb.yunpicture.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.bbkb.yunpicture.config.CosClientConfig;

import java.io.File;

@Component
@RequiredArgsConstructor
public class CosManager {
    private final CosClientConfig cosClientConfig;
    private final COSClient cosClient;

    // 将本地文件上传到 COS
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest request = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(request);
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
        // 设置
        request.setPicOperations(picOperations);
        return cosClient.putObject(request);
    }
}
