package xyz.bbkb.yunpicture.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.bbkb.yunpicture.common.BaseResponse;
import xyz.bbkb.yunpicture.common.DeleteRequest;
import xyz.bbkb.yunpicture.common.ResultUtils;
import xyz.bbkb.yunpicture.domain.dto.comment.PictureCommentAddDTO;
import xyz.bbkb.yunpicture.domain.dto.comment.PictureCommentQueryDTO;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureCommentVO;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.service.PictureCommentService;
import xyz.bbkb.yunpicture.service.UserService;

/** 图片详情页评论接口。 */
@RestController
@RequestMapping("/picture/comment")
@RequiredArgsConstructor
public class PictureCommentController {

    private final PictureCommentService pictureCommentService;
    private final UserService userService;

    /** 发布根评论或回复。 */
    @PostMapping("/add")
    public BaseResponse<Long> addComment(@RequestBody PictureCommentAddDTO addDTO,
                                         HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureCommentService.addComment(addDTO, loginUser));
    }

    /** 删除自己的评论；图片作者和管理员也可以管理评论。 */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteComment(@RequestBody DeleteRequest deleteRequest,
                                                HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(
                pictureCommentService.deleteComment(deleteRequest.getId(), loginUser));
    }

    /** 分页查询某张图片的根评论。公开读取允许未登录用户访问。 */
    @PostMapping("/list/page")
    public BaseResponse<Page<PictureCommentVO>> listRootComments(
            @RequestBody PictureCommentQueryDTO queryDTO,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUserPermitNull(request);
        return ResultUtils.success(pictureCommentService.listRootComments(queryDTO, loginUser));
    }

    /** 展开某条根评论下的回复。 */
    @PostMapping("/reply/list/page")
    public BaseResponse<Page<PictureCommentVO>> listReplies(
            @RequestBody PictureCommentQueryDTO queryDTO,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUserPermitNull(request);
        return ResultUtils.success(pictureCommentService.listReplies(queryDTO, loginUser));
    }
}
