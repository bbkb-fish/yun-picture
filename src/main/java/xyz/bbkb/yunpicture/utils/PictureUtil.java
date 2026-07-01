package xyz.bbkb.yunpicture.utils;

import org.springframework.http.MediaType;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import java.awt.*;

public class PictureUtil {

    private PictureUtil() {}

    /**
     * 根据URL猜测Content-Type
     */
    public static String guessContentTypeFromUrl(String url) {
        if (url == null) return MediaType.IMAGE_JPEG_VALUE;

        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lowerUrl.contains(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (lowerUrl.contains(".webp")) return "image/webp";
        if (lowerUrl.contains(".bmp")) return "image/bmp";
        if (lowerUrl.contains(".svg")) return "image/svg+xml";
        if (lowerUrl.contains(".jpeg") || lowerUrl.contains(".jpg") || lowerUrl.contains(".jfif")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        return MediaType.IMAGE_JPEG_VALUE; // 默认
    }

    /**
     * 生成文件名（优先使用URL中的真实扩展名）
     */
    public static String generateFilename(String customFilename, String contentType, String imageUrl) {
        String extension = getExtensionFromUrlOrContentType(imageUrl, contentType);

        if (StrUtil.isNotBlank(customFilename)) {
            // 确保有正确扩展名
            if (!customFilename.toLowerCase().endsWith(extension)) {
                return customFilename + extension;
            }
            return customFilename;
        }

        // 生成默认文件名
        return "image_" + System.currentTimeMillis() + extension;
    }

    /**
     * 优先从URL提取扩展名，失败再从Content-Type获取
     */
    private static String getExtensionFromUrlOrContentType(String imageUrl, String contentType) {
        // 1. 优先从URL提取真实扩展名
        String urlExtension = extractExtensionFromUrl(imageUrl);
        if (StrUtil.isNotBlank(urlExtension)) {
            // 统一将 .jfif 转换为 .jpg
            if (".jfif".equals(urlExtension)) {
                return ".jpg";
            }
            return urlExtension;
        }

        // 2. 从Content-Type获取
        return getExtensionFromContentType(contentType);
    }

    /**
     * 从URL中提取文件扩展名
     */
    private static String extractExtensionFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }

        // 去除URL参数
        String cleanUrl = url.split("\\?")[0];
        // 获取最后一个点后面的部分
        String extension = FileUtil.getSuffix(cleanUrl);

        if (StrUtil.isNotBlank(extension)) {
            extension = extension.toLowerCase();
            // 统一处理：.jfif 映射为 .jpg
            if ("jfif".equals(extension)) {
                return ".jpg";
            }
            return "." + extension;
        }
        return null;
    }

    /**
     * 从Content-Type获取扩展名（修正为返回 .jpg 而不是 .jfif）
     */
    public static String getExtensionFromContentType(String contentType) {
        if (contentType == null) return ".jpg";

        if (contentType.contains("png")) return ".png";
        if (contentType.contains("gif")) return ".gif";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("bmp")) return ".bmp";
        if (contentType.contains("jpeg") || contentType.contains("jpg") || contentType.contains("jfif")) {
            return ".jpg";  // 关键：统一返回 .jpg
        }
        return ".jpg";
    }

    /**
     * 获取真实的图片格式（通过读取文件头）
     * 这是最准确的方法
     */
    public static String detectImageFormat(byte[] fileHeader) {
        if (fileHeader == null || fileHeader.length < 8) {
            return ".jpg";
        }

        // PNG: 137 80 78 71 13 10 26 10
        if (fileHeader[0] == (byte) 0x89 && fileHeader[1] == (byte) 0x50 &&
                fileHeader[2] == (byte) 0x4E && fileHeader[3] == (byte) 0x47) {
            return ".png";
        }
        // GIF: 47 49 46 38
        if (fileHeader[0] == (byte) 0x47 && fileHeader[1] == (byte) 0x49 &&
                fileHeader[2] == (byte) 0x46 && fileHeader[3] == (byte) 0x38) {
            return ".gif";
        }
        // WEBP: 52 49 46 46
        if (fileHeader[0] == (byte) 0x52 && fileHeader[1] == (byte) 0x49 &&
                fileHeader[2] == (byte) 0x46 && fileHeader[3] == (byte) 0x46) {
            return ".webp";
        }
        // JPEG: 255 216
        if (fileHeader[0] == (byte) 0xFF && fileHeader[1] == (byte) 0xD8) {
            return ".jpg";
        }

        return ".jpg";
    }

    /**
     * 计算图片中颜色的相似度
     * @param c1
     * @param c2
     * @return
     */
    public static double calculateSimilarity(Color c1, Color c2) {
        int r1 = c1.getRed();
        int g1 = c1.getGreen();
        int b1 = c1.getBlue();

        int r2 = c2.getRed();
        int g2 = c2.getGreen();
        int b2 = c2.getBlue();

        double dis = Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(b1 - b2, 2) + Math.pow(g1 - g2, 2));
        return 1 - dis / Math.sqrt(3 * Math.sqrt(3 * Math.pow(255, 2)));
    }
}