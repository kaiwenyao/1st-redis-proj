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
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
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
    private final BlockingQueue<VoucherOrder> voucherOrderQueue = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderRunnable());
    }

    private class VoucherOrderRunnable implements Runnable {
        @Override
        public void run() {
            while (true) {
                // 获取队列中的订单信息
                try {
                    VoucherOrder voucherOrder = voucherOrderQueue.take();
                    handleVouchOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常 {}", e);
                }
            }
        }
    }

    private void handleVouchOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        // 其实这里的锁不是很有必要了
        RLock lock = redissonClient.getLock("order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败
            log.error("获取锁失败？？不可能");
            return;
        }
        try {
            self.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 通过lua脚本调用redis实现
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        long orderId = redisIdWorker.nextId("order");
        // 阻塞队列
        // 添加订单到数据库
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        voucherOrderQueue.add(voucherOrder);

        return Result.ok(orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 用户id
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            // 用户已经购买过 不允许下单
            log.error("不太可能错误");
            return;
        }

        // 都通过 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!success) {
            // 失败
            log.error("库存不足？？不可能");
            return;
        }
        save(voucherOrder);
    }
/*    @Override
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
    }*/

}
