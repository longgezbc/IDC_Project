#!/usr/bin/env bash
# ============================================================================
# HBSZ IDC 系统 - 自动化部署脚本
# 功能：环境变量校验 / 依赖检查 / 代码拉取 / 构建打包 / 服务停启 / 健康检查 / 失败回滚
# 运行环境：Linux 服务器（Ubuntu/CentOS/Debian），需预装 docker + compose + git + curl
# 用法见 --help
# 注意：如在 Windows 编辑后上传，先执行 dos2unix scripts/deploy.sh 转换行尾
# ============================================================================
set -Eeuo pipefail

# ======================== 路径与默认配置 ========================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
ENV_FILE="$PROJECT_DIR/.env.docker"
LOG_DIR="$PROJECT_DIR/logs/deploy"
BACKUP_DIR="$PROJECT_DIR/.deploy-backup"

# 参数默认值
ACTION="deploy"
ENV_NAME="prod"
BRANCH=""
BACKEND_PORT=""
FRONTEND_PORT=""
HEALTH_TIMEOUT=300
DRY_RUN=false
TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/deploy-${TS}.log"

# 部署阶段标志（trap 据此判断是否需要回滚）
STAGE="init"
ROLLBACK_DONE=false

# compose 项目名（决定镜像名前缀，默认为目录名小写）
COMPOSE_PROJECT="hbsz"
BUILD_SERVICES=("backend" "frontend")

# ======================== 颜色 ========================
if [[ -t 1 ]]; then
  C_RESET="\033[0m"; C_RED="\033[31m"; C_GREEN="\033[32m"; C_YELLOW="\033[33m"
  C_BLUE="\033[34m"; C_GRAY="\033[90m"; C_BOLD="\033[1m"
else
  C_RESET=""; C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_GRAY=""; C_BOLD=""
fi

# ======================== 日志（终端 + 文件）========================
log() {
  local level="$1"; shift
  local msg="$*"
  local time; time="$(date '+%Y-%m-%d %H:%M:%S')"
  echo -e "[$time] [$level] $msg" | tee -a "$LOG_FILE" >&2
}
info()    { log "INFO"    "${C_BLUE}$*${C_RESET}"; }
success() { log "SUCCESS" "${C_GREEN}$*${C_RESET}"; }
warn()    { log "WARN"    "${C_YELLOW}$*${C_RESET}"; }
error()   { log "ERROR"   "${C_RED}$*${C_RESET}"; }
fatal()   { error "$*"; exit 1; }

# ======================== 错误陷阱（失败自动回滚 + 中断）========================
on_error() {
  local lineno="$1"
  error "命令在第 ${lineno} 行失败（当前阶段: ${STAGE}）"
  # 仅在服务已启动后的阶段（start/health）才自动回滚，避免误回滚
  if { [[ "$STAGE" == "start" ]] || [[ "$STAGE" == "health" ]]; } && [[ "$ROLLBACK_DONE" == false ]]; then
    warn "检测到服务启动后失败，触发自动回滚..."
    do_rollback
  fi
  error "===== 部署失败 ====="
  error "日志文件: $LOG_FILE"
  error "排查建议:"
  error "  1. 查看完整日志:         less $LOG_FILE"
  error "  2. 查看后端容器日志:     docker compose --env-file $ENV_FILE logs --tail=200 backend"
  error "  3. 查看容器状态:         docker compose --env-file $ENV_FILE ps"
  error "  4. 手动回滚:             $0 --action rollback"
  exit 1
}
trap 'on_error $LINENO' ERR

# ======================== 参数解析 ========================
usage() {
  cat <<EOF
HBSZ 自动化部署脚本

用法: $0 [选项]

选项:
  --env <name>          环境名称（仅用于日志标记，默认 prod）
  --env-file <path>     env 文件路径（默认 $PROJECT_DIR/.env.docker）
  --branch <name>       部署前切换并拉取的 git 分支（不指定则用当前分支）
  --backend-port <n>    覆盖后端端口（BACKEND_PORT）
  --frontend-port <n>   覆盖前端端口（FRONTEND_PORT）
  --action <type>       执行动作: deploy(默认) | rollback | health | status
  --timeout <s>         健康检查超时秒数（默认 300，backend 启动慢建议 ≥300）
  --dry-run             只打印将执行的命令，不实际执行（预演）
  --help, -h            显示本帮助

示例:
  # 生产部署（main 分支，默认端口）
  $0 --branch main

  # 指定后端端口 + 自定义 env 文件
  $0 --branch main --backend-port 8081 --env-file .env.staging

  # 仅健康检查
  $0 --action health

  # 失败后手动回滚
  $0 --action rollback

  # 预演（不实际执行）
  $0 --branch main --dry-run
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --env)           ENV_NAME="$2"; shift 2 ;;
      --env-file)      ENV_FILE="$2"; shift 2 ;;
      --branch)        BRANCH="$2"; shift 2 ;;
      --backend-port)  BACKEND_PORT="$2"; shift 2 ;;
      --frontend-port) FRONTEND_PORT="$2"; shift 2 ;;
      --action)        ACTION="$2"; shift 2 ;;
      --timeout)       HEALTH_TIMEOUT="$2"; shift 2 ;;
      --dry-run)       DRY_RUN=true; shift ;;
      --help|-h)       usage; exit 0 ;;
      *)               fatal "未知参数: $1（用 --help 查看用法）" ;;
    esac
  done
  # 参数化端口通过环境变量传给 compose（compose 读取顺序：shell env > --env-file）
  [[ -n "$BACKEND_PORT" ]]  && export BACKEND_PORT
  [[ -n "$FRONTEND_PORT" ]] && export FRONTEND_PORT
  # 校验 action
  case "$ACTION" in deploy|rollback|health|status) ;; *) fatal "未知 --action: $ACTION（支持: deploy|rollback|health|status）";; esac
}

# dry-run 包装：打印命令但不执行
run() {
  if $DRY_RUN; then echo "[DRY-RUN] $*" >&2; else "$@"; fi
}

# ======================== 依赖检查 ========================
check_deps() {
  STAGE="deps"
  info "检查部署依赖..."
  local missing=()
  command -v docker >/dev/null 2>&1 || missing+=("docker")
  if ! docker compose version >/dev/null 2>&1; then missing+=("docker-compose-v2"); fi
  command -v git >/dev/null 2>&1 || missing+=("git")
  command -v curl >/dev/null 2>&1 || missing+=("curl")
  if [[ ${#missing[@]} -gt 0 ]]; then
    error "缺少依赖: ${missing[*]}"
    cat >&2 <<EOF
请先安装缺失组件：
  docker + compose v2: curl -fsSL https://get.docker.com | bash
  git:                 apt-get install -y git   (或 yum install -y git)
  curl:                apt-get install -y curl  (或 yum install -y curl)
安装 docker 后需重新登录使 docker 组生效（或用 sudo）。
EOF
    fatal "依赖检查未通过"
  fi
  success "依赖就绪: $(docker --version | head -1), compose $(docker compose version --short), git $(git --version | awk '{print $3}'), curl $(curl --version | head -1 | awk '{print $2}')"
}

# ======================== 环境变量校验 ========================
check_env() {
  STAGE="env"
  info "校验环境变量文件: $ENV_FILE"
  [[ -f "$COMPOSE_FILE" ]] || fatal "docker-compose.yml 不存在: $COMPOSE_FILE"
  [[ -f "$ENV_FILE" ]] || fatal "env 文件不存在: $ENV_FILE（参考 .env.docker 创建，含 MYSQL_ROOT_PASSWORD/DB_PASSWORD/REDIS_PASSWORD/JWT_SECRET/FLYWAY_PASSWORD）"

  # compose 中用 ${VAR:?required} 强制的变量，必须非空
  local required=("MYSQL_ROOT_PASSWORD" "DB_PASSWORD" "REDIS_PASSWORD" "JWT_SECRET" "FLYWAY_PASSWORD")
  local missing=()
  while IFS='=' read -r key val || [[ -n "$key" ]]; do
    [[ "$key" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${key// /}" ]] && continue
    local k="${key// /}"
    # 值为空则检查是否是必需变量
    if [[ -z "$val" || "$val" =~ ^[[:space:]]*$ ]]; then
      for r in "${required[@]}"; do [[ "$k" == "$r" ]] && missing+=("$k"); done
    fi
  done < "$ENV_FILE"

  if [[ ${#missing[@]} -gt 0 ]]; then
    error "以下必需变量为空: ${missing[*]}"
    fatal "请在 $ENV_FILE 中设置（生产环境务必使用强密码，勿用 123456/默认值）"
  fi

  # 弱密码/占位值告警（不阻断，仅提示）
  if grep -qiE 'CHANGE_ME|123456|hbsz_app_2026|hbsz_mysql_2026|hbsz_redis_2026|hbsz_migrate_2026' "$ENV_FILE"; then
    warn "⚠ 检测到弱密码或占位值（CHANGE_ME/123456/默认密码），生产环境强烈建议替换为强随机值！"
    warn "  生成强密码: openssl rand -base64 32"
    warn "  JWT 密钥:   openssl rand -base64 64"
  fi
  success "环境变量校验通过"
}

# ======================== 代码拉取 ========================
git_pull() {
  STAGE="git"
  cd "$PROJECT_DIR"
  # 记录部署前的 commit（用于代码级回滚）
  git rev-parse HEAD > "$BACKUP_DIR/commit-before-deploy" 2>/dev/null || true
  if [[ -n "$BRANCH" ]]; then
    info "拉取分支: $BRANCH"
    run git fetch origin "$BRANCH"
    run git checkout "$BRANCH"
    run git pull origin "$BRANCH"
    success "代码已更新: $BRANCH @ $(git rev-parse --short HEAD)"
  else
    warn "未指定 --branch，使用当前分支 $(git rev-parse --abbrev-ref HEAD) @ $(git rev-parse --short HEAD)"
  fi
}

# ======================== 备份（镜像 + env + commit）========================
backup() {
  STAGE="backup"
  mkdir -p "$BACKUP_DIR"
  info "备份当前状态到 $BACKUP_DIR ..."
  # 备份 env 文件
  cp -f "$ENV_FILE" "$BACKUP_DIR/env.backup-${TS}"
  # 备份当前运行镜像（tag 成 :rollback），用于快速回滚
  local backed=0
  for svc in "${BUILD_SERVICES[@]}"; do
    local img="${COMPOSE_PROJECT}-${svc}"
    if docker image inspect "${img}:latest" >/dev/null 2>&1; then
      docker tag "${img}:latest" "${img}:rollback"
      success "镜像备份: ${img}:latest -> :rollback"
      backed=$((backed+1))
    fi
  done
  [[ $backed -eq 0 ]] && info "无既有镜像（首次部署），跳过镜像备份"
  # 清理超过 10 个的旧 env 备份
  ls -t "$BACKUP_DIR"/env.backup-* 2>/dev/null | tail -n +11 | xargs -r rm -f
  success "备份完成"
}

# ======================== 构建镜像 ========================
build() {
  STAGE="build"
  info "构建镜像（docker compose build）..."
  cd "$PROJECT_DIR"
  run docker compose --env-file "$ENV_FILE" build
  success "镜像构建完成"
}

# ======================== 停止旧服务 ========================
stop_services() {
  STAGE="stop"
  info "停止并清理旧服务..."
  cd "$PROJECT_DIR"
  run docker compose --env-file "$ENV_FILE" down --remove-orphans
  success "旧服务已停止"
}

# ======================== 启动服务 ========================
start_services() {
  STAGE="start"
  info "启动服务（docker compose up -d）..."
  cd "$PROJECT_DIR"
  run docker compose --env-file "$ENV_FILE" up -d
  success "服务已启动"
}

# ======================== 健康检查 ========================
get_backend_port() {
  if [[ -n "$BACKEND_PORT" ]]; then echo "$BACKEND_PORT"; return; fi
  local p; p=$(grep -E '^[[:space:]]*BACKEND_PORT=' "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2 | tr -d '[:space:]')
  [[ -n "$p" ]] && echo "$p" || echo "8080"
}

health_check() {
  STAGE="health"
  local port; port=$(get_backend_port)
  local url="http://127.0.0.1:${port}/api/actuator/health"
  info "健康检查: $url （超时 ${HEALTH_TIMEOUT}s）"
  local elapsed=0 interval=10
  while [[ $elapsed -lt $HEALTH_TIMEOUT ]]; do
    local resp
    if resp=$(curl -fsS --max-time 5 "$url" 2>/dev/null); then
      if echo "$resp" | grep -q '"status":"UP"'; then
        success "健康检查通过（${elapsed}s）: UP"
        return 0
      fi
      warn "服务已响应但状态非 UP: $(echo "$resp" | head -c 200)"
    fi
    printf "\r${C_GRAY}  等待服务就绪... %ds / %ds${C_RESET}" "$elapsed" "$HEALTH_TIMEOUT" >&2
    sleep "$interval"
    elapsed=$((elapsed + interval))
  done
  echo >&2
  error "健康检查超时（${HEALTH_TIMEOUT}s），服务未就绪"
  error "排查: docker compose --env-file $ENV_FILE logs --tail=200 backend mysql redis"
  return 1
}

# ======================== 回滚 ========================
do_rollback() {
  STAGE="rollback"
  ROLLBACK_DONE=true   # 防止 trap 重复触发
  warn "===== 开始回滚 ====="
  cd "$PROJECT_DIR"

  # 1. 停止当前服务（可能是失败的半启动状态）
  docker compose --env-file "$ENV_FILE" down --remove-orphans 2>/dev/null || true

  # 2. 镜像级快速回滚（优先）
  local rolled=false
  for svc in "${BUILD_SERVICES[@]}"; do
    local img="${COMPOSE_PROJECT}-${svc}"
    if docker image inspect "${img}:rollback" >/dev/null 2>&1; then
      docker tag "${img}:rollback" "${img}:latest"
      success "镜像回滚: ${img}:rollback -> :latest"
      rolled=true
    fi
  done

  if $rolled; then
    # 恢复最近的 env 备份
    local latest_env; latest_env=$(ls -t "$BACKUP_DIR"/env.backup-* 2>/dev/null | head -1)
    if [[ -n "$latest_env" ]]; then
      cp -f "$latest_env" "$ENV_FILE"
      success "env 文件已恢复: $latest_env"
    fi
    info "用回滚镜像启动服务（不重新构建）..."
    docker compose --env-file "$ENV_FILE" up -d --no-build
    success "===== 回滚完成（镜像级）====="
    warn "请验证: $0 --action health"
  else
    # 3. 无镜像备份 → 代码级回滚
    local commit_file="$BACKUP_DIR/commit-before-deploy"
    if [[ -f "$commit_file" ]]; then
      local old_commit; old_commit=$(cat "$commit_file")
      warn "无镜像备份，执行代码级回滚: git reset --hard ${old_commit:0:8}"
      git reset --hard "$old_commit"
      docker compose --env-file "$ENV_FILE" build
      docker compose --env-file "$ENV_FILE" up -d
      success "===== 回滚完成（代码级）====="
    else
      error "无可回滚资源（无镜像备份、无 commit 记录）"
      error "请手动排查: docker compose --env-file $ENV_FILE logs --tail=200"
      return 1
    fi
  fi
}

# ======================== 状态查看 ========================
do_status() {
  cd "$PROJECT_DIR"
  info "===== 当前部署状态 ====="
  docker compose --env-file "$ENV_FILE" ps || true
  echo
  local port; port=$(get_backend_port)
  info "后端健康检查: curl http://127.0.0.1:${port}/api/actuator/health"
  info "本次部署日志: $LOG_FILE"
}

# ======================== 部署主流程 ========================
do_deploy() {
  info "========== 开始部署 =========="
  info "项目: $PROJECT_DIR"
  info "动作: deploy | 环境: $ENV_NAME | 分支: ${BRANCH:-当前} | 后端端口: ${BACKEND_PORT:-默认} | 前端端口: ${FRONTEND_PORT:-默认} | 超时: ${HEALTH_TIMEOUT}s"
  $DRY_RUN && warn "⚠ DRY-RUN 模式：仅打印命令，不实际执行"
  check_deps
  check_env
  git_pull
  backup
  build
  stop_services
  start_services
  health_check
  success "========== 部署成功 =========="
  do_status
}

# ======================== 主入口 ========================
main() {
  mkdir -p "$LOG_DIR" "$BACKUP_DIR"
  parse_args "$@"
  case "$ACTION" in
    deploy)   do_deploy ;;
    rollback) do_rollback ;;
    health)   health_check; success "健康检查完成" ;;
    status)   do_status ;;
  esac
}

main "$@"
