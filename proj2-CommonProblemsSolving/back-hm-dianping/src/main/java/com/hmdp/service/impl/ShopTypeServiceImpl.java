package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        String prefix = "shop:type:";
        // 先看看redis中是否有缓存数据
        String shopTypeJson = stringRedisTemplate.opsForValue().get(prefix);
        // 有的话直接返回
        if (StrUtil.isNotBlank(shopTypeJson)) {
            return JSONUtil.toList(shopTypeJson, ShopType.class);
        }
        // 没有的话去mysql中寻找
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        // 找不到返回空
        if (typeList.isEmpty()) {
            // 空数据也要设置缓存。防止缓存穿透
            stringRedisTemplate.opsForValue().set(prefix, "", 30L, TimeUnit.SECONDS);
            return new ArrayList<>();
        }
        // 不空的话 存入redis 时间给长一点。
        stringRedisTemplate.opsForValue().set(prefix, JSONUtil.toJsonStr(typeList), 30L, TimeUnit.MINUTES);
        return typeList;
    }
}
