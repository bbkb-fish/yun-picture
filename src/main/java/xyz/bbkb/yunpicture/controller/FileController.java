package xyz.bbkb.yunpicture.controller;

import cn.hutool.http.server.HttpServerResponse;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xyz.bbkb.yunpicture.annotation.AuthCheck;
import xyz.bbkb.yunpicture.common.BaseResponse;
import xyz.bbkb.yunpicture.common.ResultUtils;
import xyz.bbkb.yunpicture.constant.UserConstant;
import xyz.bbkb.yunpicture.manager.CosManager;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final CosManager cosManager;
    /**
     * 测试文件上传
     * @param multipartFile
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file")MultipartFile multipartFile) {
        // 文件目录
        String filename = multipartFile.getOriginalFilename();
        String filePath = String.format("/test/%s", filename);
        File file = null;
        try {
            file = File.createTempFile(filePath, null);
            multipartFile.transferTo(file);
            cosManager.putObject(filePath, file);
            return ResultUtils.success(filePath);
        } catch (IOException e) {
            log.info("file upload error, filePath = {}, error = {}", filePath, e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean ifDelete = file.delete();
                if(!ifDelete) {
                    log.error("file delete error, filePath = {}", filePath);
                }
            }
        }
    }
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/download")
    public void testDownload(String filepath, HttpServletResponse response) throws IOException {
        COSObjectInputStream objectContent = null;
        try {
            COSObject cosObject = cosManager.getObject(filepath);
            objectContent = cosObject.getObjectContent();
            byte[] byteArray = IOUtils.toByteArray(objectContent);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应
            response.getOutputStream().write(byteArray);
            // 更新
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if(objectContent != null) {
                objectContent.close();
            }
        }
    }
}
