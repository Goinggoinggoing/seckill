# 秒杀系统 V1 - 基础版本

这是一个基于Spring Boot和MySQL的秒杀系统基础版本（V1），实现了秒杀的核心功能，包括商品展示、秒杀下单和订单查询。

## 技术栈

- Spring Boot 2.7.5
- MyBatis
- MySQL
- Druid连接池
- Redis (预配置，V1版本暂不使用)
- RocketMQ (预配置，V1版本暂不使用)

## 项目结构

```
seckill-system/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── seckill/
│   │   │               ├── SeckillApplication.java    # 启动类
│   │   │               ├── controller/                # 控制器层
│   │   │               ├── service/                   # 服务层
│   │   │               ├── dao/                       # 数据访问层
│   │   │               ├── entity/                    # 实体类
│   │   │               ├── vo/                        # 值对象
│   │   │               ├── exception/                 # 异常处理
│   │   │               └── util/                      # 工具类
│   │   └── resources/
│   │       ├── application.properties                 # 应用配置
│   │       └── mapper/                                # MyBatis映射文件
│   └── test/                                          # 测试代码
└── pom.xml                                            # 项目依赖
```

## 数据库设计

- `user` 表：用户信息
- `goods` 表：商品基本信息
- `seckill_goods` 表：秒杀商品信息，包含库存、价格、秒杀时间等
- `seckill_order` 表：秒杀订单信息

## 功能说明

### V1版本功能

1. **商品模块**
    - 商品列表接口：`GET /goods/list`
    - 商品详情接口：`GET /goods/detail/{goodsId}`

2. **秒杀模块**
    - 执行秒杀接口：`POST /seckill/{userId}/{goodsId}`
    - 获取秒杀结果：`GET /seckill/result/{userId}/{goodsId}`

3. **订单模块**
    - 订单详情查询：`GET /order/detail/{orderNo}`
    - 用户订单列表：`GET /order/list/{userId}`

### 秒杀流程

1. 用户进入商品列表页，查看秒杀商品
2. 点击进入商品详情页，查看商品信息和秒杀状态
3. 在秒杀开始时间到达后，用户点击"立即秒杀"按钮
4. 服务端验证用户、商品和秒杀条件
5. 使用数据库行锁减少库存并创建订单
6. 返回秒杀结果

### 并发控制

V1版本使用MySQL的行锁机制来保证库存的原子性操作，具体实现了两种方式：

1. **悲观锁**：使用`for update`语句锁定行
2. **乐观锁**：使用版本号机制避免并发问题

## 如何运行

1. 创建MySQL数据库并导入SQL脚本
2. 修改`application.properties`中的数据库配置
3. 使用Maven命令构建项目：`mvn clean package`
4. 运行项目：`java -jar target/seckill-system-0.0.1-SNAPSHOT.jar`

## 测试接口

可以使用Postman或curl命令测试接口：

```bash
# 获取商品列表
curl http://localhost:8080/goods/list

# 获取商品详情
curl http://localhost:8080/goods/detail/1

# 执行秒杀（需要POST请求）
curl -X POST http://localhost:8080/seckill/1/1

# 查询秒杀结果
curl http://localhost:8080/seckill/result/1/1

# 查询订单详情
curl http://localhost:8080/order/detail/{orderNo}

# 查询用户订单列表
curl http://localhost:8080/order/list/1
```

## 性能瓶颈分析

V1版本主要有以下性能瓶颈：

1. 数据库压力大：所有请求都直接访问数据库
2. 行锁争用：高并发下会造成严重的锁等待
3. 无缓存层：每次都需要查询数据库
4. 同步处理：请求堆积会导致系统崩溃

这些问题将在后续版本中通过引入Redis缓存、消息队列等技术来解决。

## 下一步计划（V2版本）

1. 引入Redis缓存商品信息
2. 使用Redis实现库存预减
3. 实现分布式锁
4. 优化秒杀逻辑