# 短链服务

把长链接压缩成短码，访问短码 302 跳转到原地址，并异步统计点击量。

围绕「读多写少 + 高并发跳转」这一典型场景，实现了 Redis 缓存、布隆过滤器防穿透、点击计数异步落库等常见优化手段。

## 功能

- **生成短链**：粘贴长链接生成短码，可选有效期（不填为永久）
- **短链跳转**：访问 `/{code}` 返回 302 重定向到原始地址
- **点击统计**：每次跳转在 Redis 原子自增，定时任务批量落库，不阻塞跳转响应
- **缓存加速**：跳转链路优先读 Redis（TTL 24 小时），未命中回源 MySQL 并回写
- **防缓存穿透**：布隆过滤器拦截不存在的短码，恶意请求不落到数据库
- **过期控制**：支持设置有效期，过期短码不再跳转
- **管理页面**：单文件 Vue 3 页面，生成、列表、分页、复制短链

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.3.5 |
| 数据库 | MySQL 8、MyBatis-Plus 3.5.7 |
| 缓存 | Redis（Spring Data Redis / Lettuce） |
| 短码生成 | 雪花 ID + Base62 编码 |
| 前端 | Vue 3（CDN 引入，无需 Node 构建） |

## 项目结构

```
src/main/java/com/zyh/shortlink
├── config/          # 分页插件、布隆过滤器预热
├── controller/      # 接口层（生成、列表、详情、跳转）
├── dto/             # 请求参数
├── entity/          # 数据库实体
├── mapper/          # MyBatis-Plus Mapper
├── service/         # 业务逻辑（缓存、布隆过滤器、定时落库）
├── util/            # Base62 编码
└── vo/              # 响应对象
src/main/resources
├── static/index.html      # 单文件前端页面
├── application.yml        # 公共配置
├── application-local.yml  # 本机私密配置（已 gitignore）
└── schema.sql             # 建表脚本（启动时自动执行）
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8+
- Redis（本机 6379 端口）

### 2. 初始化数据库

先创建数据库，表结构由 `schema.sql` 在启动时自动创建：

```sql
CREATE DATABASE short_link DEFAULT CHARACTER SET utf8mb4;
```

### 3. 本地配置

在 `src/main/resources/` 下新建 `application-local.yml`（该文件已被 `.gitignore` 忽略）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/short_link?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: "你的数据库密码"
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
```

`application.yml` 中的 `spring.profiles.active: local` 会自动加载这个文件。

### 4. 启动

```bash
mvn spring-boot:run
```

或在 IDEA 中运行 `ShortLinkApplication`。启动日志中出现「布隆过滤器预热完成，共加载 N 个短码」表示就绪。

浏览器访问 <http://localhost:8081> 使用管理页面。

## 接口说明

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/links` | 生成短链，body：`{"longUrl": "...", "expireDays": 30}`，`expireDays` 可省略 |
| GET | `/api/links?page=1&size=10` | 分页查询短链列表 |
| GET | `/api/links/{code}` | 查询单个短链详情（点击数含尚未落库的增量） |
| GET | `/{code}` | 跳转接口，返回 302 |
| GET | `/ping` | 健康检查 |

生成短链返回示例：

```json
{
  "shortCode": "2WxjPrRECYr",
  "shortUrl": "http://localhost:8081/2WxjPrRECYr"
}
```

## 设计说明

- **短码生成**：用雪花算法生成 64 位 ID，再转 Base62（`0-9A-Za-z`）。相比数据库自增 ID，既不会暴露业务量，也不需要「先插入再回填短码」两次写库；雪花 ID 本身全局唯一，因此不存在随机生成短码后的查重问题。短码长度随 ID 增长从 1 位逐步到 11 位左右。
- **为什么用 302 而不是 301**：301 会被浏览器永久缓存，同一个用户之后不再经过服务端，点击统计会严重失真；302 每次都回到服务端，保证统计准确。
- **缓存策略（Cache-Aside）**：跳转先查 Redis，命中直接返回；未命中回源 MySQL 并写回缓存。缓存 TTL 默认 24 小时，若短链自身剩余有效期更短，则取更短的 TTL，避免缓存比短链活得久。
- **点击统计异步化**：跳转时只做 `INCR sl:click:{code}`，直接返回；定时任务每 30 秒用 `SCAN` 扫出有增量的 key，减去增量并 `UPDATE click_count = click_count + delta` 落库。用 `SCAN` 而不是 `KEYS`，避免大 key 空间下阻塞 Redis。
- **布隆过滤器防穿透**：用 Redis 位图实现（1000 万 bit、7 个哈希函数），不依赖 RedisBloom 模块，多实例天然共享同一份数据。哈希值由 MD5 摘要拆成两个 64 位整数，再用 Kirsch-Mitzenmacher 技巧生成 7 个偏移量，一次摘要算出全部哈希，避免多次计算 MD5。
- **预热与写入**：应用启动时用 `ApplicationRunner` 全量加载已有短码写入过滤器；新建短链后立即 `add`。这样冷启动期间也不会把正常请求挡掉。
- **静态资源与短码路径冲突**：跳转接口路径限制为正则 `/{code:[0-9a-zA-Z]+}`，只匹配字母数字。否则 `/index.html`、`/favicon.ico` 这类带点的请求会被当成短码去查库。

## 已知限制

这些是当前实现有意保留的取舍，也是后续可优化的点：

- 布隆过滤器状态存在 Redis 中，Redis 数据丢失后需要重启应用重新预热，否则所有短码都会被判定为不存在。
- 布隆过滤器不支持删除，短链过期或被删后仍留在位图里，只影响空间、不影响正确性。
- 点击落库采用「读取增量 → 递减 → 更新数据库」，高并发下极小概率出现重复计数；对点击量这种非核心指标可以接受。
- 没有用户体系，任何人都能看到全部短链列表。

## 后续规划

- 自定义短码（用户指定别名）
- 用户体系与短链归属隔离
- 访问明细统计（来源、地域、时间分布）
- 分库分表：按短码哈希分片
- Docker 部署
