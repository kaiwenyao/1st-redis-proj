package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.NonNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    @Lazy
    private IVoucherOrderService self;
    // 自己注入自己
@Resource
private RedissonClient redissonClient;
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 判断是否开始结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 没开始
            return Result.fail("秒杀尚未开始!");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 结束力
            return Result.fail("秒杀已经结束!");
        }
        // 判断库存
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        // 锁是userId但是不能是对象因为每次都是新对象
        // 要用intern找到已经存在的对象。
//        synchronized (userId.toString().intern()) {
//            // 避免使用目标对象直接调用
//            // 自己注入自己
//            return self.createVoucherOrder(voucherId);
//        }
        // 使用分布式锁：
        // 创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败
            return Result.fail("一个人只允许下一单");

        }
        try {
            return self.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }

    }

    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        // 用户id
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            // 用户已经购买过 不允许下单
            return Result.fail("用户已经购买过一次！");
        }

        // 都通过 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            // 失败
            return Result.fail("库存不足！！");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
