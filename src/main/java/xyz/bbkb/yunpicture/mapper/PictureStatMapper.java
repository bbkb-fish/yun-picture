package xyz.bbkb.yunpicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import xyz.bbkb.yunpicture.domain.entity.PictureStat;

/**
* @author dearSmile
* @description 针对表【picture_stat】的数据库操作Mapper
* @createDate 2026-08-16 21:04:12
* @Entity xyz.bbkb.yunpicture.domain.entity.PictureStat
*/
public interface PictureStatMapper extends BaseMapper<PictureStat> {

    /**
     * 新增或覆盖图片累计统计。Redis 中保存的是实时绝对值，因此同步时直接覆盖即可。
     */
    @Insert({
            "INSERT INTO picture_stat",
            "(picture_id, view_count, download_count, like_count, favorite_count)",
            "VALUES",
            "(#{pictureId}, #{viewCount}, #{downloadCount}, #{likeCount}, #{favoriteCount})",
            "ON DUPLICATE KEY UPDATE",
            "view_count = VALUES(view_count),",
            "download_count = VALUES(download_count),",
            "like_count = VALUES(like_count),",
            "favorite_count = VALUES(favorite_count)"
    })
    int upsert(PictureStat pictureStat);
}




