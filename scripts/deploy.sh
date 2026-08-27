#!/usr/bin/env bash
# =============================================================================
# 金语聊天系统 一键部署脚本
#
# 基于实际部署经验编写（2026-08-27），覆盖：
#   backend — 后端 Spring Boot jar（构建 → 上传校验 → 备份替换 → supervisor 重启 → 健康检查）
#   admin   — 管理员前端 admin-web 静态资源（构建 → 打包上传 → 备份替换 → 存活检查）
#   frontend— 用户前端 tousechat-frontend（独立仓库 Next.js standalone：
#             构建 → 补齐 static/public/env → 打包上传 → 备份替换 → systemd 重启 → 健康检查）
#
# 用法：
#   ./scripts/deploy.sh                    # 等价于 backend
#   ./scripts/deploy.sh backend            # 仅后端
#   ./scripts/deploy.sh admin              # 仅管理员前端
#   ./scripts/deploy.sh frontend           # 仅用户前端（独立仓库）
#   ./scripts/deploy.sh all                # 后端 + 管理员前端
#   ./scripts/deploy.sh all-with-frontend  # 后端 + 管理员前端 + 用户前端
#   ./scripts/deploy.sh preflight          # 只读预检（本地工具链 + 远端状态），不改任何东西
#   ./scripts/deploy.sh list-backups       # 查看远端现有备份
#   ./scripts/deploy.sh rollback backend   # 回滚后端到最近一次备份
#   ./scripts/deploy.sh rollback admin     # 回滚管理前端到最近一次备份
#
# 可选参数（可与动作任意组合）：
#   --skip-build    跳过本地构建，直接使用现有产物（target/*.jar 或 admin-web/dist）
#   --dry-run       只打印将要执行的步骤，不构建、不上传、不访问远端
#   --yes           跳过回滚前的确认
#
# 注意：
#   * 用户前端 tousechat-frontend 是独立仓库，不在本项目内，本脚本不覆盖。
#   * 后端单测编译存在存量错误（测试类未同步新构造器），构建固定
#     -Dmaven.test.skip=true，与历次生产部署一致。
# __USAGE_END__

set -euo pipefail

# ----------------------------- 项目配置 -------------------------------------
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_NAME="chat-0.0.1-SNAPSHOT.jar"
ADMIN_WEB_DIR="$PROJECT_ROOT/admin-web"

# 本地工具链（macOS 开发机；可用环境变量覆盖）
JAVA_HOME_17="${DEPLOY_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}"
MVN_BIN="${DEPLOY_MVN:-/opt/homebrew/bin/mvn}"

# 远端家用机（三机架构：经 frps 公网机 DDNS 进入，9001 是 frp 映射的 SSH）
SSH_HOST="${DEPLOY_SSH_HOST:-home.example.com}"
SSH_PORT="${DEPLOY_SSH_PORT:-9001}"
SSH_USER="${DEPLOY_SSH_USER:-instser}"
SSH_OPTS=(-p "$SSH_PORT" -o ConnectTimeout=10 -o BatchMode=yes)
# 注意：scp 的端口参数是大写 -P（-p 是保留时间戳）；ssh 才是小写 -p
SCP_OPTS=(-P "$SSH_PORT" -o ConnectTimeout=10 -o BatchMode=yes)

# 远端路径与服务
REMOTE_SUPCTL="${DEPLOY_SUPCTL:-/www/server/panel/pyenv/bin/supervisorctl}" # 宝塔 supervisor 不在 PATH
REMOTE_SUP_PROGRAM="${DEPLOY_SUP_PROGRAM:-chat:chat_00}"
REMOTE_BACKEND_DIR="${DEPLOY_BACKEND_DIR:-/opt/chat}"                        # jar 所在目录
REMOTE_BACKEND_LOGS="${DEPLOY_LOG_DIR:-/opt/chat/logs}"                      # jinyu-chat-all/error.log
REMOTE_ADMIN_DIR="${DEPLOY_ADMIN_DIR:-/opt/tousechat-admin}"                 # 管理前端静态目录
BACKUP_KEEP="${DEPLOY_BACKUP_KEEP:-3}"                                       # 远端保留备份份数（单个 jar 备份 112M）

# 用户前端（独立仓库，Next.js standalone）
FRONTEND_DIR="${DEPLOY_FRONTEND_DIR:-/Users/instser/Sync/Instser-code/git/tousechat/frontend}"
REMOTE_FRONTEND_DIR="${DEPLOY_FRONTEND_REMOTE_DIR:-/opt/tousechat-frontend}" # standalone 运行目录
REMOTE_FRONTEND_SVC="${DEPLOY_FRONTEND_SVC:-tousechat-frontend-pub.service}" # systemd 服务
FRONTEND_PORT="${DEPLOY_FRONTEND_PORT:-3001}"
FRONTEND_HEALTH_TIMEOUT="${DEPLOY_FRONTEND_HEALTH_TIMEOUT:-90}"

APP_PORT="${DEPLOY_APP_PORT:-8080}"
HEALTH_URL1="/api/models"                      # 未认证应答 401
HEALTH_URL2="/api/admin/dashboard/overview"
HEALTH_TIMEOUT_SEC="${DEPLOY_HEALTH_TIMEOUT:-150}"

PUBLIC_SITE="${DEPLOY_PUBLIC_SITE:-https://chat.instser.cn}"  # 验收参考入口

DRY_RUN=0
SKIP_BUILD=0
ASSUME_YES=0

# ----------------------------- 输出工具 -------------------------------------
if [[ -t 1 ]]; then
  C_G=$'\033[32m'; C_Y=$'\033[33m'; C_R=$'\033[31m'; C_B=$'\033[36m'; C_0=$'\033[0m'
else
  C_G=""; C_Y=""; C_R=""; C_B=""; C_0=""
fi
log_info() { echo "${C_B}[INFO]${C_0} $*"; }
log_ok()   { echo "${C_G}[ OK ]${C_0} $*"; }
log_warn() { echo "${C_Y}[WARN]${C_0} $*"; }
log_err()  { echo "${C_R}[FAIL]${C_0} $*" >&2; }
step()     { echo; echo "${C_B}======== $* ========${C_0}"; }

on_err() { log_err "第 $1 行执行失败。" >&2; }
trap 'on_err $LINENO' ERR

usage() { sed -n '2,/^# __USAGE_END__$/p' "$0" | sed '$d'; }
die()   { log_err "$*"; exit 1; }

confirm() {
  [[ "$ASSUME_YES" == "1" ]] && return 0
  read -r -p "$(echo "${C_Y}[确认]${C_0} $1 (y/N): ")" ans
  [[ "$ans" == "y" || "$ans" == "Y" ]]
}

# ----------------------------- 远程工具 -------------------------------------
SSHC() { ssh "${SSH_OPTS[@]}" "$SSH_USER@$SSH_HOST" "$@"; }

remote_run() {
  # 用法：remote_run [远端参数...] <<'TMPL'
  # stdin 为远端 bash 脚本模板；本地配置以 __TOKEN__ 形式注入，
  # 其余 $ 变量保持原样由远端求值；追加参数成为远端 $1 $2 ...
  local body
  body="$(render_stdin)"
  printf '%s\n' "$body" | SSHC 'bash -s' "$@"
}

render_stdin() {
  local body
  body="$(cat)"
  body="${body//__BACKEND_DIR__/$REMOTE_BACKEND_DIR}"
  body="${body//__ALLLOG__/$REMOTE_BACKEND_LOGS/jinyu-chat-all.log}"
  body="${body//__ERRLOG__/$REMOTE_BACKEND_LOGS/jinyu-chat-error.log}"
  body="${body//__JAR__/$JAR_NAME}"
  body="${body//__SUPCTL__/$REMOTE_SUPCTL}"
  body="${body//__PROG__/$REMOTE_SUP_PROGRAM}"
  body="${body//__PORT__/$APP_PORT}"
  body="${body//__KEEP__/$BACKUP_KEEP}"
  body="${body//__ADMIN_DIR__/$REMOTE_ADMIN_DIR}"
  body="${body//__REMOTE_FRONTEND_DIR__/$REMOTE_FRONTEND_DIR}"
  body="${body//__REMOTE_FRONTEND_SVC__/$REMOTE_FRONTEND_SVC}"
  body="${body//__FRONTEND_PORT__/$FRONTEND_PORT}"
  body="${body//__HEALTH_URL1__/$HEALTH_URL1}"
  body="${body//__HEALTH_URL2__/$HEALTH_URL2}"
  body="${body//__HEALTH_TIMEOUT_SEC__/$HEALTH_TIMEOUT_SEC}"
  printf '%s\n' "$body"
}

sudo_cmd() { # 在远端以 sudo 执行命令（逐参数转义）
  local c="sudo "
  local a
  for a in "$@"; do c+="$(printf '%q' "$a") "; done
  SSHC "$c"
}

remote_ts() { SSHC 'date +%Y%m%d%H%M'; }

md5_of() {
  if command -v md5 >/dev/null 2>&1; then md5 -q "$1"; else md5sum "$1" | awk '{print $1}'; fi
}

# ----------------------------- 预检 -----------------------------------------
check_local_toolchain() {
  local fails=0
  if [[ -d "$JAVA_HOME_17" ]]; then log_ok "JDK17: $JAVA_HOME_17"; else log_err "缺 JDK17：$JAVA_HOME_17"; fails=$((fails+1)); fi
  if [[ -x "$MVN_BIN" ]]; then log_ok "Maven: $MVN_BIN"; else log_err "缺 Maven：$MVN_BIN"; fails=$((fails+1)); fi
  if command -v scp >/dev/null 2>&1; then log_ok "scp 可用"; else log_err "缺 scp"; fails=$((fails+1)); fi
  if [[ -d "$ADMIN_WEB_DIR/node_modules" ]]; then
    log_ok "admin-web 依赖已安装"
  else
    log_warn "admin-web 缺 node_modules，构建前需先 npm install"
  fi
  if [[ -d "$FRONTEND_DIR" ]]; then
    log_ok "用户前端仓库存在: $FRONTEND_DIR"
    if frontend_npm node -v >/dev/null 2>&1; then
      log_ok "nvm Node 可用 ($(frontend_npm node -v))"
    else
      log_err "nvm Node v22.14.0 不可用（~/.nvm/versions/node/v22.14.0）"; fails=$((fails+1))
    fi
    if [[ -x "$FRONTEND_DIR/node_modules/.bin/next" ]]; then
      log_ok "用户前端 next 已安装"
    else
      log_warn "用户前端缺 node_modules（部署时会自动 pnpm install）"
    fi
  else
    log_warn "用户前端仓库不存在，跳过 frontend 相关检查：$FRONTEND_DIR"
  fi
  return "$fails"
}

check_remote_server() {
  local fails=0
  if ! SSHC true 2>/dev/null; then
    log_err "SSH 不通：$SSH_USER@$SSH_HOST:$SSH_PORT"
    return 1
  fi
  log_ok "SSH 连通"

  if sudo_cmd test -x "$REMOTE_SUPCTL"; then log_ok "supervisorctl: $REMOTE_SUPCTL"
  else log_err "远端无 supervisorctl：$REMOTE_SUPCTL"; fails=$((fails+1)); fi

  if sudo_cmd test -d "$REMOTE_BACKEND_DIR"; then log_ok "后端目录: $REMOTE_BACKEND_DIR"
  else log_err "远端缺目录 $REMOTE_BACKEND_DIR"; fails=$((fails+1)); fi

  if sudo_cmd test -d "$REMOTE_ADMIN_DIR"; then log_ok "管理前端目录: $REMOTE_ADMIN_DIR"
  else log_err "远端缺目录 $REMOTE_ADMIN_DIR"; fails=$((fails+1)); fi

  if sudo_cmd test -d "$REMOTE_FRONTEND_DIR"; then
    if sudo_cmd test -f "$REMOTE_FRONTEND_DIR/server.js"; then
      log_ok "用户前端目录: $REMOTE_FRONTEND_DIR （含 server.js）"
    else
      log_err "用户前端目录缺 server.js（standalone 不完整？）"; fails=$((fails+1))
    fi
    log_info "用户前端 systemd 状态: $(SSHC "systemctl is-active '$REMOTE_FRONTEND_SVC'" 2>/dev/null || echo unknown)"
  else
    log_err "远端缺用户前端目录 $REMOTE_FRONTEND_DIR"; fails=$((fails+1))
  fi

  if sudo_cmd test -f "$REMOTE_BACKEND_DIR/$JAR_NAME"; then
    log_ok "线上 jar 存在 ($(SSHC "ls -lh '$REMOTE_BACKEND_DIR/$JAR_NAME' | awk '{print \$5}'"))"
  else
    log_err "线上 jar 不存在"; fails=$((fails+1))
  fi

  log_info "supervisor 状态: $(sudo_cmd "$REMOTE_SUPCTL" status "$REMOTE_SUP_PROGRAM" 2>/dev/null || echo unknown)"
  log_info "磁盘余量: $(SSHC "df -h '$REMOTE_BACKEND_DIR' | tail -1 | awk '{print \$4\" free / \"\$2\" total\"'}")"
  return "$fails"
}

do_preflight() {
  step "预检 · 本地"
  local rc=0
  check_local_toolchain || rc=1
  step "预检 · 远端"
  check_remote_server || rc=1
  step "预检结果"
  if [[ $rc -eq 0 ]]; then log_ok "全部通过"; else log_err "存在失败项，请处理后重试"; exit 1; fi
}

# ----------------------------- 后端部署 --------------------------------------
build_backend() {
  step "后端 · 本地构建 (JDK17)"
  if [[ "$SKIP_BUILD" == "1" ]]; then
    log_warn "--skip-build：复用现有产物"
  else
    log_info "mvn clean package -Dmaven.test.skip=true ..."
    ( cd "$PROJECT_ROOT" \
      && JAVA_HOME="$JAVA_HOME_17" "$MVN_BIN" clean package "-Dmaven.test.skip=true" -q )
  fi
  [[ -f "$PROJECT_ROOT/target/$JAR_NAME" ]] || die "产物不存在：target/$JAR_NAME"
  log_ok "构建完成：target/$JAR_NAME ($(du -h "$PROJECT_ROOT/target/$JAR_NAME" | awk '{print $1}'))"
}

upload_backend() {
  step "后端 · 上传与完整性校验"
  local local_jar="$PROJECT_ROOT/target/$JAR_NAME"
  BACKEND_TMP_JAR="/tmp/chat-upload.$$.jar"

  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] 将执行: scp $local_jar → $SSH_USER@$SSH_HOST:$BACKEND_TMP_JAR ，随后比对两端 MD5"
    return
  fi

  log_info "scp 上传中（约 112M，视带宽 1~3 分钟）..."
  scp "${SCP_OPTS[@]}" "$local_jar" "$SSH_USER@$SSH_HOST:$BACKEND_TMP_JAR"

  local lsum rsum
  lsum="$(md5_of "$local_jar")"
  rsum="$(SSHC "md5sum '$BACKEND_TMP_JAR' | awk '{print \$1}'")"
  if [[ "$lsum" != "$rsum" ]]; then
    SSHC "rm -f '$BACKEND_TMP_JAR'"
    die "MD5 校验失败（本地 $lsum / 远端 $rsum），传输损坏已中止"
  fi
  log_ok "上传完成且 MD5 一致"
}

restart_backend() {
  step "后端 · 备份替换 + 严格重启"

  if [[ "$DRY_RUN" == "1" ]]; then
    cat <<EOF
[dry-run] 计划步骤（时间戳以远端为准）：
  1. 记录 __ERRLOG__ 字节偏移（用于统计本次启动窗口 ERROR 增量）
  2. 备份   : $REMOTE_BACKEND_DIR/$JAR_NAME → $JAR_NAME.bak.<ts> （仅保留最近 $BACKUP_KEEP 份）
  3. 替换   : install -o www -g www -m 644 新包 ← /tmp/chat-upload.*.jar
  4. 停止   : $REMOTE_SUPCTL stop $REMOTE_SUP_PROGRAM ，轮询 ≤15s 确认 $APP_PORT 释放
  5. 启动   : $REMOTE_SUPCTL start $REMOTE_SUP_PROGRAM
  6. 健康检查: 最长 ${HEALTH_TIMEOUT_SEC}s —— $APP_PORT 监听 + GET $HEALTH_URL1 应答 401
EOF
    return
  fi

  local ts;       ts="$(remote_ts)"
  local marker="/tmp/chat-deploy.$$.marker"

  remote_run "$ts" "$BACKEND_TMP_JAR" "$marker" <<'TMPL'
set -e
TS="$1"; TMP_JAR="$2"; MARKER="$3"
cd '__BACKEND_DIR__'

echo "--- 1) 记录错误日志偏移 ---"
OFF=$(sudo wc -c < '__ERRLOG__' 2>/dev/null || echo 0)
echo "$OFF" > "$MARKER"
echo "error.log 当前字节: $OFF"

echo "--- 2) 备份当前 jar ---"
sudo cp -f "__JAR__" "__JAR__.bak.$TS"
echo "已创建备份: __JAR__.bak.$TS"
ls -1t "__JAR__".bak.* 2>/dev/null | tail -n +$((__KEEP__+1)) | while read -r old; do
  sudo rm -f -- "$old"; echo "清理旧备份: $old"
done

echo "--- 3) 替换 jar ---"
sudo install -o www -g www -m 644 "$TMP_JAR" "__BACKEND_DIR__/__JAR__"
rm -f "$TMP_JAR"
ls -lh "__BACKEND_DIR__/__JAR__"

echo "--- 4) 停止服务 ---"
sudo '__SUPCTL__' stop '__PROG__'
released=0
for i in $(seq 1 15); do
  if ! sudo ss -tlnp | grep -q ":__PORT__ "; then echo "__PORT__ 已释放 (${i}s)"; released=1; break; fi
  sleep 1
done
[ "$released" = 1 ] || { echo "ERROR: __PORT__ 未按预期释放"; exit 1; }

echo "--- 5) 启动服务 ---"
sudo '__SUPCTL__' start '__PROG__'
TMPL

  log_ok "重启指令已完成，进入健康检查"
}

health_check_backend() {
  step "后端 · 健康检查（最长 ${HEALTH_TIMEOUT_SEC}s）"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] 略过健康检查"
    return
  fi

  local marker="/tmp/chat-deploy.$$.marker"

  if ! remote_run "$marker" <<'TMPL'
set -e
MARKER="$1"
PORT='__PORT__'
U1='__HEALTH_URL1__'; U2='__HEALTH_URL2__'
TIMEOUT='__HEALTH_TIMEOUT_SEC__'
ALLLOG='__ALLLOG__'; ERRLOG='__ERRLOG__'

deadline=$(( $(date +%s) + TIMEOUT ))
ok=0; http_code=''
while [ "$(date +%s)" -lt "$deadline" ]; do
  port_ok=0
  sudo ss -tlnp | grep -q ":$PORT " && port_ok=1
  if [ "$port_ok" = 1 ]; then
    http_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$PORT$U1" || echo 000)
  fi
  if [ "$port_ok" = 1 ] && [ "$http_code" = "401" ]; then
    echo "HEALTH_OK http=$http_code waited=$(( TIMEOUT - (deadline - $(date +%s)) ))s"
    ok=1; break
  fi
  sleep 5
done
if [ "$ok" != 1 ]; then
  echo "HEALTH_FAIL last_http=${http_code:-none}"
  echo "--- $ERRLOG 最后 30 行 ---"
  sudo tail -n 30 "$ERRLOG" 2>/dev/null || true
  exit 1
fi

echo "--- 启动标志 ---"
sudo grep 'Started ChatApplication' "$ALLLOG" | tail -1

echo "--- 本次启动窗口 ERROR 增量 ---"
OFF=$(sudo cat "$MARKER" 2>/dev/null || echo 0)
DELTA=$(sudo tail -c +"$((OFF+1))" "$ERRLOG" 2>/dev/null | grep -c 'ERROR' || true)
echo "ERROR_DELTA=$DELTA"
sudo rm -f "$MARKER"

echo "--- 接口复核（未认证应 401）---"
for u in "$U1" "$U2"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT$u" || echo 000)
  echo "$u -> $code"
done
TMPL
  then
    log_err "健康检查未通过。可回滚: $0 rollback backend --yes"
    exit 1
  fi

  log_ok "健康检查通过"
}

deploy_backend() {
  build_backend
  upload_backend
  restart_backend
  health_check_backend
  step "后端部署完成 ✔"
  echo "  公网验收参考: curl ${PUBLIC_SITE}${HEALTH_URL1} → 401 ；浏览器上传图片验证附件解析"
}

# ----------------------------- 管理前端部署 ----------------------------------
build_admin() {
  step "管理前端 · 本地构建 (npm run build)"
  if [[ "$SKIP_BUILD" == "1" ]]; then
    log_warn "--skip-build：复用现有 dist/"
  else
    ( cd "$ADMIN_WEB_DIR" && npm run build )
  fi
  [[ -f "$ADMIN_WEB_DIR/dist/index.html" ]] || die "构建产物缺失：admin-web/dist/index.html"
  log_ok "构建完成：$(du -sh "$ADMIN_WEB_DIR/dist" | awk '{print $1}')"
}

upload_admin() {
  step "管理前端 · 打包上传"
  ADMIN_TGZ="/tmp/adminweb.$$.tar.gz"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] 将执行: tar czf $ADMIN_TGZ -C $ADMIN_WEB_DIR/dist . 并 scp 到远端同名路径"
    return
  fi
  tar czf "$ADMIN_TGZ" -C "$ADMIN_WEB_DIR/dist" .
  log_info "tar 包大小: $(du -h "$ADMIN_TGZ" | awk '{print $1}')"
  scp "${SCP_OPTS[@]}" "$ADMIN_TGZ" "$SSH_USER@$SSH_HOST:$ADMIN_TGZ"
  log_ok "上传完成"
}

replace_admin() {
  step "管理前端 · 备份替换"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] 计划：cp -a $REMOTE_ADMIN_DIR → *.bak.<ts>（保留 $BACKUP_KEEP 份）；清空目录；解压新 dist；chown -R www:www"
    return
  fi
  local ts; ts="$(remote_ts)"

  remote_run "$ts" "$ADMIN_TGZ" <<'TMPL'
set -e
TS="$1"; TGZ="$2"
DIR='__ADMIN_DIR__'

echo "--- 备份当前站点 ---"
sudo cp -a "$DIR" "$DIR.bak.$TS"
echo "已创建备份: $DIR.bak.$TS"

cd "$(dirname "$DIR")"
BASE=$(basename "$DIR")
ls -1dt "$BASE".bak.* 2>/dev/null | tail -n +$((__KEEP__+1)) | while read -r old; do
  sudo rm -rf -- "$old"; echo "清理旧备份: $old"
done

echo "--- 替换内容 ---"
sudo find "$DIR" -mindepth 1 -delete
sudo tar xzf "$TGZ" -C "$DIR"
rm -f "$TGZ"
sudo chown -R www:www "$DIR"
ls -lh "$DIR/index.html"
TMPL

  log_ok "替换完成"
}

health_check_admin() {
  step "管理前端 · 存活检查"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] 略过存活检查"
    return
  fi
  local code
  code="$(curl -sk -o /dev/null -w '%{http_code}' --max-time 20 "$PUBLIC_SITE/admin/" || echo 000)"
  if [[ "$code" == "200" ]]; then
    log_ok "GET ${PUBLIC_SITE}/admin/ → 200"
  else
    log_warn "GET ${PUBLIC_SITE}/admin/ → $code（可能是 CDN 缓存/网络抖动，请浏览器人工复查）"
  fi
}

deploy_admin() {
  build_admin
  upload_admin
  replace_admin
  health_check_admin
  step "管理前端部署完成 ✔"
}

# ----------------------------- 用户前端（独立仓库 Next standalone）-------------
FRONTEND_TGZ=""   # 运行期填充：打包产物路径

frontend_npm() { # 统一 nvm node 工具链前缀
  local node_bin="$HOME/.nvm/versions/node/v22.14.0/bin"
  env PATH="$node_bin:$PATH" "$@"
}

build_frontend() {
  step "用户前端 · 本地构建 (Next.js standalone)"
  [[ -d "$FRONTEND_DIR" ]] || die "用户前端仓库不存在：$FRONTEND_DIR"
  if [[ "$SKIP_BUILD" == "1" ]]; then
    log_warn "--skip-build：复用现有 .next/standalone"
    [[ -f "$FRONTEND_DIR/.next/standalone/server.js" ]] || die "standalone 产物不存在，请先去掉 --skip-build 构建"
    return
  fi

  frontend_npm node -v || die "本地 Node 不可用（需要 nvm node v22.14.0）"
  if [[ ! -x "$FRONTEND_DIR/node_modules/.bin/next" ]]; then
    log_info "node_modules 缺失或不含 next，执行 pnpm install（corepack）..."
    ( cd "$FRONTEND_DIR" && frontend_npm corepack pnpm install --frozen-lockfile )
  fi

  log_info "next build 中（约 2~5 分钟）..."
  ( cd "$FRONTEND_DIR" && frontend_npm node node_modules/next/dist/bin/next build )

  [[ -f "$FRONTEND_DIR/.next/standalone/server.js" ]] \
    || die "构建未产出 .next/standalone/server.js（检查 next.config.mjs output: 'standalone'）"

  # 按 Next 文档补齐 standalone 运行所需：静态资源 / public / env
  log_info "补齐 standalone：.next/static、public、.env.production"
  rm -rf "$FRONTEND_DIR/.next/standalone/.next/static"
  cp -R "$FRONTEND_DIR/.next/static" "$FRONTEND_DIR/.next/standalone/.next/static"
  rm -rf "$FRONTEND_DIR/.next/standalone/public"
  cp -R "$FRONTEND_DIR/public" "$FRONTEND_DIR/.next/standalone/public"
  if [[ -f "$FRONTEND_DIR/.env.production" ]]; then
    cp "$FRONTEND_DIR/.env.production" "$FRONTEND_DIR/.next/standalone/.env.production"
  fi

  log_ok "构建完成：$(du -sh "$FRONTEND_DIR/.next/standalone" | awk '{print $1}')"
}

upload_frontend() {
  step "用户前端 · 打包上传"
  FRONTEND_TGZ="/tmp/tousechat-frontend.$$.tar.gz"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] 将执行: tar czf $FRONTEND_TGZ（--no-xattrs，去 macOS 扩展属性）-C $FRONTEND_DIR/.next/standalone . 并 scp 到远端同名路径"
    return
  fi
  # 历史教训：macOS 扩展属性会污染远端解包，统一 --no-xattrs
  tar --no-xattrs -czf "$FRONTEND_TGZ" -C "$FRONTEND_DIR/.next/standalone" .
  log_info "tar 包大小: $(du -h "$FRONTEND_TGZ" | awk '{print $1}')"
  scp "${SCP_OPTS[@]}" "$FRONTEND_TGZ" "$SSH_USER@$SSH_HOST:$FRONTEND_TGZ"
  local lsum rsum
  lsum="$(md5_of "$FRONTEND_TGZ")"
  rsum="$(SSHC "md5sum '$FRONTEND_TGZ' | awk '{print \$1}'")"
  [[ "$lsum" == "$rsum" ]] || { SSHC "rm -f '$FRONTEND_TGZ'"; die "前端 tar MD5 校验失败"; }
  log_ok "上传完成且 MD5 一致"
}

replace_frontend_and_restart() {
  step "用户前端 · 备份替换 + systemd 重启"
  if [[ "$DRY_RUN" == "1" ]]; then
    cat <<EOF
[dry-run] 计划步骤：
  1. 备份   : cp -a $REMOTE_FRONTEND_DIR → *.bak.<ts> （保留最近 $BACKUP_KEEP 份）
  2. 替换   : 清空 $REMOTE_FRONTEND_DIR 后解压新 standalone；chown root:root
             注意保留线上可能的本地文件策略 → 本脚本整目录替换（含 node_modules 瘦身追踪）
  3. 重启   : systemctl restart $REMOTE_FRONTEND_SVC （ExecStart= node server.js, 端口 $FRONTEND_PORT）
  4. 健康检查: 最长 ${FRONTEND_HEALTH_TIMEOUT}s —— GET http://127.0.0.1:$FRONTEND_PORT/ 应答 200
EOF
    return
  fi
  local ts; ts="$(remote_ts)"

  remote_run "$ts" "$FRONTEND_TGZ" <<'TMPL'
set -e
TS="$1"; TGZ="$2"
DIR='__REMOTE_FRONTEND_DIR__'
SVC='__REMOTE_FRONTEND_SVC__'
KEEP='__KEEP__'

echo "--- 备份当前目录 ---"
sudo cp -a "$DIR" "$DIR.bak.$TS"
echo "已创建备份: $DIR.bak.$TS"

PARENT=$(dirname "$DIR"); BASE=$(basename "$DIR")
cd "$PARENT"
ls -1dt "$BASE".bak.* 2>/dev/null | tail -n +$((KEEP+1)) | while read -r old; do
  sudo rm -rf -- "$old"; echo "清理旧备份: $old"
done

echo "--- 停止服务（避免运行中替换报 ETXTBSY）---"
sudo systemctl stop "$SVC"

echo "--- 替换内容 ---"
sudo find "$DIR" -mindepth 1 -delete
sudo tar xzf "$TGZ" -C "$DIR"
rm -f "$TGZ"
# tar 会保留归档内 macOS 侧属主，统一规范为 root（systemd User=root）
sudo chown -R root:root "$DIR"
ls -lh "$DIR/server.js" | head -1

echo "--- 启动服务 ---"
sudo systemctl start "$SVC"
TMPL

  log_ok "远端重启指令已完成，进入健康检查"
}

health_check_frontend() {
  step "用户前端 · 健康检查（最长 ${FRONTEND_HEALTH_TIMEOUT}s）"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] 略过健康检查"
    return
  fi

  if ! SSHC "systemctl is-active '$REMOTE_FRONTEND_SVC'" >/dev/null; then
    log_err "服务未处于 active 状态。可回滚: $0 rollback frontend --yes"
    exit 1
  fi

  local deadline=$(( $(date +%s) + FRONTEND_HEALTH_TIMEOUT ))
  local code=''
  while (( $(date +%s) < deadline )); do
    code="$(SSHC "curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:$FRONTEND_PORT/ 2>/dev/null")" || code=000
    [[ "$code" == "200" ]] && break
    sleep 5
  done

  if [[ "$code" != "200" ]]; then
    log_err "健康检查失败（末次 HTTP=$code）。近 40 行服务日志："
    SSHC "sudo tail -n 40 /var/log/tousechat-frontend.log" | tail -20 >&2
    exit 1
  fi
  log_ok "本机 127.0.0.1:$FRONTEND_PORT → 200"

  local pub
  pub="$(curl -sk -o /dev/null -w '%{http_code}' --max-time 25 "$PUBLIC_SITE/" || echo 000)"
  case "$pub" in
    200) log_ok "公网 ${PUBLIC_SITE}/ → 200" ;;
    *)   log_warn "公网 ${PUBLIC_SITE}/ → $pub（CDN 缓存可能滞后，请浏览器人工复查）" ;;
  esac
}

deploy_frontend() {
  build_frontend
  upload_frontend
  replace_frontend_and_restart
  health_check_frontend
  step "用户前端部署完成 ✔"
}

# ----------------------------- 回滚 ------------------------------------------
pick_backup() { # $1=backend|admin|frontend，输出最近一个备份名
  case "$1" in
    backend)
      SSHC "cd '$REMOTE_BACKEND_DIR' && ls -1t '${JAR_NAME}.bak.'* 2>/dev/null | head -1" ;;
    admin)
      SSHC "ls -1dt '${REMOTE_ADMIN_DIR}.bak.'* 2>/dev/null | head -1" ;;
    frontend)
      SSHC "ls -1dt '${REMOTE_FRONTEND_DIR}.bak.'* 2>/dev/null | head -1" ;;
  esac
}

do_list_backups() {
  step "远端备份清单"
  echo "— 后端 ($REMOTE_BACKEND_DIR) —"
  SSHC "ls -lht '$REMOTE_BACKEND_DIR/${JAR_NAME}.bak.'* 2>/dev/null || echo '（无）'"
  echo
  echo "— 管理前端 (${REMOTE_ADMIN_DIR}.bak.*) —"
  SSHC "ls -ldt '${REMOTE_ADMIN_DIR}.bak.'* 2>/dev/null || echo '（无）'"
  echo
  echo "— 用户前端 (${REMOTE_FRONTEND_DIR}.bak.*) —"
  SSHC "ls -ldt '${REMOTE_FRONTEND_DIR}.bak.'* 2>/dev/null || echo '（无）'"
}

do_rollback() {
  local what="${1:-}"
  shift || true
  local bk="${1:-}"
  [[ "$what" == "backend" || "$what" == "admin" || "$what" == "frontend" ]] \
    || die "用法: $0 rollback backend|admin|frontend [备份名]"
  [[ -n "$bk" ]] || bk="$(pick_backup "$what")"
  [[ -n "$bk" ]] || die "没有可用备份"
  confirm "将回滚 $what 到【$bk】，当前版本会另存为 *.preroll.*。继续？"

  if [[ "$what" == "backend" ]]; then
    remote_run "$bk" <<'TMPL'
set -e
BAK="$1"
cd '__BACKEND_DIR__'

echo "--- 停止服务 ---"
sudo '__SUPCTL__' stop '__PROG__' || true
sleep 2
released=0
for i in $(seq 1 10); do
  sudo ss -tlnp | grep -q ":__PORT__ " || { echo "__PORT__ 已释放"; released=1; break; }
  sleep 1
done
[ "$released" = 1 ] || { echo "WARN: 端口仍在占用，继续尝试恢复"; }

PRE="__JAR__.preroll.$(date +%Y%m%d%H%M)"
echo "--- 当前版本另存: $PRE ---"
sudo cp -f "__JAR__" "$PRE"
echo "--- 恢复 $BAK ---"
sudo cp -f "$BAK" "__JAR__"
sudo chown www:www "__JAR__"
sudo chmod 644 "__JAR__"
ls -lh "__JAR__"
echo "--- 启动服务 ---"
sudo '__SUPCTL__' start '__PROG__'
TMPL
    health_check_backend
  elif [[ "$what" == "frontend" ]]; then
    remote_run "$bk" <<'TMPL'
set -e
BAK="$1"
DIR='__REMOTE_FRONTEND_DIR__'
SVC='__REMOTE_FRONTEND_SVC__'

echo "--- 停止服务 ---"
sudo systemctl stop "$SVC" || true

PRE="$DIR.preroll.$(date +%Y%m%d%H%M)"
echo "--- 当前版本另存: $PRE ---"
sudo cp -a "$DIR" "$PRE"
echo "--- 恢复 $BAK ---"
sudo rm -rf "$DIR"
sudo cp -a "$BAK" "$DIR"
echo "--- 启动服务 ---"
sudo systemctl start "$SVC"
TMPL
    health_check_frontend
  else
    remote_run "$bk" <<'TMPL'
set -e
BAK="$1"
DIR='__ADMIN_DIR__'
PRE="$DIR.preroll.$(date +%Y%m%d%H%M)"
sudo cp -a "$DIR" "$PRE"
sudo rm -rf "$DIR"
sudo cp -a "$BAK" "$DIR"
sudo chown -R www:www "$DIR"
echo "已恢复 $BAK（当前版本另存 $PRE）"
TMPL
    health_check_admin
  fi
  log_ok "回滚完成"
}

# ----------------------------- 参数解析 --------------------------------------
ACTION="backend"
SUBARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    backend|admin|frontend|all|all-with-frontend|preflight|list-backups|rollback) ACTION="$1" ;;
    --dry-run) DRY_RUN=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --yes|-y) ASSUME_YES=1 ;;
    -h|--help) usage; exit 0 ;;
    *) SUBARGS+=("$1") ;;
  esac
  shift
done

# 仅在直接执行时进入主流程；被 source（或 DEPLOY_LIB_MODE=1）时不执行，
# 以便本地对渲染逻辑做 mock 测试
if [[ "${BASH_SOURCE[0]}" != "$0" || -n "${DEPLOY_LIB_MODE:-}" ]]; then
  return 0 2>/dev/null || true
fi

echo "金语聊天系统一键部署 · 目标 $SSH_USER@$SSH_HOST:$SSH_PORT · 动作 $ACTION$( [[ $DRY_RUN == 1 ]] && echo ' · DRY-RUN' )"

case "$ACTION" in
  preflight)         do_preflight ;;
  list-backups)      do_list_backups ;;
  rollback)          do_rollback "${SUBARGS[0]:-}" "${SUBARGS[1]:-}" ;;
  backend)           deploy_backend ;;
  admin)             deploy_admin ;;
  frontend)          deploy_frontend ;;
  all)               deploy_backend
                     deploy_admin ;;
  all-with-frontend) deploy_backend
                     deploy_admin
                     deploy_frontend ;;
esac
