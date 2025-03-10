/*
 Navicat Premium Data Transfer

 Source Server         : local
 Source Server Type    : MySQL
 Source Server Version : 50719
 Source Host           : localhost:3306
 Source Schema         : seckillv2

 Target Server Type    : MySQL
 Target Server Version : 50719
 File Encoding         : 65001

 Date: 10/03/2025 20:06:24
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for goods
-- ----------------------------
DROP TABLE IF EXISTS `goods`;
CREATE TABLE `goods`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `goods_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品名称',
  `goods_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品标题',
  `goods_img` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品图片',
  `goods_detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '商品详情',
  `goods_price` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '商品价格',
  `goods_stock` int(11) NULL DEFAULT 0 COMMENT '商品库存',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of goods
-- ----------------------------
INSERT INTO `goods` VALUES (1, 'iPhone 13', 'Apple iPhone 13 (A2634) 128GB 星光色', '/img/iphone13.jpg', 'iPhone 13详细介绍', 5999.00, 100, '2025-02-28 17:21:28', '2025-02-28 17:21:28');
INSERT INTO `goods` VALUES (2, '华为Mate 40 Pro', '华为 HUAWEI Mate 40 Pro 麒麟9000 5G', '/img/mate40pro.jpg', '华为Mate 40 Pro详细介绍', 6999.00, 100, '2025-02-28 17:21:28', '2025-02-28 17:21:28');

-- ----------------------------
-- Table structure for idempotence_record
-- ----------------------------
DROP TABLE IF EXISTS `idempotence_record`;
CREATE TABLE `idempotence_record`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '事务ID/消息ID',
  `processed` tinyint(1) NOT NULL COMMENT '是否处理成功',
  `create_time` datetime(0) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_transaction_id`(`transaction_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3508872 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '幂等性记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of idempotence_record
-- ----------------------------

-- ----------------------------
-- Table structure for seckill_goods
-- ----------------------------
DROP TABLE IF EXISTS `seckill_goods`;
CREATE TABLE `seckill_goods`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '秒杀商品ID',
  `goods_id` bigint(20) NOT NULL COMMENT '商品ID',
  `seckill_price` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '秒杀价格',
  `stock_count` int(11) NULL DEFAULT 0 COMMENT '秒杀库存',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '秒杀开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '秒杀结束时间',
  `version` int(11) NULL DEFAULT 0 COMMENT '版本号',
  `total_stock` int(11) NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_goods_id`(`goods_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '秒杀商品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of seckill_goods
-- ----------------------------
INSERT INTO `seckill_goods` VALUES (1, 1, 4999.00, 100000, '2025-03-04 14:36:23', '2029-04-01 15:36:23', 3603614, 100000);
INSERT INTO `seckill_goods` VALUES (2, 2, 5999.00, 10, '2025-03-09 16:39:08', '2025-03-10 17:39:08', 0, 10);

-- ----------------------------
-- Table structure for seckill_order
-- ----------------------------
DROP TABLE IF EXISTS `seckill_order`;
CREATE TABLE `seckill_order`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `goods_id` bigint(20) NOT NULL COMMENT '商品ID',
  `seckill_goods_id` bigint(20) NOT NULL COMMENT '秒杀商品ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单编号',
  `status` tinyint(4) NULL DEFAULT 0 COMMENT '订单状态：0新建未支付，1已支付，2已发货，3已收货，4已退款，5已完成',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `pay_time` datetime(0) NULL DEFAULT NULL COMMENT '支付时间',
  `pay_amount` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '支付金额',
  `transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_no`(`order_no`) USING BTREE,
  INDEX `idx_transaction_id`(`transaction_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3591217 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '秒杀订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of seckill_order
-- ----------------------------

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  `password` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码',
  `salt` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '盐值',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `head` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
  `register_date` datetime(0) NULL DEFAULT NULL COMMENT '注册时间',
  `last_login_date` datetime(0) NULL DEFAULT NULL COMMENT '上次登录时间',
  `login_count` int(11) NULL DEFAULT 0 COMMENT '登录次数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username_unique`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'user1', 'b7797cce01b4b131b433b6acf4add449', '1a2b3c4d', '13800000001', 'user1@example.com', NULL, '2025-02-28 17:21:28', NULL, 0);
INSERT INTO `user` VALUES (2, 'user2', 'b7797cce01b4b131b433b6acf4add449', '1a2b3c4d', '13800000002', 'user2@example.com', NULL, '2025-02-28 17:21:28', NULL, 0);

SET FOREIGN_KEY_CHECKS = 1;
