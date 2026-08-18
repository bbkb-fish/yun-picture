package xyz.bbkb.yunpicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.bbkb.yunpicture.domain.entity.PictureFavorite;
import xyz.bbkb.yunpicture.domain.entity.Picture;

import java.util.Collection;
import java.util.List;

/** 图片收藏关系数据库操作。 */
public interface PictureFavoriteMapper extends BaseMapper<PictureFavorite> {

    /**
     * 原子新增收藏关系。
     * 联合唯一索引会拦截重复收藏；INSERT IGNORE 将重复请求转换为影响 0 行。
     */
    @Insert("""
            INSERT IGNORE INTO picture_favorite (id, user_id, picture_id, favorite_time)
            VALUES (#{id}, #{userId}, #{pictureId}, NOW())
            """)
    int insertIgnore(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("pictureId") Long pictureId);

    /** 只删除当前用户与指定图片之间的收藏关系。 */
    @Delete("""
            DELETE FROM picture_favorite
            WHERE user_id = #{userId} AND picture_id = #{pictureId}
            """)
    int deleteRelation(@Param("userId") Long userId,
                       @Param("pictureId") Long pictureId);

    /** 批量返回已收藏的图片 ID，供图片列表一次性补充用户状态。 */
    @Select("""
            <script>
            SELECT picture_id
            FROM picture_favorite
            WHERE user_id = #{userId}
              AND picture_id IN
              <foreach collection="pictureIds" item="pictureId" open="(" separator="," close=")">
                #{pictureId}
              </foreach>
            </script>
            """)
    List<Long> selectFavoritedPictureIds(@Param("userId") Long userId,
                                         @Param("pictureIds") Collection<Long> pictureIds);

    /**
     * 分页查询当前用户的收藏图片。
     * 已删除图片保留为墓碑；未删除图片仍然必须是审核通过的公共图片。
     */
    @Select("""
            SELECT p.*
            FROM picture_favorite pf
            INNER JOIN picture p ON p.id = pf.picture_id
            WHERE pf.user_id = #{userId}
              AND p.spaceId IS NULL
              AND (
                    p.isDelete = 1
                    OR (p.isDelete = 0 AND p.reviewStatus = #{reviewStatus})
                  )
            ORDER BY pf.favorite_time DESC
            """)
    IPage<Picture> selectFavoritePicturePage(Page<Picture> page,
                                             @Param("userId") Long userId,
                                             @Param("reviewStatus") Integer reviewStatus);
}
