package xyz.bbkb.yunpicture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.bbkb.yunpicture.domain.entity.Space;
import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.OriginalDownloadQuotaVO;
import xyz.bbkb.yunpicture.enums.SpaceLevelEnum;
import xyz.bbkb.yunpicture.exception.ErrorCode;
import xyz.bbkb.yunpicture.exception.ThrowUtils;
import xyz.bbkb.yunpicture.mapper.SpaceMapper;
import xyz.bbkb.yunpicture.mapper.UserDownloadDailyMapper;
import xyz.bbkb.yunpicture.service.OriginalDownloadQuotaService;
import xyz.bbkb.yunpicture.service.UserService;

import java.time.LocalDate;

/**
 * 以用户空间等级决定原图下载额度。没有创建空间的用户按普通版处理，
 * 管理员和旗舰版不限量，但仍记录实际下载次数。
 */
@Service
@RequiredArgsConstructor
public class OriginalDownloadQuotaServiceImpl implements OriginalDownloadQuotaService {

    private final UserDownloadDailyMapper downloadDailyMapper;
    private final SpaceMapper spaceMapper;
    private final UserService userService;

    @Override
    public OriginalDownloadQuotaVO getQuota(User loginUser) {
        validateLoginUser(loginUser);
        QuotaLevel quotaLevel = resolveQuotaLevel(loginUser);
        Integer usedCount = downloadDailyMapper.selectUsageCount(
                loginUser.getId(), LocalDate.now());
        int used = usedCount == null ? 0 : usedCount;

        OriginalDownloadQuotaVO quotaVO = new OriginalDownloadQuotaVO();
        quotaVO.setSpaceId(quotaLevel.space() == null ? null : quotaLevel.space().getId());
        quotaVO.setSpaceLevel(quotaLevel.level().getValue());
        quotaVO.setLevelName(quotaLevel.admin() ? "管理员" : quotaLevel.level().getText());
        quotaVO.setUnlimited(quotaLevel.unlimited());
        quotaVO.setDailyLimit(quotaLevel.unlimited() ? null : quotaLevel.dailyLimit());
        quotaVO.setUsedCount(used);
        quotaVO.setRemainingCount(quotaLevel.unlimited()
                ? null : Math.max(quotaLevel.dailyLimit() - used, 0));
        return quotaVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reserveQuota(User loginUser) {
        validateLoginUser(loginUser);
        QuotaLevel quotaLevel = resolveQuotaLevel(loginUser);
        LocalDate today = LocalDate.now();

        if (quotaLevel.unlimited()) {
            downloadDailyMapper.incrementUnlimited(
                    IdWorker.getId(), loginUser.getId(), today);
            return;
        }

        int dailyLimit = quotaLevel.dailyLimit();
        // 先更新已有行；如果当天尚无记录，则尝试插入第一条。
        if (downloadDailyMapper.incrementIfBelowLimit(
                loginUser.getId(), today, dailyLimit) == 1) {
            return;
        }
        if (downloadDailyMapper.insertFirstUsage(
                IdWorker.getId(), loginUser.getId(), today) == 1) {
            return;
        }
        // 并发首次下载时，其他请求可能刚刚插入成功，因此再尝试一次条件更新。
        if (downloadDailyMapper.incrementIfBelowLimit(
                loginUser.getId(), today, dailyLimit) == 1) {
            return;
        }
        ThrowUtils.throwIf(true, ErrorCode.FORBIDDEN_ERROR,
                "今日原图下载额度已用完，请升级空间服务");
    }

    @Override
    public void releaseQuota(User loginUser) {
        if (loginUser != null && loginUser.getId() != null) {
            downloadDailyMapper.releaseUsage(loginUser.getId(), LocalDate.now());
        }
    }

    private QuotaLevel resolveQuotaLevel(User loginUser) {
        Space space = spaceMapper.selectOne(new QueryWrapper<Space>()
                .eq("userId", loginUser.getId())
                .orderByDesc("spaceLevel")
                .last("LIMIT 1"));
        SpaceLevelEnum level = space == null
                ? SpaceLevelEnum.COMMON
                : SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        // 历史异常数据或空等级按普通版兜底，不能意外获得不限量权限。
        if (level == null) {
            level = SpaceLevelEnum.COMMON;
        }
        boolean admin = userService.isAdmin(loginUser);
        boolean unlimited = admin || level.getOriginalDownloadDailyLimit() < 0;
        return new QuotaLevel(space, level, admin, unlimited,
                level.getOriginalDownloadDailyLimit());
    }

    private void validateLoginUser(User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);
    }

    private record QuotaLevel(Space space,
                              SpaceLevelEnum level,
                              boolean admin,
                              boolean unlimited,
                              int dailyLimit) {
    }
}
