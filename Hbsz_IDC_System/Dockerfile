# ============================================================================
# 前端应用 Dockerfile（精简版：本地构建 dist → 上传 → Nginx 运行）
# 适用：2核2G 轻量云服务器
# ============================================================================
# 前置步骤（本地执行）：
#   cd Hbsz_IDC_System
#   npm install
#   npm run build
#   将 dist/ 目录上传到服务器同目录下
# ============================================================================

# P1-4.6：使用 nginx-unprivileged 镜像（nginxinc 官方维护），
# 默认以 UID 101 用户运行，监听 8080 端口（非特权端口），
# pid 文件默认写入 /tmp/nginx.pid，避免 read_only 文件系统的写入冲突。
# 镜像地址：https://hub.docker.com/r/nginxinc/nginx-unprivileged
FROM nginxinc/nginx-unprivileged:1.27-alpine

LABEL maintainer="hbsz"
LABEL description="数据中心综合布线项目管理系统 - 前端应用"

# 复制 Nginx 配置（监听 8080）
# 注：nginx-unprivileged 默认配置已使用 user nginx + pid /tmp/nginx.pid，
#     此处仅需覆盖 conf.d/default.conf
COPY --chown=101:101 nginx.conf /etc/nginx/conf.d/default.conf

# 复制本地构建好的静态资源（需在服务器上准备好 dist/ 目录）
# --chown 确保静态文件归属 nginx 用户
COPY --chown=101:101 dist/ /usr/share/nginx/html

# 监听非特权端口（镜像默认 EXPOSE 8080，此处显式说明）
EXPOSE 8080

# 健康检查：访问容器内 8080 端口（使用 busybox wget，alpine 自带无需安装）
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/ > /dev/null || exit 1

# nginx-unprivileged 默认以 nginx 用户启动，无需 USER 指令
CMD ["nginx", "-g", "daemon off;"]
