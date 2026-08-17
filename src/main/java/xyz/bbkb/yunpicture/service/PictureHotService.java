package xyz.bbkb.yunpicture.service;

import xyz.bbkb.yunpicture.domain.vo.HotPictureVO;
import xyz.bbkb.yunpicture.domain.vo.PictureStatVO;

import java.util.List;

public interface PictureHotService {
    void recordView(Long pictureId, String viewerId);

    void recordDownload(Long pictureId);

    List<HotPictureVO> getHotPictures(String period, int limit);

    PictureStatVO getPictureStat(Long pictureId);
}
