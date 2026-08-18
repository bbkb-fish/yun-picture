package xyz.bbkb.yunpicture.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.bbkb.yunpicture.domain.dto.comment.PictureCommentAddDTO;
import xyz.bbkb.yunpicture.domain.dto.comment.PictureCommentQueryDTO;
import xyz.bbkb.yunpicture.domain.entity.Picture;
import xyz.bbkb.yunpicture.domain.entity.PictureComment;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.PictureCommentVO;
import xyz.bbkb.yunpicture.domain.vo.UserVO;
import xyz.bbkb.yunpicture.enums.PictureReviewStatusEnum;
import xyz.bbkb.yunpicture.enums.NotificationTypeEnum;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.mapper.PictureCommentMapper;
import xyz.bbkb.yunpicture.mapper.PictureMapper;
import xyz.bbkb.yunpicture.service.PictureCommentService;
import xyz.bbkb.yunpicture.service.NotificationService;
import xyz.bbkb.yunpicture.service.UserService;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论实现：MySQL 保存最终状态，分页读取评论；删除使用逻辑删除保留必要的对话上下文。
 */
@Service
@RequiredArgsConstructor
public class PictureCommentServiceImpl implements PictureCommentService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_PAGE_SIZE = 50;

    private final PictureCommentMapper pictureCommentMapper;
    private final PictureMapper pictureMapper;
    private final UserService userService;
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addComment(PictureCommentAddDTO addDTO, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(addDTO == null || addDTO.getPictureId() == null
                        || addDTO.getPictureId() <= 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 不合法");

        String content = addDTO.getContent() == null ? "" : addDTO.getContent().trim();
        ThrowUtils.throwIf(!StringUtils.hasText(content) || content.length() > MAX_CONTENT_LENGTH,
                ErrorCode.PARAMS_ERROR, "评论内容长度须为 1～500 个字符");
        Picture picture = validatePublicAcceptedPicture(addDTO.getPictureId());

        PictureComment comment = new PictureComment();
        comment.setPictureId(addDTO.getPictureId());
        comment.setUserId(loginUser.getId());
        comment.setContent(content);
        comment.setRootId(0L);
        comment.setParentId(0L);
        comment.setReplyCount(0);
        Long notificationRecipientId = picture.getUserId();
        String notificationTitle = "收到新的图片评论";

        Long parentId = addDTO.getParentId();
        if (parentId != null && parentId > 0) {
            PictureComment parent = pictureCommentMapper.selectAnyById(parentId);
            ThrowUtils.throwIf(parent == null || Objects.equals(parent.getIsDelete(), 1),
                    ErrorCode.NOT_FOUND_ERROR, "回复的评论不存在或已删除");
            ThrowUtils.throwIf(!Objects.equals(parent.getPictureId(), addDTO.getPictureId()),
                    ErrorCode.PARAMS_ERROR, "回复的评论不属于当前图片");

            // 无论回复根评论还是其他回复，所有子回复都归属于同一个 rootId。
            Long rootId = Objects.equals(parent.getRootId(), 0L)
                    ? parent.getId() : parent.getRootId();
            PictureComment root = pictureCommentMapper.selectAnyById(rootId);
            ThrowUtils.throwIf(root == null, ErrorCode.NOT_FOUND_ERROR, "根评论不存在");
            comment.setRootId(rootId);
            comment.setParentId(parent.getId());
            comment.setReplyUserId(parent.getUserId());
            notificationRecipientId = parent.getUserId();
            notificationTitle = "有人回复了你的评论";
        }

        int inserted = pictureCommentMapper.insert(comment);
        ThrowUtils.throwIf(inserted != 1, ErrorCode.OPERATION_ERROR, "发布评论失败");
        if (!Objects.equals(comment.getRootId(), 0L)) {
            int updated = pictureCommentMapper.incrementReplyCount(comment.getRootId());
            ThrowUtils.throwIf(updated != 1, ErrorCode.OPERATION_ERROR, "更新回复数失败");
        }
        if (notificationRecipientId != null
                && !Objects.equals(notificationRecipientId, loginUser.getId())) {
            String actorName = StringUtils.hasText(loginUser.getUserName())
                    ? loginUser.getUserName() : "用户";
            notificationService.createNotification(
                    notificationRecipientId,
                    NotificationTypeEnum.COMMENT,
                    notificationTitle,
                    actorName + "：" + content,
                    "PICTURE",
                    addDTO.getPictureId(),
                    "COMMENT:" + comment.getId());
        }
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteComment(Long commentId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(commentId == null || commentId <= 0,
                ErrorCode.PARAMS_ERROR, "评论 ID 不合法");

        PictureComment comment = pictureCommentMapper.selectAnyById(commentId);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        if (Objects.equals(comment.getIsDelete(), 1)) {
            return true;
        }
        Picture picture = pictureMapper.selectById(comment.getPictureId());
        boolean canDelete = Objects.equals(comment.getUserId(), loginUser.getId())
                || (picture != null && Objects.equals(picture.getUserId(), loginUser.getId()))
                || userService.isAdmin(loginUser);
        ThrowUtils.throwIf(!canDelete, ErrorCode.NO_AUTH_ERROR, "无权删除该评论");

        int affectedRows = pictureCommentMapper.markDeleted(commentId);
        if (affectedRows == 1 && !Objects.equals(comment.getRootId(), 0L)) {
            pictureCommentMapper.decrementReplyCount(comment.getRootId());
        }
        return true;
    }

    @Override
    public Page<PictureCommentVO> listRootComments(PictureCommentQueryDTO queryDTO,
                                                   User loginUser) {
        ThrowUtils.throwIf(queryDTO == null || queryDTO.getPictureId() == null
                        || queryDTO.getPictureId() <= 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        validatePage(queryDTO);
        Picture picture = validatePublicAcceptedPicture(queryDTO.getPictureId());
        IPage<PictureComment> commentPage = pictureCommentMapper.selectRootPage(
                new Page<>(queryDTO.getCurrent(), queryDTO.getPageSize()), queryDTO.getPictureId());
        return toVOPage(commentPage, loginUser, picture);
    }

    @Override
    public Page<PictureCommentVO> listReplies(PictureCommentQueryDTO queryDTO,
                                              User loginUser) {
        ThrowUtils.throwIf(queryDTO == null || queryDTO.getRootId() == null
                        || queryDTO.getRootId() <= 0,
                ErrorCode.PARAMS_ERROR, "根评论 ID 不合法");
        validatePage(queryDTO);
        PictureComment root = pictureCommentMapper.selectAnyById(queryDTO.getRootId());
        ThrowUtils.throwIf(root == null || !Objects.equals(root.getRootId(), 0L),
                ErrorCode.NOT_FOUND_ERROR, "根评论不存在");
        Picture picture = validatePublicAcceptedPicture(root.getPictureId());
        IPage<PictureComment> replyPage = pictureCommentMapper.selectReplyPage(
                new Page<>(queryDTO.getCurrent(), queryDTO.getPageSize()), queryDTO.getRootId());
        return toVOPage(replyPage, loginUser, picture);
    }

    private Picture validatePublicAcceptedPicture(Long pictureId) {
        Picture picture = pictureMapper.selectById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(picture.getSpaceId() != null
                        || !Objects.equals(picture.getReviewStatus(),
                        PictureReviewStatusEnum.ACCEPTED.getStatus()),
                ErrorCode.FORBIDDEN_ERROR, "只有公开且审核通过的图片可以评论");
        return picture;
    }

    private void validatePage(PictureCommentQueryDTO queryDTO) {
        ThrowUtils.throwIf(queryDTO.getCurrent() <= 0 || queryDTO.getPageSize() <= 0
                        || queryDTO.getPageSize() > MAX_PAGE_SIZE,
                ErrorCode.PARAMS_ERROR, "分页参数不合法，单页最多 50 条");
    }

    /** 批量读取用户，避免每条评论各查一次数据库。 */
    private Page<PictureCommentVO> toVOPage(IPage<PictureComment> source,
                                            User loginUser,
                                            Picture picture) {
        List<PictureComment> comments = source.getRecords();
        if (comments == null || comments.isEmpty()) {
            return new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        }
        Set<Long> userIds = new LinkedHashSet<>();
        comments.forEach(comment -> {
            if (comment.getUserId() != null) {
                userIds.add(comment.getUserId());
            }
            if (comment.getReplyUserId() != null) {
                userIds.add(comment.getReplyUserId());
            }
        });
        Map<Long, UserVO> userVOMap = userIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .map(userService::getUserVO)
                .collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));

        boolean admin = loginUser != null && userService.isAdmin(loginUser);
        List<PictureCommentVO> records = comments.stream().map(comment -> {
            PictureCommentVO vo = new PictureCommentVO();
            BeanUtils.copyProperties(comment, vo);
            boolean deleted = Objects.equals(comment.getIsDelete(), 1);
            vo.setDeleted(deleted);
            // 已删除评论不向前端返回原内容，避免前端误用或泄漏。
            vo.setContent(deleted ? null : comment.getContent());
            vo.setUser(userVOMap.get(comment.getUserId()));
            vo.setReplyUser(userVOMap.get(comment.getReplyUserId()));
            vo.setCanDelete(loginUser != null && (admin
                    || Objects.equals(loginUser.getId(), comment.getUserId())
                    || Objects.equals(loginUser.getId(), picture.getUserId())));
            return vo;
        }).toList();

        Page<PictureCommentVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(records);
        return target;
    }
}
