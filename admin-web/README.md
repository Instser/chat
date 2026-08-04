# 金语管理后台（admin-web）

Vue 3 + TypeScript + Vite + Element Plus + Pinia。

## 部署约定

- 访问路径：`https://chat.instser.cn/admin/`
- 前端 `base`：`/admin/`
- 后端 API：同源 `/api/**`（8080，context-path `/api`）
- 鉴权：复用用户登录 JWT Cookie（`auth-token`），要求 `role=admin`
- 不使用管理 API Key

## 本地开发

```bash
# 建议 Node 20+
cd admin-web
npm install
npm run dev
```

开发服务器默认 `http://127.0.0.1:5174/admin/`，并通过 Vite 代理把 `/api` 转到 `http://127.0.0.1:8080`。

## 构建

```bash
npm run build
```

产物在 `admin-web/dist`，可由 Nginx 挂载到 `/admin/`。

### Nginx 示例

```nginx
location /admin/ {
    alias /var/www/jinyu-admin/;
    try_files $uri $uri/ /admin/index.html;
}

location /api/ {
    proxy_pass http://127.0.0.1:8080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## 页面

- 登录 `/admin/login`
- 概览 `/admin/dashboard`
- 用户 `/admin/users`
- 套餐 `/admin/packages`
- 模型 `/admin/models`
- 订单 `/admin/orders`
- 审计 `/admin/audits`
- 运维 `/admin/ops`

运维与管理能力均走 JWT 管理员接口 `/api/admin/**`，不再在浏览器中使用 `X-API-Key`。
