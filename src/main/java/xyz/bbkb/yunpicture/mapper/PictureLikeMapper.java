package xyz.bbkb.yunpicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.bbkb.yunpicture.domain.entity.PictureLike;

import java.util.Collection;
import java.util.List;

/** 图片点赞关系数据库操作。 */
public interface PictureLikeMapper extends BaseMapper<PictureLike> {

    /**
     * 原子新增点赞关系。
     * 联合唯一索引会拦截重复点赞；INSERT IGNORE 将重复请求转换为影响 0 行。
     */
    @Insert("""
            INSERT IGNORE INTO picture_like (id, user_id, picture_id, create_time)
            VALUES (#{id}, #{userId}, #{pictureId}, NOW())
            """)
    int insertIgnore(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("pictureId") Long pictureId);

    /** 只删除当前用户与指定图片之间的点赞关系。 */
    @Delete("""
            DELETE FROM picture_like
            WHERE user_id = #{userId} AND picture_id = #{pictureId}
            """)
    int deleteRelation(@Param("userId") Long userId,
                       @Param("pictureId") Long pictureId);

    /** 图片删除时清理该图片的全部点赞关系。 */
    @Delete("DELETE FROM picture_like WHERE picture_id = #{pictureId}")
    int deleteByPictureId(@Param("pictureId") Long pictureId);

    /**
     * 批量返回已点赞的图片 ID，直接返回 Long 可避免实体字段映射影响查询结果。
     */
    @Select("""
            <script>
            SELECT picture_id
            FROM picture_like
            WHERE user_id = #{userId}
              AND picture_id IN
              <foreach collection="pictureIds" item="pictureId" open="(" separator="," close=")">
                #{pictureId}
              </foreach>
            </script>
            """)
    List<Long> selectLikedPictureIds(@Param("userId") Long userId,
                                     @Param("pictureIds") Collection<Long> pictureIds);
}
