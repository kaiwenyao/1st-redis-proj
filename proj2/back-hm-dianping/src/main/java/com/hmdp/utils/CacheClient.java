package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 因为CacheClient添加了Component注解
    // 所以构造函数需要的StringRedisTemplate，会由spring mvc自动传入参数进行构造。
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    // 这里如果不使用构造函数进行StringRedisTemplate的注入，也可以在成员变量上面加上注解@Resource
    // 但是！这个@Resource不是强制需要有一个StringRedisTemplate被注入。如果没有注入，也不会报错直到后面需要用到的时候才报错
    // 而使用了构造函数，就代表一定要传入一个StringRedisTemplate对象。否则就报错。

    // 将任意java对象存储到redis中。设置redis过期时间
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        // value 需要被序列化成字符串
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    // 将任意java对象存储到redis中。设置逻辑过期时间。
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        // value 封装 到 RedisData 带有逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 从redis查询商铺的id
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if (StrUtil.isNotBlank(json)) { // 如果是null 空 或者换行 都是false 只有字符串才是true
            // 存在 返回
            return JSONUtil.toBean(json, type);
        }
        // 判断是否命中空值
        if (json != null) { // 命中了我们预先设置的redis空值防止穿透
            return null;
        }

        // 不存在 根据id查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 空值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL,
                    TimeUnit.MINUTES);
            return null;
        }
        // 存在 写入redis
        this.set(key, r, time, timeUnit);
        // 返回
        return r;
    }

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback, Long time, TimeUnit timeUnit
    ) {
        String key = keyPrefix + id;
        // 从redis查询商铺的id
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if (StrUtil.isBlank(json)) { // 如果是null 空 或者换行 都是false 只有字符串才是true
            return null;
        }
        // 命中 判断过期时间
        // 先把json 反序列化成对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 注意！这里的toBean会把shopJson变成JSONObject类型
        // 所以redisData.getData()返回的就是JSONObject类型
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期
            return r;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 成功获取锁 开启独立线程 缓存重建
            // 注意！这里要double check！！看看缓存有没有过期 如果没过期 就不用重建了！！
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 未过期
                return r;
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> { // 独立线程，把任务交给线程池，会自动分配线程进行执行。
                try {
                    // 重建缓存
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // 返回
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL,
                TimeUnit.MINUTES);// 值随意
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


}
