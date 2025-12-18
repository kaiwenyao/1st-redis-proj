local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足 返回1
    return 1
end

-- 判断是否已经下单
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 存在 不允许再次下单
    return 2
end

-- 扣库存 下单
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
