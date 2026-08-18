package xyz.bbkb.yunpicture.mapper;

import xyz.bbkb.yunpicture.domain.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.bbkb.yunpicture.domain.dto.picture.PictureInteractionCountDTO;

import java.util.List;

/**
* @author dearSmile
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2026-06-12 16:40:34
* @Entity xyz.bbkb.yunpicture.domain.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    /**
     * 一次联表聚合全部公开图片的真实点赞数、收藏数。
     * 以 picture 为主表可以返回计数为 0 的图片，用来修复“关系已经全部取消但缓存仍大于 0”的情况。
     */
    @Select("""
            SELECT p.id AS pictureId,
                   COALESCE(pl.likeCount, 0) AS likeCount,
                   COALESCE(pf.favoriteCount, 0) AS favoriteCount
            FROM picture p
            LEFT JOIN (
                SELECT picture_id, COUNT(*) AS likeCount
                FROM picture_like
                GROUP BY picture_id
            ) pl ON pl.picture_id = p.id
            LEFT JOIN (
                SELECT picture_id, COUNT(*) AS favoriteCount
                FROM picture_favorite
                GROUP BY picture_id
            ) pf ON pf.picture_id = p.id
            WHERE p.isDelete = 0
              AND p.spaceId IS NULL
              AND p.reviewStatus = #{reviewStatus}
            """)
    List<PictureInteractionCountDTO> selectInteractionCounts(
            @Param("reviewStatus") Integer reviewStatus);
}




