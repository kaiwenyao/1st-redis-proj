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
        String shopTypeJson = stringRedisTemplate.opsForValue().get(prefix);
        if (StrUtil.isNotBlank(shopTypeJson)) {
            return JSONUtil.toList(shopTypeJson, ShopType.class);
        }
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        if (typeList.isEmpty()) {
            return new ArrayList<>();
        }
        stringRedisTemplate.opsForValue().set(prefix,  JSONUtil.toJsonStr(typeList));
        return typeList;
    }
}
