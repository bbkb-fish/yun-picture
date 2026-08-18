package xyz.bbkb.yunpicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xyz.bbkb.yunpicture.domain.entity.UserDownloadDaily;

import java.time.LocalDate;

/** 每日原图下载用量数据库操作。 */
public interface UserDownloadDailyMapper extends BaseMapper<UserDownloadDaily> {

    /** 已有当天记录时，仅在尚未到达上限的情况下原子加一。 */
    @Update("""
            UPDATE user_download_daily
            SET original_download_count = original_download_count + 1,
                update_time = NOW()
            WHERE user_id = #{userId}
              AND stat_date = #{statDate}
              AND original_download_count < #{dailyLimit}
            """)
    int incrementIfBelowLimit(@Param("userId") Long userId,
                              @Param("statDate") LocalDate statDate,
                              @Param("dailyLimit") Integer dailyLimit);

    /** 当天首次下载时插入记录；联合唯一索引负责处理并发首次下载。 */
    @Insert("""
            INSERT IGNORE INTO user_download_daily
                (id, user_id, stat_date, original_download_count)
            VALUES (#{id}, #{userId}, #{statDate}, 1)
            """)
    int insertFirstUsage(@Param("id") Long id,
                         @Param("userId") Long userId,
                         @Param("statDate") LocalDate statDate);

    /** 旗舰版不限量，但仍记录实际用量，方便后续统计。 */
    @Insert("""
            INSERT INTO user_download_daily
                (id, user_id, stat_date, original_download_count)
            VALUES (#{id}, #{userId}, #{statDate}, 1)
            ON DUPLICATE KEY UPDATE
                original_download_count = original_download_count + 1,
                update_time = NOW()
            """)
    int incrementUnlimited(@Param("id") Long id,
                           @Param("userId") Long userId,
                           @Param("statDate") LocalDate statDate);

    @Select("""
            SELECT COALESCE(original_download_count, 0)
            FROM user_download_daily
            WHERE user_id = #{userId} AND stat_date = #{statDate}
            LIMIT 1
            """)
    Integer selectUsageCount(@Param("userId") Long userId,
                             @Param("statDate") LocalDate statDate);

    /** 下载过程失败时归还刚才预占的额度，且计数不会变成负数。 */
    @Update("""
            UPDATE user_download_daily
            SET original_download_count = GREATEST(original_download_count - 1, 0),
                update_time = NOW()
            WHERE user_id = #{userId} AND stat_date = #{statDate}
            """)
    int releaseUsage(@Param("userId") Long userId,
                     @Param("statDate") LocalDate statDate);
}
