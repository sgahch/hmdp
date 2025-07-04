/**
 * CacheClient使用示例
 * 展示如何使用封装好的缓存方法
 */

// 1. 使用互斥锁方案（推荐用于热点数据）
public Shop queryShopById(Long id) {
    return cacheClient.queryWithMutex(
        CACHE_SHOP_KEY,           // 缓存key前缀: "cache:shop:"
        id,                       // 查询ID
        Shop.class,              // 返回类型
        this::getById,           // 数据库查询方法引用
        CACHE_SHOP_TTL,          // 缓存时间: 30分钟
        TimeUnit.MINUTES,        // 时间单位
        LOCK_SHOP_KEY            // 锁key前缀: "lock:shop:"
    );
}

// 2. 使用缓存穿透方案（适用于一般数据）
public User queryUserById(Long id) {
    return cacheClient.queryWithPassThrough(
        CACHE_USER_KEY,          // 缓存key前缀
        id,                      // 查询ID
        User.class,             // 返回类型
        this::getById,          // 数据库查询方法
        CACHE_USER_TTL,         // 缓存时间
        TimeUnit.MINUTES        // 时间单位
    );
}

// 3. 使用逻辑过期方案（适用于超热点数据，永不过期）
public Shop queryHotShopById(Long id) {
    return cacheClient.queryWithLogicalExpire(
        CACHE_SHOP_KEY,          // 缓存key前缀
        id,                      // 查询ID
        Shop.class,             // 返回类型
        this::getById,          // 数据库查询方法
        CACHE_SHOP_TTL,         // 逻辑过期时间
        TimeUnit.MINUTES        // 时间单位
    );
}

/**
 * 三种缓存策略对比：
 * 
 * 1. queryWithPassThrough (缓存穿透解决方案)
 *    - 适用场景：一般业务数据
 *    - 解决问题：缓存穿透
 *    - 特点：简单高效，适合大部分场景
 * 
 * 2. queryWithMutex (互斥锁解决方案)
 *    - 适用场景：热点数据，对一致性要求较高
 *    - 解决问题：缓存穿透 + 缓存击穿 + 缓存雪崩
 *    - 特点：强一致性，但可能有性能瓶颈
 * 
 * 3. queryWithLogicalExpire (逻辑过期解决方案)
 *    - 适用场景：超热点数据，对可用性要求极高
 *    - 解决问题：缓存击穿
 *    - 特点：高可用，但可能返回过期数据
 */
