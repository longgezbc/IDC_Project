#!/usr/bin/env bash
# ============================================================================
# HBSZ 服务器首次初始化脚本 - 从 GitHub 拉取代码并完成首次部署
# 功能：依赖检查 / GitHub 认证(SSH key 或 token) / clone 仓库 / 配置 env / 首次部署
# 运行环境：Linux 服务器（Ubuntu/CentOS/Deian），需可访问 GitHub
# 用法见 --help
# 注意：如在 Windows 编辑后上传，先 dos2unix scripts/server-init.sh
# ============================================================================
set -Eeuo pipefail

# ======================== 默认配置 ========================
REPO_HTTPS="https://github.com/longgezbc/IDC_Project.git"
REPO_SSH="git@github.com:longgezbc/IDC_Project.git"
REPO_USER="longgezbc"
REPO_NAME="IDC_Project"
TARGET_DIR="$HOME/hbsz"
BRANCH="main"
AUTH_MODE="ssh"          # ssh 或 token
GITHUB_TOKEN=""
AUTO_PASSWORDS=false     # 自动生成强密码填入 .env.docker（测试用，生产建议手动）
SKIP_DEPLOY=false        # 仅 clone+配置，不执行部署（首次配置 env 后手动部署）
DEPLOY_EXTRA_ARGS=""     # 透传给 deploy.sh 的额外参数

# ======================== 颜色与日志 ========================
if [[ -t 1 ]]; then
  C_RESET="\033[0m"; C_RED="\033[31m"; C_GREEN="\033[32m"; C_YELLOW="\033[33m"; C_BLUE="\033[34m"; C_GRAY="\033[90m"
else
  C_RESET=""; C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_GRAY=""
fi
log()    { echo -e "[$(date '+%H:%M:%S')] [$1] $2" >&2; }
info()   { log "INFO"    "${C_BLUE}$*${C_RESET}"; }
success(){ log "SUCCESS" "${C_GREEN}$*${C_RESET}"; }
warn()   { log "WARN"    "${C_YELLOW}$*${C_RESET}"; }
error()  { log "ERROR"   "${C_RED}$*${C_RESET}"; }
fatal()  { error "$*"; exit 1; }

# ======================== 用法 ========================
usage() {
  cat <<EOF
HBSZ 服务器首次初始化脚本（从 GitHub 拉取并部署）

用法: $0 [选项]

选项:
  --target-dir <path>   部署目录（默认 $HOME/hbsz）
  --branch <name>       拉取的分支（默认 main）
  --auth <ssh|token>    GitHub 认证方式（默认 ssh，推荐）
  --token <token>       GitHub Personal Access Token（--auth token 时必需）
  --auto-passwords      自动生成强密码填入 .env.docker（仅测试用，生产请手动）
  --skip-deploy         仅 clone + 配置 env，不执行首次部署
  --deploy-args <...>   透传给 deploy.sh 的额外参数（如 --backend-port 8081）
  --help, -h            显示本帮助

认证方式说明:
  ssh   (推荐) 使用 SSH key 认证。脚本检测 ~/.ssh/id_ed25519，不存在则自动生成，
        并打印公钥，需你将其添加到 GitHub 账户或仓库 Deploy Key。
  token 使用 Personal Access Token。通过 --token 传入或 GITHUB_TOKEN 环境变量。
        clone 后自动从 remote URL 移除 token，避免泄露到 .git/config。

示例:
  # SSH key 认证（推荐，首次会提示添加公钥到 GitHub）
  $0

  # 指定部署目录 + 分支
  $0 --target-dir /opt/hbsz --branch dev

  # Token 认证（CI/临时场景）
  $0 --auth token --token ghp_xxxxxxxxxxxx

  # 仅 clone + 配置，不自动部署（手动检查 .env.docker 后再部署）
  $0 --skip-deploy

  # 测试环境：自动生成密码 + 自定义端口
  $0 --auto-passwords --deploy-args "--backend-port 8081"
EOF
}

# ======================== 参数解析 ========================
parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --target-dir)    TARGET_DIR="$2"; shift 2 ;;
      --branch)        BRANCH="$2"; shift 2 ;;
      --auth)          AUTH_MODE="$2"; shift 2 ;;
      --token)         GITHUB_TOKEN="$2"; shift 2 ;;
      --auto-passwords) AUTO_PASSWORDS=true; shift ;;
      --skip-deploy)   SKIP_DEPLOY=true; shift ;;
      --deploy-args)   DEPLOY_EXTRA_ARGS="$2"; shift 2 ;;
      --help|-h)       usage; exit 0 ;;
      *)               fatal "未知参数: $1（用 --help 查看用法）" ;;
    esac
  done
  case "$AUTH_MODE" in ssh|token) ;; *) fatal "未知 --auth: $AUTH_MODE（支持: ssh|token）";; esac
  if [[ "$AUTH_MODE" == "token" && -z "$GITHUB_TOKEN" ]]; then
    GITHUB_TOKEN="${GITHUB_TOKEN:-${GITHUB_TOKEN_ENV:-}}"
    [[ -z "$GITHUB_TOKEN" ]] && fatal "--auth token 需要通过 --token 或环境变量 GITHUB_TOKEN 提供 token"
  fi
}

# ======================== 依赖检查 ========================
check_deps() {
  info "检查依赖..."
  local missing=()
  command -v docker >/dev/null 2>&1 || missing+=("docker")
  docker compose version >/dev/null 2>&1 || missing+=("docker-compose-v2")
  command -v git >/dev/null 2>&1 || missing+=("git")
  command -v curl >/dev/null 2>&1 || missing+=("curl")
  command -v openssl >/dev/null 2>&1 || missing+=("openssl")
  if [[ ${#missing[@]} -gt 0 ]]; then
    error "缺少依赖: ${missing[*]}"
    cat >&2 <<EOF
安装命令：
  docker:    curl -fsSL https://get.docker.com | bash
  git:       apt-get install -y git (或 yum install -y git)
  curl:      apt-get install -y curl (或 yum install -y curl)
  openssl:   apt-get install -y openssl (或 yum install -y openssl)
安装 docker 后需重新登录使 docker 组生效。
EOF
    fatal "依赖检查未通过"
  fi
  success "依赖就绪: docker $(docker --version | awk '{print $3}'), git $(git --version | awk '{print $3}')"
}

# ======================== GitHub 认证 ========================
setup_ssh_key() {
  info "配置 SSH key 认证..."
  local key_file="$HOME/.ssh/id_ed25519"
  if [[ -f "$key_file" ]]; then
    success "已有 SSH key: $key_file"
  else
    info "生成 SSH key（ed25519）..."
    mkdir -p "$HOME/.ssh" && chmod 700 "$HOME/.ssh"
    ssh-keygen -t ed25519 -C "hbsz-deploy@$(hostname)" -f "$key_file" -N "" >/dev/null
    success "SSH key 已生成: $key_file"
  fi
  local pubkey; pubkey=$(cat "$key_file.pub")
  echo
  info "请将以下公钥添加到 GitHub（仓库 Settings → Deploy keys，或账户 SSH keys）："
  echo -e "${C_GREEN}${pubkey}${C_RESET}"
  echo
  info "添加后按回车继续（或 Ctrl+C 退出后重新运行）..."
  read -r
  # 测试 SSH 连接
  info "测试 GitHub SSH 连接..."
  if ssh -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -T git@github.com 2>&1 | grep -q "successfully authenticated"; then
    success "GitHub SSH 认证成功"
  else
    warn "SSH 认证测试未返回成功信息（可能需确认公钥已添加），继续尝试 clone..."
  fi
}

setup_token() {
  info "使用 Token 认证..."
  [[ -n "$GITHUB_TOKEN" ]] || fatal "GITHUB_TOKEN 为空"
  success "Token 已就绪（长度 ${#GITHUB_TOKEN}）"
}

# ======================== clone 仓库 ========================
clone_repo() {
  info "拉取仓库到 $TARGET_DIR ..."
  if [[ -d "$TARGET_DIR/.git" ]]; then
    warn "目录已存在 git 仓库，执行 pull 更新"
    cd "$TARGET_DIR"
    git fetch origin "$BRANCH"
    git checkout "$BRANCH"
    git pull origin "$BRANCH"
    success "代码已更新"
  else
    mkdir -p "$(dirname "$TARGET_DIR")"
    if [[ "$AUTH_MODE" == "ssh" ]]; then
      git clone -b "$BRANCH" "$REPO_SSH" "$TARGET_DIR"
    else
      # token clone，clone 后立即移除 URL 中的 token，避免泄露到 .git/config
      git clone -b "$BRANCH" "https://x-access-token:${GITHUB_TOKEN}@github.com/${REPO_USER}/${REPO_NAME}.git" "$TARGET_DIR"
      cd "$TARGET_DIR"
      git remote set-url origin "$REPO_HTTPS"
      success "已从 remote URL 移除 token"
    fi
    success "仓库已 clone: $TARGET_DIR @ $BRANCH"
  fi
}

# ======================== 配置 .env.docker ========================
gen_strong_password() { openssl rand -base64 32 | tr -d '/+=' | cut -c1-32; }
gen_jwt_secret()      { openssl rand -base64 64; }

setup_env() {
  cd "$TARGET_DIR"
  if [[ -f .env.docker ]]; then
    success ".env.docker 已存在，跳过配置（如需重置请先删除它）"
    return 0
  fi
  if [[ ! -f .env.docker.template ]]; then
    fatal "未找到 .env.docker.template，请确认仓库完整性"
  fi
  cp .env.docker.template .env.docker
  if $AUTO_PASSWORDS; then
    info "自动生成强密码填入 .env.docker..."
    local db_pass fly_pass root_pass redis_pass grafana_pass jwt_secret
    db_pass=$(gen_strong_password); fly_pass=$(gen_strong_password)
    root_pass=$(gen_strong_password); redis_pass=$(gen_strong_password)
    grafana_pass=$(gen_strong_password); jwt_secret=$(gen_jwt_secret)
    sed -i "s|MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=${root_pass}|" .env.docker
    sed -i "s|DB_PASSWORD=.*|DB_PASSWORD=${db_pass}|" .env.docker
    sed -i "s|FLYWAY_PASSWORD=.*|FLYWAY_PASSWORD=${fly_pass}|" .env.docker
    sed -i "s|REDIS_PASSWORD=.*|REDIS_PASSWORD=${redis_pass}|" .env.docker
    sed -i "s|GRAFANA_ADMIN_PASSWORD=.*|GRAFANA_ADMIN_PASSWORD=${grafana_pass}|" .env.docker
    sed -i "s|JWT_SECRET=.*|JWT_SECRET=${jwt_secret}|" .env.docker
    success "强密码已自动填入（请妥善保存，可用 grep 查看）"
    warn "生产环境建议改为手动填写并记录密码"
  else
    warn "请手动编辑 .env.docker 填入强密码："
    echo "  生成强密码: openssl rand -base64 32"
    echo "  JWT 密钥:   openssl rand -base64 64"
    echo "  编辑命令:   vi $TARGET_DIR/.env.docker"
    echo
    if [[ -t 0 ]]; then
      info "编辑完成后按回车继续部署（或 Ctrl+C 退出，改好后跑 deploy.sh）..."
      read -r
    else
      warn "非交互环境，跳过等待。请确保 .env.docker 已配置后再部署。"
    fi
  fi
}

# ======================== 首次部署 ========================
run_deploy() {
  cd "$TARGET_DIR"
  if [[ ! -x scripts/deploy.sh ]]; then
    chmod +x scripts/deploy.sh || true
  fi
  info "调用 deploy.sh 执行首次部署..."
  # shellcheck disable=SC2086
  ./scripts/deploy.sh --branch "$BRANCH" $DEPLOY_EXTRA_ARGS
}

# ======================== 主流程 ========================
main() {
  parse_args "$@"
  info "===== HBSZ 服务器初始化 ====="
  info "认证: $AUTH_MODE | 分支: $BRANCH | 目录: $TARGET_DIR | 自动密码: $AUTO_PASSWORDS"
  check_deps
  if [[ "$AUTH_MODE" == "ssh" ]]; then setup_ssh_key; else setup_token; fi
  clone_repo
  setup_env
  if $SKIP_DEPLOY; then
    warn "--skip-deploy：仅完成 clone + 配置，未执行部署"
    info "检查 .env.docker 后，运行：cd $TARGET_DIR && ./scripts/deploy.sh --branch $BRANCH"
  else
    run_deploy
    success "===== 初始化 + 首次部署完成 ====="
    info "后续部署：cd $TARGET_DIR && ./scripts/deploy.sh --branch $BRANCH"
    info "查看状态：./scripts/deploy.sh --action status"
    info "失败回滚：./scripts/deploy.sh --action rollback"
  fi
}

main "$@"
