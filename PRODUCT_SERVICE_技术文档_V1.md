好！我给你整理完整的技术文档内容。你按照下面的步骤操作：

---

## 📝 创建文档的步骤

1. **在项目中右键** → 选择"新建文件"
2. **命名为**：`PRODUCT_SERVICE_技术文档_V1.md`
3. **复制下面所有内容**并粘贴到文件中
4. **保存**

---

## 📄 完整的文档内容（从这里开始复制）

```markdown
# ProductService 技术文档 V1.0

> 基于 Redis 分布式锁的高并发商品缓存服务  
> 核心技术：Spring Boot 3.4.12 + MyBatis + Redis

---

## 📌 一、项目定位

### 1.1 核心职责

ProductService 是商品查询服务的核心组件，负责：
- ✅ 对外提供高效的商品查询接口
- ✅ 使用 Redis 缓存热点数据，减少数据库压力
- ✅ 防止缓存穿透（恶意查询不存在的商品）
- ✅ 防止缓存击穿（热点 key 过期时的并发保护）
- ✅ 使用分布式锁保证只有一个线程查询数据库

### 1.2 解决的问题

| 问题 | 场景 | 解决方案 |
|------|------|---------|
| **性能问题** | 高频查询同一商品 | Redis 缓存，99% 请求不走数据库 |
| **缓存穿透** | 恶意用户查询不存在的 ID | NULL_MARKER 空标记缓存 |
| **缓存击穿** | 热点商品缓存过期，万箭齐发 | Redis 分布式互斥锁 |
| **死锁风险** | 持有锁的线程挂了 | 锁自动过期机制 |
| **误删锁** | 超时线程误删别人的锁 | Lua 脚本原子性检查 |

---

## 🔐 二、分布式锁实现原理

### 2.1 什么是分布式锁？

**定义**：跨 JVM、跨服务器的互斥访问机制

**本质**：找一个大家都信任的第三方（Redis）来记录锁的状态

**对比普通锁**：

| 特性 | synchronized（普通锁） | Redis 分布式锁 |
|------|---------------------|---------------|
| 存储位置 | JVM 堆内存（Java 对象） | Redis 服务器（字符串 key） |
| 作用范围 | 当前 JVM 内 | 跨 JVM、跨服务器 |
| 代码标志 | `synchronized(this)` | `redisTemplate.setIfAbsent()` |

### 2.2 核心代码（灵魂三行）

```
java
// 抢锁 - 第 214-218 行
private boolean tryLock(String lockKey, String lockValue) {
Boolean success = redisTemplate.opsForValue().setIfAbsent(
lockKey,                    // ① 锁的名字
lockValue,                  // ② 唯一标识
Duration.ofSeconds(10)      // ③ 过期时间
);
return Boolean.TRUE.equals(success);
}
```
**对应的 Redis 命令**：

```
bash
SETNX lock:product:1 uuid-xxx EX 10
```
**执行逻辑**：

```

if (lock:product:1 不存在) {
设置 lock:product:1 = uuid-xxx
设置 10 秒后自动删除
return 1  // 成功抢到锁
} else {
return 0  // 失败（已被别人占了）
}
```
### 2.3 为什么用 SETNX？

**关键特性**：原子性 + 唯一性

| 特性 | 含义 | 为什么要这个特性 |
|------|------|------------------|
| **原子性** | 检查和设置一气呵成 | 避免被别的线程插队 |
| **唯一性** | 只有 key 不存在时才成功 | 保证只有一个线程抢到 |

**如果不用 SETNX，用普通 SET**：

```
bash
# 普通 SET：不管 key 存不存在，直接覆盖
线程 A: SET lock:product:1 uuid-A → 成功
线程 B: SET lock:product:1 uuid-B → 也成功（覆盖了 A）❌
线程 C: SET lock:product:1 uuid-C → 也成功（覆盖了 B）❌

结果：3 个线程都以为自己拿到了锁 → 全部去查数据库 → 缓存击穿！
```
### 2.4 安全释放锁（Lua 脚本）

```
lua
-- 第 58-63 行：UNLOCK_SCRIPT
if redis.call('get', KEYS[1]) == ARGV[1] then
return redis.call('del', KEYS[1])
else
return 0
end
```
**Java 调用**：

```
java
// 第 226-232 行
private void unlock(String lockKey, String lockValue) {
redisTemplate.execute(
UNLOCK_SCRIPT,
Collections.singletonList(lockKey),
lockValue
);
}
```
**为什么用 Lua 脚本？**

防止超时的线程误删别人的锁：

```

时刻 0: 线程 A 抢到锁 (uuid-A)，10 秒过期
时刻 10: 锁自动过期
时刻 11: 线程 B 抢到锁 (uuid-B)
时刻 15: 线程 A 回来释放锁

如果没有 Lua 脚本：
线程 A: delete(lockKey) → 删掉了线程 B 的锁 ❌

有了 Lua 脚本：
Redis: get(lockKey) = "uuid-B" != "uuid-A"
→ 返回 0（不删除）✓
```
### 2.5 为什么必须设置锁的过期时间？

**生产环境的血泪教训**：

```

时刻 0: 线程 A 抢到锁（没有过期时间）
时刻 1: 线程 A 遇到 Bug/服务器宕机/网络中断
unlock() 没机会执行
时刻 2: 锁永远不释放
时刻 3: 后续所有线程都被堵在外面
结果：死锁 → 系统崩溃 💥
```
**有了过期时间**：

```

时刻 0: 线程 A 抢到锁（10 秒后自动过期）
时刻 1: 线程 A 挂了，unlock() 没执行
时刻 10: 锁自动过期（Redis 自动删除） ✓
时刻 11: 线程 B 可以重新抢锁 ✓
结果：系统不会死锁
```
---

## 🛡️ 三、缓存三大问题解决方案

### 3.1 正常查询（缓存命中）

**流程**（第 113-126 行）：

```
java
// 1. 先查 Redis
Object cached = redisTemplate.opsForValue().get(cacheKey);

// 2. 命中正常商品对象：直接返回
if (cached instanceof Product) {
return (Product) cached;  // 直接从缓存返回，不打数据库 ✓
}
```
**执行时间**：1-2ms  
**访问组件**：只访问 Redis

---

### 3.2 缓存穿透（查无此人）

#### ❌ 什么是缓存穿透？

```

恶意用户专门查询不存在的商品 ID：
用户 A: 查询 ID=999999（不存在）
→ Redis 没有
→ MySQL 也没有
→ 不写缓存

用户 B: 查询 ID=999999
→ Redis 还是没有
→ MySQL 又查一次

1 万个用户都查 ID=999999
→ MySQL 被打了 1 万次！
```
#### ✅ 解决方案：NULL_MARKER 空标记

**写入空标记**（第 161-167 行）：

```
java
// DB 也查不到：写空标记（防穿透）
if (product == null) {
redisTemplate.opsForValue().set(
cacheKey,
NULL_MARKER,        // "__NULL__"
Duration.ofSeconds(productNullTtlSeconds)  // 60 秒
);
return null;
}
```
**命中空标记**（第 117-120 行）：

```
java
// 命中空标记：说明之前已经确认"商品不存在"
if (NULL_MARKER.equals(cached)) {
System.out.println("【命中空缓存】商品不存在，productId = " + id);
return null;  // 直接返回，不打数据库 ✓
}
```
**效果**：
- 只有第 1 次会查数据库
- 后面 60 秒内的查询都被 Redis 挡住

---

### 3.3 缓存击穿（热点 key 过期）

#### ❌ 什么是缓存击穿？

```

热点商品（iPhone），每秒 10 万人访问

时刻 0: 缓存过期
时刻 1: 10 万用户同时查询
时刻 2: Redis 都没有缓存
时刻 3: 10 万请求全部冲向 MySQL
结果：MySQL 被打爆 💥
```
#### ✅ 解决方案：分布式互斥锁

**核心流程**（第 128-182 行）：

```
java
// 1. Redis 没有命中，开始处理"缓存重建"
String lockKey = productLockKey(id);
String lockValue = UUID.randomUUID().toString();

boolean locked = tryLock(lockKey, lockValue);

if (locked) {
try {
// 2. 双检：抢到锁后再查一次 Redis
Object cachedAgain = redisTemplate.opsForValue().get(cacheKey);

        if (cachedAgain instanceof Product) {
            return (Product) cachedAgain;  // 别人已经建好缓存了
        }
        
        // 3. 只有抢到锁并且双检后仍无缓存的人，才真正去查 DB
        Product product = productMapper.findById(id);
        
        // 4. 写缓存
        redisTemplate.opsForValue().set(cacheKey, product, ...);
        
        return product;
    } finally {
        // 5. 释放锁（Lua 保证只能删自己的锁）
        unlock(lockKey, lockValue);
    }
} else {
// 6. 没抢到锁：等待重试
Thread.sleep(retrySleepMillis);
return getProductByIdWithRetry(id, retryTimes + 1);
}
```
**效果**：
- 只有 1 个线程查数据库
- 其他线程都从缓存拿数据
- 避免了"万箭齐发"打爆数据库

---

## 🔄 四、完整调用链路

### 4.1 系统架构

```

用户浏览器 → Controller → ProductService → Redis → MySQL
```
### 4.2 完整执行流程（3 个用户同时查询 iPhone）

```

时刻 0: 缓存过期（Redis 里没有 iPhone 的数据）

时刻 1:
用户 A → 线程 A → getProductById(1)
用户 B → 线程 B → getProductById(1)
用户 C → 线程 C → getProductById(1)

时刻 2: 三个线程都查 Redis
线程 A: get("product:detail:1") → null
线程 B: get("product:detail:1") → null
线程 C: get("product:detail:1") → null

时刻 3: 都开始抢锁
线程 A: SETNX "lock:product:detail:1" "uuid-A" EX 10
→ 返回 1 ✓ 抢到了

线程 B: SETNX "lock:product:detail:1" "uuid-B" EX 10
→ 返回 0 ✗ 被占了

线程 C: SETNX "lock:product:detail:1" "uuid-C" EX 10
→ 返回 0 ✗ 被占了

时刻 4: 分工明确
线程 A: 进入 if (locked) 块
→ 双检 Redis（还是 null）
→ 查 MySQL: SELECT * FROM product WHERE id=1
→ 拿到 iPhone 数据
→ 写 Redis: set("product:detail:1", iPhone, 300s)
→ 释放锁：unlock("uuid-A")
→ 返回商品

线程 B: 进入 else 分支
→ sleep(50ms)
→ 递归重试：getProductByIdWithRetry(1, 1)
→ 再查 Redis：已经 iPhone 缓存了 ✓
→ 直接返回，不打数据库

线程 C: 进入 else 分支
→ sleep(50ms)
→ 递归重试：getProductByIdWithRetry(1, 1)
→ 再查 Redis：已经 iPhone 缓存了 ✓
→ 直接返回，不打数据库

最终结果：
✅ 只有线程 A 查了 MySQL（1 次）
✅ 线程 B 和 C 都从缓存拿数据
✅ 避免了缓存击穿
```
---

## ⚙️ 五、配置参数说明

| 参数名 | 配置项 | 默认值 | 在哪里用 | 作用 |
|--------|--------|--------|---------|------|
| **正常缓存 TTL** | `app.cache.product-ttl-seconds` | 300 秒 | 第 174 行 | 正常商品缓存多久过期 |
| **空缓存 TTL** | `app.cache.product-null-ttl-seconds` | 60 秒 | 第 165 行 | 空标记缓存多久（防穿透） |
| **锁 TTL** | `app.cache.product-lock-ttl-seconds` | 10 秒 | 第 217 行 | 锁多久后自动释放（防死锁） |
| **重试间隔** | `app.cache.product-retry-sleep-millis` | 50 毫秒 | 第 200 行 | 没抢到锁等多久再试 |
| **最大重试次数** | `app.cache.product-max-retry-times` | 5 次 | 第 192 行 | 最多重试几次后兜底查 DB |

**配置文件示例**（application.yml）：

```
yaml
app:
cache:
product-ttl-seconds: 600          # 正常缓存 600 秒
product-null-ttl-seconds: 120     # 空缓存 120 秒
product-lock-ttl-seconds: 15      # 锁 15 秒过期
product-retry-sleep-millis: 100   # 重试间隔 100ms
product-max-retry-times: 10       # 最多重试 10 次
```
---

## 🎯 六、代码结构

```

ProductService
│
├── 成员变量（配置参数）
│   ├── productTtlSeconds          (300 秒 - 正常缓存)
│   ├── productNullTtlSeconds      (60 秒 - 空缓存)
│   ├── productLockTtlSeconds      (10 秒 - 锁超时)
│   ├── retrySleepMillis           (50ms - 重试间隔)
│   └── maxRetryTimes              (5 次 - 最大重试)
│
├── 常量（工具）
│   ├── NULL_MARKER                (空标记-"__NULL__")
│   └── UNLOCK_SCRIPT              (Lua 脚本 - 安全删锁)
│
├── 对外方法
│   ├── getAllProducts()           (查询所有商品)
│   └── getProductById(id)         (查询单个商品 - 入口)
│
├── 核心方法
│   └── getProductByIdWithRetry()  (带重试的查询 - 主逻辑)
│
├── 辅助方法
│   ├── productDetailKey(id)       (生成缓存 key)
│   ├── productLockKey(id)         (生成锁 key)
│   ├── tryLock(key, value)        (抢锁)
│   └── unlock(key, value)         (释放锁)
│
└── 依赖注入
├── ProductMapper              (操作数据库)
└── RedisTemplate              (操作 Redis)
```
---

## 💡 七、面试常见问题

### Q1: 为什么用 Redis 而不用 synchronized？

**答**：synchronized 只能锁住当前 JVM 内的线程，无法阻止其他服务器的线程同时查数据库。而 Redis 是独立于所有 JVM 的第三方服务，所有服务器的线程都认 Redis 里的锁，所以能实现跨服务器的互斥访问。

---

### Q2: 锁的过期时间为什么要设置为 10 秒？

**答**：主要考虑两个因素：
1. 不能太短：要确保拿到锁的线程有足够时间查完数据库并重建缓存（一般查 DB+ 写缓存需要 2-3 秒）
2. 不能太长：如果持有锁的线程挂了，锁要在合理时间内自动释放，避免死锁

10 秒是一个经验值，既能完成正常的缓存重建，又能在异常时快速恢复。

---

### Q3: 为什么要用 Lua 脚本释放锁？

**答**：因为 get 和 delete 如果不是原子操作，可能会出现线程 A 误删线程 B 的锁的问题。比如：
- 线程 A 抢到锁，10 秒过期
- 10 秒后锁自动释放
- 线程 B 抢到一把新锁
- 线程 A 回来释放锁时，如果直接 delete，会删掉线程 B 的锁

Lua 脚本可以保证"检查锁的归属权"和"删除锁"这两个操作在 Redis 服务器端原子执行，不会被插队。

---

### Q4: 抢到锁后为什么还要再查一次 Redis（双检）？

**答**：因为在抢锁成功之前，可能已经有别的线程刚刚把缓存建好了，只是第一次查缓存时还没看到。双检可以减少不必要的 DB 查询。

---

### Q5: 如果一直抢不到锁怎么办？

**答**：采用等待重试机制：
1. sleep 50ms 后再试
2. 最多重试 5 次
3. 如果 5 次后还抢不到，直接查数据库兜底（避免无限等待）

---

## 🚀 八、性能指标

| 场景 | 响应时间 | 数据库访问 | 说明 |
|------|---------|-----------|------|
| **缓存命中** | 1-2ms | 0 次 | 99% 的请求走这里 |
| **缓存未命中（抢到锁）** | 50-100ms | 1 次 | 查 DB + 重建缓存 |
| **缓存未命中（没抢到锁）** | 10-20ms | 0 次 | 等待后命中缓存 |
| **缓存穿透** | 50-100ms | 1 次 | 只有第 1 次查 DB |

---

## 📚 九、技术关键词

- Spring Boot 3.4.12
- MyBatis
- Redis
- 分布式锁
- SETNX
- Lua 脚本
- 缓存穿透
- 缓存击穿
- 高并发
- 秒杀系统

---

## 📞 十、作者信息

**技术栈**：Java 后端开发  
**项目类型**：高并发秒杀系统  
**学习目标**：项目驱动面试准备

---

*最后更新时间：2026-03-19*
```


---

## ✅ 文档内容结束（复制到上面为止）

---

## 📌 下一步操作建议

1. **创建文件**：按我上面的步骤创建 `.md` 文件
2. **Git 提交**：

```bash
git add PRODUCT_SERVICE_技术文档_V1.md
git commit -m "docs: 添加 ProductService 技术文档 V1.0

包含内容:
- 分布式锁实现原理
- 缓存穿透/击穿解决方案
- 完整调用链路分析
- 配置参数说明
- 面试常见问题"
git push origin main
```


3. **GitHub 查看**：提交后可以在 GitHub 上看到这份详细的技术文档

---

现在你可以开始创建文件了，有任何问题随时问我！