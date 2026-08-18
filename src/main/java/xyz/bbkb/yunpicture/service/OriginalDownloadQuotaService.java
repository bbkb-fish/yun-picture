package xyz.bbkb.yunpicture.service;

import xyz.bbkb.yunpicture.domain.entity.User;
import xyz.bbkb.yunpicture.domain.vo.OriginalDownloadQuotaVO;

/** 原图下载每日额度服务。 */
public interface OriginalDownloadQuotaService {

    OriginalDownloadQuotaVO getQuota(User loginUser);

    /** 下载前原子预占一次额度，达到上限时直接抛出业务异常。 */
    void reserveQuota(User loginUser);

    /** 实际下载失败时归还本次预占额度。 */
    void releaseQuota(User loginUser);
}
