package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.data.geo.Point;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

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
        //1.解决缓存穿透和缓存雪崩
        //Result shop = queryWithPassThrough(id);
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //2.互斥锁解决缓存击穿和缓存穿透和缓存雪崩
        //Shop shop = queryWithMutex(id);
        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES, LOCK_SHOP_KEY);
        //高级篇：逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     *
     * 互斥锁解决缓存击穿，同时解决缓存雪崩和缓存穿透
     *
     * @param id id
     * @return {@link Shop}
     */
    private Shop queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isNotEmpty(shopJson)) {
            //存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断空值
        if ("".equals(shopJson)) {
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //是否获取成功
            if (!isLock) {
                //获取失败 休眠并且重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功 通过id查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            if (shop == null) {
                //redis写入空值
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //数据库不存在 返回错误
                return null;
            }
            //随机过期时间，解决缓存雪崩
            int random = (int) (Math.random() * 11 + 30); // 范围是 [30, 40]
            //数据库存在 写入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), random, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //返回
        return shop;
    }

    /**
     * 获取锁
     *
     * @param key 关键
     * @return boolean
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key 关键
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 解决缓存穿透和缓存雪崩
     * @param id id
     * @return
     */
    public Result queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        if(StrUtil.isNotBlank(shopJson)){//缓存命中,直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if(shopJson!=null){//缓存击中,返回错误信息
            return Result.fail("店铺不存在");
        }
        //随机过期时间，解决缓存雪崩
        int random = (int) (Math.random() * 11 + 30); // 范围是 [30, 40]
        Shop shop = this.getById(id); // 直接从数据库查询
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }else{//设置随机过期时间，解决缓存雪崩
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), random, TimeUnit.MINUTES);
        }
        return Result.ok(shop);
    }
    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id id
     * @return {@link Shop}
     */
    /*private Shop queryWithLogicalExpire(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isEmpty(shopJson)) {
            //不存在返回空
            return null;
        }
        //命中 反序列化
        RedisDate redisDate = JSONUtil.toBean(shopJson, RedisDate.class);
        JSONObject jsonObject = (JSONObject) redisDate.getData();
        Shop shop = BeanUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisDate.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期 直接返回
            return shop;
        }
        //已过期
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        //是否获取锁成功
        if (flag) {
            //成功 异步重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShopToRedis(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //返回过期商铺信息
        return shop;
    }*/

    /**
     * 使用设置空值解决缓存穿透
     */
   /* private Shop queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isNotEmpty(shopJson)) {
            //存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断空值
        if ("".equals(shopJson)) {
            return null;
        }
        //不存在 查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            //redis写入空值
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //数据库不存在 返回错误
            return null;
        }
        //数据库存在 写入redis
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }*/
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {// 更新
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        System.out.println("🏪 [Service层] 查询商铺 - typeId=" + typeId + ", current=" + current + ", x=" + x + ", y=" + y);

        //判断是否需要坐标查询
        if (x == null || y == null) {
            System.out.println("📍 [普通查询] 不使用地理位置，直接查询数据库");
            //不需要坐标查询
            Page<Shop> page = lambdaQuery()
                    .eq(Shop::getTypeId, typeId)
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

            System.out.println("📊 [查询结果] 总记录数=" + page.getTotal() + ", 当前页记录数=" + page.getRecords().size());
            return Result.ok(page.getRecords());
        }
        System.out.println("🌍 [地理位置查询] 开始处理地理位置查询");
        //计算分页参数
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = current * SystemConstants.MAX_PAGE_SIZE;
        System.out.println("📄 [分页参数] from=" + from + ", end=" + end + ", current=" + current + ", pageSize=" + SystemConstants.MAX_PAGE_SIZE);

        //查询redis 距离排序 分页
        String key= SHOP_GEO_KEY+typeId;
        System.out.println("🔑 [Redis GEO Key] " + key);
        System.out.println("📍 [查询坐标] x=" + x + ", y=" + y + ", 搜索半径=5000米");

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key
                        , GeoReference.fromCoordinate(x, y)
                        , new Distance(5000)
                        , RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        //解析出id
        if (results==null){
            System.out.println("❌ [GEO查询结果] Redis GEO查询结果为null，可能key不存在或没有数据");
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        System.out.println("📊 [GEO查询结果] 总共找到 " + content.size() + " 个商铺");

        if (content.size()<from){
            System.out.println("📄 [分页检查] content.size()=" + content.size() + " < from=" + from + "，没有下一页数据");
            //没有下一页
            return Result.ok();
        }

        //截取
        List<Long> ids=new ArrayList<>(content.size());
        Map<String,Distance> distanceMap=new HashMap<>();

        System.out.println("✂️ [数据截取] 开始从第 " + from + " 个位置截取数据");
        content.stream().skip(from).forEach(result->{
            //店铺id
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            //距离
            Distance distance = result.getDistance();
            distanceMap.put(shopId,distance);
            System.out.println("🏪 [商铺数据] ID=" + shopId + ", 距离=" + distance.getValue() + "米");
        });

        System.out.println("📋 [最终ID列表] ids.size()=" + ids.size() + ", ids=" + ids);

        if (ids.isEmpty()) {
            System.out.println("❌ [ID列表为空] 经过分页截取后，没有找到任何商铺ID");
            return Result.ok(Collections.emptyList()); // 如果当前页没有ID，直接返回空列表
        }
        //根据id查询shop
        String join = StrUtil.join(",", ids);
        List<Shop> shopList = lambdaQuery().in(Shop::getId, ids).last("order by field(id,"+join+")").list();
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shopList);
    }

    @Override
    public Result debugGeoData(Integer typeId) {
        String key = SHOP_GEO_KEY + typeId;
        System.out.println("🔍 [调试GEO数据] 检查Redis key: " + key);

        // 检查key是否存在
        Boolean exists = stringRedisTemplate.hasKey(key);
        System.out.println("🔑 [Key存在性] " + key + " 存在: " + exists);

        if (!exists) {
            return Result.ok("Redis GEO key不存在: " + key);
        }

        // 获取所有GEO数据
        Set<String> members = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        System.out.println("📊 [GEO数据统计] key=" + key + ", 成员数量=" + (members != null ? members.size() : 0));

        if (members != null && !members.isEmpty()) {
            System.out.println("📋 [GEO成员列表] " + members);

            // 获取前几个成员的详细信息
            List<String> sampleMembers = members.stream().limit(5).collect(Collectors.toList());
            for (String member : sampleMembers) {
                List<Point> positions = stringRedisTemplate.opsForGeo().position(key, member);
                if (positions != null && !positions.isEmpty()) {
                    Point point = positions.get(0);
                    System.out.println("📍 [样本数据] 商铺ID=" + member + ", 坐标=(" + point.getX() + ", " + point.getY() + ")");
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("exists", exists);
        result.put("memberCount", members != null ? members.size() : 0);
        result.put("sampleMembers", members != null ? members.stream().limit(10).collect(Collectors.toList()) : Collections.emptyList());

        return Result.ok(result);
    }

    @Override
    public Result initGeoData() {
        System.out.println("🚀 [初始化GEO数据] 开始导入商铺地理位置数据到Redis");

        try {
            // 查询所有商铺信息
            List<Shop> shopList = this.list();
            System.out.println("📊 [数据统计] 从数据库查询到 " + shopList.size() + " 个商铺");

            // 按商铺类型分组
            Map<Long, List<Shop>> shopMap = shopList.stream()
                    .collect(Collectors.groupingBy(Shop::getTypeId));

            System.out.println("📈 [分组统计] 共有 " + shopMap.size() + " 种商铺类型");

            int totalImported = 0;

            // 分批写入Redis
            for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
                Long typeId = entry.getKey();
                List<Shop> shops = entry.getValue();
                String key = SHOP_GEO_KEY + typeId;

                System.out.println("🏪 [处理类型] typeId=" + typeId + ", 商铺数量=" + shops.size() + ", Redis Key=" + key);

                // 准备GEO位置数据
                List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());

                for (Shop shop : shops) {
                    if (shop.getX() != null && shop.getY() != null) {
                        locations.add(new RedisGeoCommands.GeoLocation<>(
                            shop.getId().toString(),
                            new Point(shop.getX(), shop.getY())
                        ));
                        totalImported++;
                    } else {
                        System.out.println("⚠️ [跳过商铺] ID=" + shop.getId() + ", 原因: 坐标为空");
                    }
                }

                if (!locations.isEmpty()) {
                    // 批量写入Redis
                    stringRedisTemplate.opsForGeo().add(key, locations);
                    System.out.println("✅ [写入成功] typeId=" + typeId + ", 实际写入=" + locations.size() + "个商铺");
                } else {
                    System.out.println("❌ [跳过类型] typeId=" + typeId + ", 原因: 没有有效坐标的商铺");
                }
            }

            System.out.println("🎉 [初始化完成] 总共导入 " + totalImported + " 个商铺的地理位置数据");

            Map<String, Object> result = new HashMap<>();
            result.put("totalShops", shopList.size());
            result.put("totalTypes", shopMap.size());
            result.put("importedShops", totalImported);
            result.put("message", "Redis GEO数据初始化成功");

            return Result.ok(result);

        } catch (Exception e) {
            System.out.println("❌ [初始化失败] 错误: " + e.getMessage());
            e.printStackTrace();
            return Result.fail("初始化失败: " + e.getMessage());
        }
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /*    */

    /**
     * 存入redis 携带逻辑过期时间
     *//*
    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
        //查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //封装逻辑过期
        RedisDate redisDate = new RedisDate();
        redisDate.setData(shop);
        redisDate.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写了redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisDate));
    }*/
}
