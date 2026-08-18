package xyz.bbkb.yunpicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import xyz.bbkb.yunpicture.domain.dto.comment.PictureCommentAddDTO;
import xyz.bbkb.yunpicture.domain.dto.comment.PictureCommentQueryDTO;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureCommentVO;

/** 图片评论业务。 */
public interface PictureCommentService {

    Long addComment(PictureCommentAddDTO addDTO, User loginUser);

    boolean deleteComment(Long commentId, User loginUser);

    Page<PictureCommentVO> listRootComments(PictureCommentQueryDTO queryDTO, User loginUser);

    Page<PictureCommentVO> listReplies(PictureCommentQueryDTO queryDTO, User loginUser);
}
