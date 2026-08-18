package xyz.bbkb.yunpicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xyz.bbkb.yunpicture.domain.entity.PictureComment;

/** 图片评论数据库操作。 */
public interface PictureCommentMapper extends BaseMapper<PictureComment> {

    /** 绕过 MyBatis-Plus 逻辑删除过滤，用于删除权限校验和根评论占位。 */
    @Select("""
            SELECT id, picture_id AS pictureId, user_id AS userId,
                   root_id AS rootId, parent_id AS parentId,
                   reply_user_id AS replyUserId, content,
                   reply_count AS replyCount, create_time AS createTime,
                   update_time AS updateTime, is_delete AS isDelete
            FROM picture_comment WHERE id = #{id} LIMIT 1
            """)
    PictureComment selectAnyById(@Param("id") Long id);

    /**
     * 根评论按最新发布排序。已删除但仍有有效回复的根评论继续返回，
     * 前端会把它渲染为灰色占位，以免回复上下文突然消失。
     */
    @Select("""
            SELECT id, picture_id AS pictureId, user_id AS userId,
                   root_id AS rootId, parent_id AS parentId,
                   reply_user_id AS replyUserId, content,
                   reply_count AS replyCount, create_time AS createTime,
                   update_time AS updateTime, is_delete AS isDelete
            FROM picture_comment
            WHERE picture_id = #{pictureId}
              AND root_id = 0
              AND (is_delete = 0 OR reply_count > 0)
            ORDER BY create_time DESC, id DESC
            """)
    IPage<PictureComment> selectRootPage(Page<PictureComment> page,
                                         @Param("pictureId") Long pictureId);

    /** 子回复按发布时间正序展示；已删除的子回复不再占据列表位置。 */
    @Select("""
            SELECT id, picture_id AS pictureId, user_id AS userId,
                   root_id AS rootId, parent_id AS parentId,
                   reply_user_id AS replyUserId, content,
                   reply_count AS replyCount, create_time AS createTime,
                   update_time AS updateTime, is_delete AS isDelete
            FROM picture_comment
            WHERE root_id = #{rootId}
              AND is_delete = 0
            ORDER BY create_time ASC, id ASC
            """)
    IPage<PictureComment> selectReplyPage(Page<PictureComment> page,
                                          @Param("rootId") Long rootId);

    /** 只有第一次删除会影响一行，使重复请求不会重复扣减 reply_count。 */
    @Update("""
            UPDATE picture_comment
            SET is_delete = 1, update_time = NOW()
            WHERE id = #{id} AND is_delete = 0
            """)
    int markDeleted(@Param("id") Long id);

    @Update("""
            UPDATE picture_comment
            SET reply_count = reply_count + 1, update_time = NOW()
            WHERE id = #{rootId}
            """)
    int incrementReplyCount(@Param("rootId") Long rootId);

    @Update("""
            UPDATE picture_comment
            SET reply_count = GREATEST(reply_count - 1, 0), update_time = NOW()
            WHERE id = #{rootId}
            """)
    int decrementReplyCount(@Param("rootId") Long rootId);
}
