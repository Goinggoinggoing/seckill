秒杀系统

一个高并发秒杀系统，展示了电商促销场景下极端流量的渐进式优化技术。

## 项目概述

本项目实现了一个完整的秒杀系统，包含四个渐进版本，每个版本解决不同的高并发挑战：

1. **V1: 基于MySQL的实现** - 使用数据库事务和锁来确保数据一致性
2. **V2: Redis优化** - 引入缓存和库存预扣减以提高性能
3. **V3: RocketMQ集成** - 实现消息队列以提高可靠性和可扩展性
4. **V4: 高级优化** - 增加限流、多级缓存和数据一致性对账机制

## 技术栈

- **框架**: Spring Boot 
- **ORM**: MyBatis
- **数据库**: MySQL
- **连接池**: Druid
- **缓存**: Redis
- **消息队列**: RocketMQ
- **其他库**: Guava (本地缓存)

## 架构演进


### V1 - 基于MySQL的基础实现

`git checkout v1`

简单架构，所有请求直接访问MySQL数据库：

```
用户请求 -> 应用服务器 -> MySQL(减库存+创建订单) 
```

### V2 - Redis优化版

`git checkout v2`

引入Redis缓存和异步处理：

```
用户请求 -> 应用服务器 -> Redis预减库存 -> 快速返回结果给用户
                       -> 异步队列 -> MySQL(最终减库存+创建订单)
                       -> 用户查询结果 -> Redis查询状态
```

### V3 - RocketMQ消息队列版

`git checkout v3`

```
用户请求 -> 应用服务器 -> Redis预减库存 
                       -> 发送半消息到RocketMQ 
                       -> 本地事务同步创建订单 
                       -> 确认消息发送
                       -> 返回秒杀成功
消息队列 -> 消费者处理 -> 异步扣减数据库库存
```

### V4 - 系统优化版

`git checkout v4`

在V3基础上引入多项优化措施：

- 分布式限流
- 延时消息处理超时订单
- 多级缓存
- 库存一致性对账机制

## 核心功能

- **商品管理**: 浏览和查看秒杀商品
- **秒杀执行**: 参与限时秒杀活动
- **订单管理**: 创建和查询秒杀订单
- **库存控制**: 防止超卖并确保数据一致性
- **分布式限流**: 使用Redis控制API请求速率
- **超时订单处理**: 自动取消未支付订单
- **多级缓存**: 通过本地和分布式缓存优化性能
- **数据对账**: 确保Redis和数据库库存的一致性
 
 基于此，系统整体架构如下所示：
 ![image-20250310164130689](https://proxy.bytewaver.top/proxy/raw.githubusercontent.com/Goinggoinggoing/image/main/blogimg/202503102027941.png)
## 项目结构

```
seckill-system/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/seckill/
│   │   │       ├── SeckillApplication.java      # 应用程序入口点
│   │   │       ├── annotation/                  # 自定义注解
│   │   │       ├── aspect/                      # AOP切面
│   │   │       ├── controller/                  # API控制器
│   │   │       ├── service/                     # 业务逻辑
│   │   │       ├── dao/                         # 数据访问
│   │   │       ├── entity/                      # 领域实体
│   │   │       ├── vo/                          # 值对象
│   │   │       ├── exception/                   # 异常处理
│   │   │       ├── mq/                          # 消息队列
│   │   │       └── util/                        # 工具类
│   │   └── resources/
│   │       ├── application.properties           # 应用配置
│   │       ├── mapper/                          # MyBatis映射
│   │       └── scripts/                         # Lua脚本
│   └── test/                                    # 测试代码
└── pom.xml                                      # 项目依赖
```

## 实现版本

### V1: 基于MySQL的实现

基础版本专注于使用MySQL事务和锁实现核心功能，使用不同的并发控制策略：

- **错误实现(V0)**: 不加锁导致超卖问题
- **悲观锁(V1)**: 使用`FOR UPDATE`锁定行，串行处理请求
- **乐观锁(V2)**: 使用版本号机制进行无锁并发控制
- **优化实现(V3)**: 通过单条原子SQL实现高效并发控制

性能指标：QPS约500-1000，响应时间200-500ms

### V2: Redis优化

使用Redis提升系统性能的关键技术：

- **Redis预减库存**: 减少数据库访问压力
- **库存快速失败**: 迅速拦截无效请求
- **异步下单处理**: 通过内存线程池提高响应速度
- **分布式锁**: 确保缓存初始化的线程安全
- **订单状态查询**: 提供结果查询机制

性能指标：QPS约10,000-20,000，平均响应时间小于100ms

### V3: RocketMQ集成

引入RocketMQ解决可靠性与扩展性问题：

- **事务消息**: 确保订单创建与消息发送的原子性
- **异步库存扣减**: 通过消息队列解耦库存操作
- 三种消费模式:
  - 至少一次处理 (At-Least-Once)
  - 至多一次处理 (At-Most-Once)
  - 恰好一次处理 (Exactly-Once)

性能指标：系统吞吐量显著提升，支持水平扩展

### V4: 高级优化

多方面优化系统性能与可靠性：

- **分布式限流**: 基于Redis的令牌桶算法控制流量
- **订单超时处理**: 使用RocketMQ延时消息实现可靠的订单超时取消
- **多级缓存**: 结合Guava本地缓存与Redis分布式缓存
- **库存对账机制**: 确保Redis与MySQL的库存数据一致性

## API文档

### 商品API

- `GET /goods/list` - 获取所有秒杀商品
- `GET /goods/detail/{goodsId}` - 获取商品详情

### 秒杀API

- `POST /seckill/{userId}/{goodsId}` - 执行秒杀
- `GET /seckill/result/{userId}/{goodsId}` - 获取秒杀结果

### 订单API

- `GET /order/detail/{orderNo}` - 获取订单详情
- `GET /order/list/{userId}` - 获取用户订单列表

## 运行应用

1. **前提条件**

   - JDK 8或更高版本
   - MySQL 
   - Redis （V2及后续需要）
   - RocketMQ (V3和V4版本需要)

2. **数据库设置**

   - 创建MySQL数据库 `CREATE DATABASE IF NOT EXISTS seckillv2`
   - 导入SQL脚本`mysql.sql`

3. **配置**

   - 更新`application.properties`中的数据库连接详情
   - 配置Redis连接
   - 配置RocketMQ 

4. **测试**

   ```bash
   # 获取商品列表
   curl http://localhost:8080/goods/list
   
   # 获取商品详情
   curl http://localhost:8080/goods/detail/1
   
   # 执行秒杀(POST请求)
   curl -X POST http://localhost:8080/seckill/1/1
   
   # 查询秒杀结果
   curl http://localhost:8080/seckill/result/1/1
   
   # 获取订单详情
   curl http://localhost:8080/order/detail/{orderNo}
   
   # 获取用户订单列表
   curl http://localhost:8080/order/list/1
   ```

## 性能对比

| 性能指标     | V1（MySQL实现）        | V2（Redis优化）        | V3/V4（进一步优化）  |
| ------------ | ---------------------- | ---------------------- | -------------------- |
| QPS          | 约500-1000             | 约10,000-20,000        | 更高，支持水平扩展   |
| 响应时间     | 平均200-500ms          | 平均100ms以下            | 更低，更稳定         |
| 数据一致性   | 非常高                | 低               | 中/高 |
| 数据库压力   | 所有请求直接访问数据库 | 只有成功请求访问数据库 | 异步处理，压力更小   |
| 系统资源利用 | CPU和数据库IO高负荷    | 内存使用率高           | 资源分配更均衡       |
| 可扩展性     | 受限于数据库性能       | 受限于Redis性能     | 完全分布式，易扩展   |

最终系统实现在100秒内处理了100万个秒杀请求（QPS 10,000），发放10万个库存且无超发。


## 文档

有关详细实现和技术细节，请参考系列文章：
1. [从零开始实现秒杀系统（一）：MySQL行锁与事务篇](https://blog.bytewaver.top/2025/02/01/project/seckill/%E4%BB%8E%E9%9B%B6%E5%BC%80%E5%A7%8B%E5%AE%9E%E7%8E%B0%E7%A7%92%E6%9D%80%E7%B3%BB%E7%BB%9F1/)
2. [从零开始实现秒杀系统（二）：Redis优化篇](https://blog.bytewaver.top/2025/02/07/project/seckill/%E4%BB%8E%E9%9B%B6%E5%BC%80%E5%A7%8B%E5%AE%9E%E7%8E%B0%E7%A7%92%E6%9D%80%E7%B3%BB%E7%BB%9F2/)
3. [从零开始实现秒杀系统（三）：RocketMQ消息队列篇](https://blog.bytewaver.top/2025/02/14/project/seckill/%E4%BB%8E%E9%9B%B6%E5%BC%80%E5%A7%8B%E5%AE%9E%E7%8E%B0%E7%A7%92%E6%9D%80%E7%B3%BB%E7%BB%9F3/)
4. [从零开始实现秒杀系统（四）：系统优化篇](https://blog.bytewaver.top/2025/02/21/project/seckill/%E4%BB%8E%E9%9B%B6%E5%BC%80%E5%A7%8B%E5%AE%9E%E7%8E%B0%E7%A7%92%E6%9D%80%E7%B3%BB%E7%BB%9F4/)


## 注意事项
1. Redis、MQ的性能应尽量高，建议通过 `redis-benchmark -t set,get -q` 测试达到10万左右，否则可能会影响对账一致性（个人测试发现Docker启动的Redis性能只有1万左右，导致对账脚本失效）。
2. 编写代码时需注意事务失效场景，例如方法必须为public、避免自调用。
3. 配置 `logging.level.root=error` 可以更好地从大量日志中过滤重点，帮助更快定位bug问题。
4. 如果不想配置Redis或者MQ可以`git checkout v1`到之前版本，实现最基础的秒杀系统


## 许可证

MIT License