package xyz.bbkb.yunpicture.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.bbkb.yunpicture.common.BaseResponse;
import xyz.bbkb.yunpicture.common.DeleteRequest;
import xyz.bbkb.yunpicture.common.ResultUtils;
import xyz.bbkb.yunpicture.domain.dto.notification.NotificationQueryDTO;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.UserNotificationVO;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.service.NotificationService;
import xyz.bbkb.yunpicture.service.NotificationSseService;
import xyz.bbkb.yunpicture.service.UserService;

/** 通知中心和 SSE 实时推送接口。 */
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final UserService userService;
    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(HttpServletRequest request, HttpServletResponse response) {
        User loginUser = userService.getLoginUser(request);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return notificationSseService.subscribe(loginUser.getId());
    }

    @PostMapping("/list/page")
    public BaseResponse<Page<UserNotificationVO>> listMyNotifications(
            @RequestBody NotificationQueryDTO queryDTO, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(notificationService.listMyNotifications(queryDTO, loginUser));
    }

    @GetMapping("/unread/count")
    public BaseResponse<Long> countUnread(HttpServletRequest request) {
        return ResultUtils.success(
                notificationService.countUnread(userService.getLoginUser(request)));
    }

    @PostMapping("/read")
    public BaseResponse<Boolean> markRead(@RequestBody DeleteRequest requestBody,
                                          HttpServletRequest request) {
        ThrowUtils.throwIf(requestBody == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(notificationService.markRead(
                requestBody.getId(), userService.getLoginUser(request)));
    }

    @PostMapping("/read/all")
    public BaseResponse<Boolean> markAllRead(HttpServletRequest request) {
        return ResultUtils.success(
                notificationService.markAllRead(userService.getLoginUser(request)));
    }
}
