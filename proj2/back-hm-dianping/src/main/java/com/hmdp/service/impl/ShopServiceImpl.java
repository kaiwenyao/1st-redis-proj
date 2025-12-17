package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithMutex(id);
//        Shop shop = queryWithLogicalExpire(id);

        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.MINUTES
        );
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById,
//                CACHE_SHOP_TTL, TimeUnit.SECONDS );

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        // 互斥锁解决缓存击穿
        return Result.ok(shop);
    }


//    public Shop queryWithMutex(Long id) {
//        // 从redis查询商铺的id
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) { // 如果是null 空 或者换行 都是false 只有字符串才是true
//            // 存在 返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 判断是否命中空值
//        if (shopJson != null) {
//            // 空字符串 是我们人为添加到redis中的 防止缓存穿透做的
//            // 就直接返回null
//            return null;
//        }
//        // 缓存重建
//        // 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 判断是否获取成功
//            if (!isLock) {
//                // 如果失败 则休眠 重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//                // 然后递归查询
//            }
//            // 成功 查询数据库
//            shop = getById(id); // my batis plus 的数据库查询方法
//
//            // 不存在 返回错误
//            if (shop == null) {
//                // 空值写入redis
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL,
//                        TimeUnit.MINUTES);
//                return null;
//            }
//            // 存在 写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop)
//                    , CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 释放互斥锁
//            unlock(lockKey);
//        }
//        return shop;
//    }

//    public void saveShop2Redis(Long id, Long expireSeconds) {
//        Shop shop = getById(id);
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    // 线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryWithLogicalExpire(Long id) {
//        // 从redis查询商铺的id
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 判断是否存在
//        if (StrUtil.isBlank(shopJson)) { // 如果是null 空 或者换行 都是false 只有字符串才是true
//            return null;
//        }
//        // 命中 判断过期时间
//        // 先把json 反序列化成对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        // 注意！这里的toBean会把shopJson变成JSONObject类型
//        // 所以redisData.getData()返回的就是JSONObject类型
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 未过期
//            return shop;
//        }
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        if (isLock) {
//            // 成功获取锁 开启独立线程 缓存重建
//            // 注意！这里要double check！！看看缓存有没有过期 如果没过期 就不用重建了！！
//            if (expireTime.isAfter(LocalDateTime.now())) {
//                // 未过期
//                return shop;
//            }
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    // 重建缓存
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    unlock(lockKey);
//                }
//            });
//        }
//
//        // 返回
//        return shop;
//    }
//
//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL,
//                TimeUnit.MINUTES);// 值随意
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unlock(String key) {
//        stringRedisTemplate.delete(key);
//    }

    // 缓存穿透Cache Penetration
//    public Shop queryWithPassThrough(Long id) {
//        // 从redis查询商铺的id
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) { // 如果是null 空 或者换行 都是false 只有字符串才是true
//            // 存在 返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 判断是否命中空值
//        if (shopJson != null) { // 不等于null 代表命中空值
//            return null;
//        }
//
//        // 不存在 根据id查询数据库
//        Shop shop = getById(id);
//
//        // 不存在 返回错误
//        if (shop == null) {
//            // 空值写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL,
//                    TimeUnit.MINUTES);
//            return null;
//        }
//        // 存在 写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop)
//                , CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 返回
//        return shop;
//    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        updateById(shop); // 这是my batis plus的方法
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();

    }
}
