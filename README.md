# 黑马点评 (HMDP) - 本地生活服务平台

> 基于Spring Boot + Redis的高性能本地生活服务平台，类似大众点评的功能实现

![GitHub 贡献贪吃蛇](https://raw.githubusercontent.com/yuaotian/yuaotian/refs/heads/output/github-contribution-grid-snake.svg)
## 📋 项目概述

黑马点评是一个完整的本地生活服务平台，提供商铺查询、优惠券秒杀、用户社交、博客分享等功能。项目重点展示了Redis在实际业务场景中的应用，包括缓存策略、分布式锁、消息队列等高级特性。

### 🛠️ 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| **后端框架** | | |
| Spring Boot | 2.7.4 | 主框架 |
| MyBatis Plus | 3.5.2 | ORM框架 |
| **数据存储** | | |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存数据库 |
| **工具库** | | |
| Hutool | 5.8.8 | Java工具库 |
| Redisson | 3.17.7 | Redis分布式锁 |
| Lombok | 1.18.30 | 代码简化 |

### 🎯 核心功能模块

#### 1. 用户管理系统
- **手机验证码登录**: 基于Redis的验证码存储和校验
- **用户信息管理**: 用户资料、头像上传
- **签到系统**: 基于Redis BitMap的连续签到统计

#### 2. 商铺管理系统
- **商铺信息查询**: 支持分类、地理位置查询
- **缓存策略**:
  - 缓存穿透解决方案（空值缓存）
  - 缓存雪崩解决方案（TTL随机化）
  - 缓存击穿解决方案（互斥锁、逻辑过期）

#### 3. 优惠券秒杀系统
- **秒杀功能**: 高并发秒杀优惠券
- **分布式锁**: 基于Redis的分布式锁防止超卖
- **异步处理**: Lua脚本 + 消息队列优化性能
- **一人一单**: 防止用户重复下单

#### 4. 博客社交系统
- **博客发布**: 用户可发布图文博客
- **点赞功能**: 基于Redis ZSet的点赞排行榜
- **关注系统**: 用户关注/取关，共同关注查询
- **Feed流**: 基于推模式的关注用户博客推送

#### 5. 地理位置服务
- **附近商铺**: 基于Redis GEO的地理位置查询
- **距离计算**: 自动计算用户与商铺的距离

## 📁 项目结构

```
hmdp/
├── src/main/java/com/hmdp/
│   ├── HmDianPingApplication.java          # 启动类
│   ├── config/                             # 配置类
│   │   ├── MvcConfig.java                  # MVC配置
│   │   ├── MybatisConfig.java              # MyBatis配置
│   │   ├── RedissonConfig.java             # Redisson配置
│   │   └── WebExceptionAdvice.java         # 全局异常处理
│   ├── controller/                         # 控制器层
│   │   ├── BlogController.java             # 博客控制器
│   │   ├── FollowController.java           # 关注控制器
│   │   ├── ShopController.java             # 商铺控制器
│   │   ├── UserController.java             # 用户控制器
│   │   ├── VoucherController.java          # 优惠券控制器
│   │   └── VoucherOrderController.java     # 订单控制器
│   ├── dto/                                # 数据传输对象
│   │   ├── LoginFormDTO.java               # 登录表单
│   │   ├── Result.java                     # 统一返回结果
│   │   ├── ScrollResult.java               # 滚动分页结果
│   │   └── UserDTO.java                    # 用户信息
│   ├── entity/                             # 实体类
│   │   ├── Blog.java                       # 博客实体
│   │   ├── Follow.java                     # 关注关系
│   │   ├── Shop.java                       # 商铺实体
│   │   ├── User.java                       # 用户实体
│   │   ├── Voucher.java                    # 优惠券实体
│   │   └── VoucherOrder.java               # 订单实体
│   ├── interceptor/                        # 拦截器
│   │   ├── LoginInterceptor.java           # 登录拦截器
│   │   └── RefreshTokenInterceptor.java    # Token刷新拦截器
│   ├── mapper/                             # 数据访问层
│   │   ├── BlogMapper.java                 # 博客Mapper
│   │   ├── ShopMapper.java                 # 商铺Mapper
│   │   ├── UserMapper.java                 # 用户Mapper
│   │   └── VoucherOrderMapper.java         # 订单Mapper
│   ├── service/                            # 服务接口层
│   │   ├── IBlogService.java               # 博客服务接口
│   │   ├── IShopService.java               # 商铺服务接口
│   │   ├── IUserService.java               # 用户服务接口
│   │   └── IVoucherOrderService.java       # 订单服务接口
│   ├── service/impl/                       # 服务实现层
│   │   ├── BlogServiceImpl.java            # 博客服务实现
│   │   ├── ShopServiceImpl.java            # 商铺服务实现
│   │   ├── UserServiceImpl.java            # 用户服务实现
│   │   └── VoucherOrderServiceImpl.java    # 订单服务实现
│   └── utils/                              # 工具类
│       ├── CacheClient.java                # 缓存工具类
│       ├── ILock.java                      # 分布式锁接口
│       ├── SimpleRedisLock.java            # Redis分布式锁实现
│       ├── RedisIdWorker.java              # Redis ID生成器
│       ├── RedisConstants.java             # Redis常量
│       └── UserHolder.java                 # 用户上下文
├── src/main/resources/
│   ├── application.yaml                    # 应用配置
│   ├── mapper/                             # MyBatis映射文件
│   ├── seckill.lua                         # 秒杀Lua脚本
│   └── unlock.lua                          # 解锁Lua脚本
├── hmdp.sql                                # 数据库脚本
└── pom.xml                                 # Maven配置
```

## 🚀 快速启动

### 环境要求
- JDK 11+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 启动步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd hmdp
```

2. **数据库初始化**
```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE hmdp;

# 导入数据
mysql -u root -p hmdp < hmdp.sql
```

3. **配置文件**
修改 `src/main/resources/application.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hmdp
    username: your_username
    password: your_password
  redis:
    host: localhost
    port: 6379
    password: your_redis_password  # 如果有密码
```

4. **启动Redis**
```bash
redis-server
```

5. **运行项目**
```bash
mvn spring-boot:run
```

6. **访问应用**
- 后端API: http://localhost:8081
- 接口文档: 查看各Controller类的接口定义

## 🔧 核心技术特性

### Redis应用场景

| 功能 | Redis数据结构 | 应用场景 |
|------|---------------|----------|
| 用户登录 | String | 存储验证码和用户Token |
| 商铺缓存 | String | 缓存商铺信息，解决缓存穿透/击穿/雪崩 |
| 点赞排行 | ZSet | 博客点赞用户排序 |
| 关注列表 | Set | 存储用户关注关系，计算共同关注 |
| 签到统计 | BitMap | 用户连续签到天数统计 |
| 附近商铺 | GEO | 地理位置查询 |
| 秒杀库存 | String + Lua | 原子性库存扣减 |
| 分布式锁 | String + Lua | 防止并发问题 |
| Feed流 | ZSet | 关注用户博客时间线 |

### 分布式锁实现
- 基于Redis的SET NX EX命令
- Lua脚本保证原子性
- 自动续期机制（Redisson）
- 防止锁误删除

### 缓存策略
- **缓存穿透**: 空值缓存 + 布隆过滤器
- **缓存击穿**: 互斥锁 + 逻辑过期
- **缓存雪崩**: TTL随机化 + 多级缓存

## 📚 学习资源

本项目来自黑马程序员Redis教程，仅供学习参考：
- 视频教程: [Redis入门到实战](https://www.bilibili.com/video/BV1cr4y1671t)
- 实战篇从P24开始

### 项目分支说明
- **master**: 完整版代码，包含所有功能实现
- **init**: 初始化代码，适合跟着教程学习开发

## 📞 联系方式

如有问题，请参考原教程或提交Issue。

## 1.下载
克隆完整项目
```git
git clone https://github.com/sgahch/hmdp.git
```
切换分支
```git
git checkout init
```

## 2.常见问题
部分同学直接使用了master分支项目来启动，控制台会一直报错:
```
NOGROUP No such key 'stream.orders' or consumer group 'g1' in XREADGROUP with GROUP option
```
这是因为我们完整版代码会尝试访问Redis，连接Redis的Stream。建议同学切换到init分支来开发，如果一定要运行master分支，请先在Redis运行一下命令：
```text
XGROUP CREATE stream.orders g1 $ MKSTREAM
```
