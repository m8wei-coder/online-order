# 隔离基线回归

使用 Java 21、Gradle wrapper 9.4.1、Node.js 和 Docker。已验证的回归集合为10个 Service 单测、1个 PostgreSQL authority 回归、4个 PostgreSQL 购物车并发回归，以及前端3个测试。

PostgreSQL 回归通过 `TEST_DATABASE_URL` 显式选择测试库，没有应用数据源回退；缺少变量时明确报错。测试使用 `experiment` 测试用户与空密码，并创建专用 schema、删除其中的测试表。仅用于合成数据，不能指向业务数据库。

## 启动专用 PostgreSQL

以下固定端口是示例，可换为本机空闲端口，并同步修改 URL：

```bash
docker run --rm --detach --name onlineorder-test-db \
  --cpus 1 --memory 512m \
  -e POSTGRES_USER=experiment \
  -e POSTGRES_DB=experiment \
  -e POSTGRES_HOST_AUTH_METHOD=trust \
  -p 127.0.0.1:55432:5432 postgres:15
docker exec onlineorder-test-db pg_isready -U experiment -d experiment
```

等待 `pg_isready` 成功。这里的 trust 认证只服务本机临时测试容器；不沿用任何应用连接配置或真实凭据。

## 后端

在 `backend/` 中运行。`JAVA_HOME` 应指向已安装的 JDK 21；使用独立 Gradle home，首次运行需下载公开依赖。

```bash
export JAVA_HOME=/path/to/jdk21
export GRADLE_USER_HOME=/tmp/onlineorder-test-gradle
export TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/experiment
cat >/tmp/onlineorder-test-config.yaml <<'YAML'
spring:
  sql:
    init:
      mode: never
logging:
  level:
    root: WARN
YAML
export SPRING_CONFIG_LOCATION=file:/tmp/onlineorder-test-config.yaml

./gradlew --no-daemon --rerun-tasks test \
  --tests '*ServiceTests' \
  --tests com.laioffer.onlineorder.AuthorityQueryPostgresTests \
  --tests com.laioffer.onlineorder.CartServicePostgresConcurrencyTests
```

测试上下文显式装配真实 JDBC 仓储和事务中的 CartService，不扫描应用启动 runner；配置位置覆盖确保不加载应用默认连接配置。并发测试在专用 schema 内引入短暂数据库触发器延迟以覆盖竞争路径。它验证已有商品、首次加购、不同商品及清车/加购交错，不代表性能测试。

`OnlineOrderApplicationTests.contextLoads` 未包括在上述集合中；完整应用启动、真实部署配置和既有业务数据仍需另行隔离验证。现有库若已经有重复明细，本次行锁修复不会自动清理那些历史数据。

## 前端与清理

在 `frontend/doordash-app/` 中运行：

```bash
CI=true npm test -- --watchAll=false --runInBand
```

完成后仅清理本次测试容器：

```bash
docker stop onlineorder-test-db
```

由于容器设置了 `--rm`，停止后会删除该容器的合成测试数据。各并行运行应使用独立容器/数据库、端口、构建输出与缓存。
