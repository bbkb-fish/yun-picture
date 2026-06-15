package xyz.bbkb.yunpicture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RedisStringTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final String TEST_KEY = "test:key";
    private final String TEST_VALUE = "Hello Redis";

    @BeforeEach
    void setUp() {
        System.out.println("========== 开始 Redis 测试 ==========");
        // 每个测试前清理测试key，确保测试环境干净
        stringRedisTemplate.delete(TEST_KEY);
    }

    @AfterEach
    void tearDown() {
        // 每个测试后清理测试key
        stringRedisTemplate.delete(TEST_KEY);
        System.out.println("========== 测试完成 ==========\n");
    }

    @Test
    void testSetAndGet() {
        System.out.println("1. 测试 set 和 get 操作");

        // 测试 set
        stringRedisTemplate.opsForValue().set(TEST_KEY, TEST_VALUE);
        System.out.println("✓ set 操作成功: " + TEST_KEY + " = " + TEST_VALUE);

        // 测试 get
        String result = stringRedisTemplate.opsForValue().get(TEST_KEY);
        System.out.println("✓ get 操作成功: " + result);

        // 验证结果
        assertEquals(TEST_VALUE, result, "存入和取出的值应该一致");
        System.out.println("✓ 验证通过: 存入值和取出值一致\n");
    }

    @Test
    void testUpdate() {
        System.out.println("2. 测试 update 操作");

        // 先存入初始值
        stringRedisTemplate.opsForValue().set(TEST_KEY, TEST_VALUE);
        System.out.println("初始值: " + TEST_KEY + " = " + TEST_VALUE);

        // 更新值
        String updatedValue = "Updated Redis Value";
        stringRedisTemplate.opsForValue().set(TEST_KEY, updatedValue);
        System.out.println("更新后的值: " + TEST_KEY + " = " + updatedValue);

        // 验证更新
        String result = stringRedisTemplate.opsForValue().get(TEST_KEY);
        assertEquals(updatedValue, result, "更新后的值应该正确");
        System.out.println("✓ 更新成功，验证通过\n");
    }

    @Test
    void testDelete() {
        System.out.println("3. 测试 delete 操作");

        // 先存入数据
        stringRedisTemplate.opsForValue().set(TEST_KEY, TEST_VALUE);
        System.out.println("已存入数据: " + TEST_KEY + " = " + TEST_VALUE);

        // 验证数据存在
        Boolean exists = stringRedisTemplate.hasKey(TEST_KEY);
        assertTrue(exists, "数据应该存在");
        System.out.println("✓ 删除前，key 存在: " + exists);

        // 删除数据
        Boolean deleted = stringRedisTemplate.delete(TEST_KEY);
        System.out.println("执行删除操作，返回结果: " + deleted);

        // 验证数据已删除
        exists = stringRedisTemplate.hasKey(TEST_KEY);
        assertFalse(exists, "数据应该已被删除");
        System.out.println("✓ 删除后，key 存在: " + exists);

        // 验证get返回null
        String result = stringRedisTemplate.opsForValue().get(TEST_KEY);
        assertNull(result, "删除后get应该返回null");
        System.out.println("✓ get返回: " + result + "\n");
    }

    @Test
    void testSetWithExpire() {
        System.out.println("4. 测试带过期时间的 set 操作");

        // 设置过期时间为3秒
        stringRedisTemplate.opsForValue().set(TEST_KEY, TEST_VALUE, 3, TimeUnit.SECONDS);
        System.out.println("已设置数据，过期时间: 3秒");

        // 立即获取，应该存在
        String result = stringRedisTemplate.opsForValue().get(TEST_KEY);
        assertEquals(TEST_VALUE, result);
        System.out.println("✓ 立即获取成功: " + result);

        // 获取剩余过期时间
        Long ttl = stringRedisTemplate.getExpire(TEST_KEY, TimeUnit.SECONDS);
        System.out.println("剩余过期时间: " + ttl + " 秒");
        assertTrue(ttl > 0 && ttl <= 3, "剩余时间应该在1-3秒之间");

        // 等待4秒后再次获取
        try {
            System.out.println("等待 4 秒...");
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        result = stringRedisTemplate.opsForValue().get(TEST_KEY);
        assertNull(result, "过期后应该获取不到数据");
        System.out.println("✓ 过期后获取: " + result + "\n");
    }

    @Test
    void testSetIfAbsent() {
        System.out.println("5. 测试 setIfAbsent (类似 setnx) 操作");

        // 第一次设置，key不存在，应该成功
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(TEST_KEY, TEST_VALUE);
        assertTrue(success, "第一次设置应该成功");
        System.out.println("✓ 第一次 setIfAbsent: " + success + ", 值: " + TEST_VALUE);

        // 第二次设置，key已存在，应该失败
        String newValue = "New Value";
        success = stringRedisTemplate.opsForValue().setIfAbsent(TEST_KEY, newValue);
        assertFalse(success, "第二次设置应该失败");
        System.out.println("✓ 第二次 setIfAbsent: " + success);

        // 验证值没有被覆盖
        String result = stringRedisTemplate.opsForValue().get(TEST_KEY);
        assertEquals(TEST_VALUE, result, "值不应该被覆盖");
        System.out.println("✓ 当前值仍然是: " + result + "\n");
    }

    @Test
    void testIncrement() {
        System.out.println("6. 测试 increment (自增) 操作");

        String counterKey = "test:counter";
        stringRedisTemplate.delete(counterKey); // 清理

        // 自增操作
        Long count = stringRedisTemplate.opsForValue().increment(counterKey);
        System.out.println("第1次自增: " + count);
        assertEquals(1L, count);

        count = stringRedisTemplate.opsForValue().increment(counterKey);
        System.out.println("第2次自增: " + count);
        assertEquals(2L, count);

        count = stringRedisTemplate.opsForValue().increment(counterKey, 5);
        System.out.println("增加5: " + count);
        assertEquals(7L, count);

        // 清理
        stringRedisTemplate.delete(counterKey);
        System.out.println("✓ 自增操作测试通过\n");
    }

    @Test
    void testBatchOperations() {
        System.out.println("7. 测试批量操作");

        // 批量设置
        stringRedisTemplate.opsForValue().multiSet(
                java.util.Map.of(
                        "test:key1", "value1",
                        "test:key2", "value2",
                        "test:key3", "value3"
                )
        );
        System.out.println("✓ 批量设置 3 个键值对");

        // 批量获取
        java.util.List<String> values = stringRedisTemplate.opsForValue().multiGet(
                java.util.Arrays.asList("test:key1", "test:key2", "test:key3")
        );
        System.out.println("批量获取结果: " + values);
        assertEquals(3, values.size());

        // 清理
        stringRedisTemplate.delete(java.util.Arrays.asList("test:key1", "test:key2", "test:key3"));
        System.out.println("✓ 批量操作测试通过\n");
    }

    @Test
    void testComprehensive() {
        System.out.println("8. 综合测试 - 模拟真实业务场景");

        // 场景：存储用户登录token
        String tokenKey = "user:token:10001";
        String tokenValue = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

        // 1. 存储token，7天过期
        stringRedisTemplate.opsForValue().set(tokenKey, tokenValue, 7, TimeUnit.DAYS);
        System.out.println("✓ 存储用户token，过期时间7天");

        // 2. 验证token是否存在
        Boolean hasKey = stringRedisTemplate.hasKey(tokenKey);
        System.out.println("✓ token是否存在: " + hasKey);

        // 3. 获取token
        String storedToken = stringRedisTemplate.opsForValue().get(tokenKey);
        System.out.println("✓ 获取到的token: " + storedToken);
        assertEquals(tokenValue, storedToken);

        // 4. 获取剩余过期时间
        Long ttl = stringRedisTemplate.getExpire(tokenKey, TimeUnit.DAYS);
        System.out.println("✓ 剩余过期天数: " + ttl);

        // 5. 更新token（刷新过期时间）
        stringRedisTemplate.expire(tokenKey, 7, TimeUnit.DAYS);
        System.out.println("✓ 刷新token过期时间");

        // 6. 删除token（用户登出）
        stringRedisTemplate.delete(tokenKey);
        System.out.println("✓ 删除token（用户登出）");

        // 7. 验证已删除
        hasKey = stringRedisTemplate.hasKey(tokenKey);
        assertFalse(hasKey);
        System.out.println("✓ 验证token已删除\n");
    }
}