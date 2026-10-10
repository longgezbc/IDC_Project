# ============================================================================
# 后端服务 Dockerfile（精简版：本地构建 JAR → 上传 → 容器运行）
# 适用：2核2G 轻量云服务器
# ============================================================================
# 前置步骤（本地执行）：
#   cd Hbsz_IDC_System_end
#   mvn clean package -DskipTests
#   将 target/Hbsz_IDC_System_end.jar 上传到服务器同目录下
# ============================================================================

FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="hbsz"
LABEL description="数据中心综合布线项目管理系统 - 后端服务"

WORKDIR /app

# 安装 curl 用于健康检查（alpine 版本更轻量）
RUN apk add --no-cache curl

# 复制本地构建好的 JAR 包（需在服务器上准备好）
COPY target/Hbsz_IDC_System_end.jar app.jar

# 创建文件上传与日志目录
RUN mkdir -p /app/uploads /app/logs

# ============================================================================
# P1-4.6 安全加固：以非 root 用户运行 JVM
# ============================================================================
# 创建专用用户 appuser（UID 1000），仅拥有 /app 目录的写权限
# 即使容器被攻破，攻击者也无法获得 root 权限访问宿主挂载卷
# 注：UID 1000 通常与宿主机普通用户重合，避免以 root 身份写入宿主文件
RUN addgroup -S -g 1000 appgroup && \
    adduser -S -u 1000 -G appgroup -h /app appuser && \
    chown -R appuser:appgroup /app

USER appuser

# 环境变量默认值（可通过 docker-compose / .env 覆盖）
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms96m -Xmx160m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:ThreadStackSize=512k -XX:+UseSerialGC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof" \
    SERVER_PORT=8080 \
    TZ=Asia/Shanghai

EXPOSE 8080

# 健康检查（2G 服务器 JVM 启动较慢，start-period 延长到 180s）
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=5 \
    CMD curl -f http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
