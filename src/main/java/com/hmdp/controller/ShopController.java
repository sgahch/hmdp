package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        return Result.ok();
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam(value = "typeId", required = false) Integer typeId,
            @RequestParam(value = "type", required = false) Integer type, // 兼容前端传递的type参数
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x",required = false) Double x,
            @RequestParam(value = "y",required = false) Double y
    ) {
        // 兼容处理：优先使用typeId，如果没有则使用type
        Integer finalTypeId = typeId != null ? typeId : type;
        System.out.println("🔍 [商铺类型查询] typeId=" + finalTypeId + ", current=" + current + ", x=" + x + ", y=" + y);
        return shopService.queryShopByType(finalTypeId, current, x, y);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 测试接口：查看数据库中的商铺类型分布
     * @return 商铺类型统计
     */
    @GetMapping("/debug/types")
    public Result debugShopTypes() {
        // 查询所有商铺的类型分布
        List<Shop> allShops = shopService.list();
        System.out.println("📊 [调试] 数据库中总共有 " + allShops.size() + " 个商铺");

        // 统计各类型的数量
        Map<Long, Long> typeCount = allShops.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId, Collectors.counting()));

        System.out.println("📈 [调试] 商铺类型分布：" + typeCount);

        return Result.ok(typeCount);
    }

    /**
     * 测试接口：检查Redis GEO数据
     * @param typeId 商铺类型ID
     * @return Redis GEO数据统计
     */
    @GetMapping("/debug/geo/{typeId}")
    public Result debugGeoData(@PathVariable Integer typeId) {
        return shopService.debugGeoData(typeId);
    }

    /**
     * 初始化接口：将商铺地理位置数据导入Redis
     * @return 初始化结果
     */
    @PostMapping("/init/geo")
    public Result initGeoData() {
        return shopService.initGeoData();
    }
}
