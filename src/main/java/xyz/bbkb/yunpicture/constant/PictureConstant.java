package xyz.bbkb.yunpicture.constant;

public interface PictureConstant {
//    公共图片的状态
    String ACCEPTED = "通过";
    String REJECT = "拒绝";
    String REVIEWING = "待审核";

//    用户举动给图片增加的热度
    double VIEW_SCORE = 1;
    double DOWNLOAD_SCORE = 3;
    double LIKE_SCORE = 5;
    double FAVORITE_SCORE = 8;
}
