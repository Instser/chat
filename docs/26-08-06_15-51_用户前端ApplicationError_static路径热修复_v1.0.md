# 用户前端 Application error 热修复 v1.0

- 时间：2026-08-06 15:51（北京时间）
- 现象：`Application error: a client-side exception has occurred while loading chat.instser.cn`

## 根因

`/opt/tousechat-frontend` 的 Next standalone 产物中，当前 Build `eioZoffBLNxuwBT1ZpBu7` 的静态资源被放在：

```
.next/static/static/chunks/app/page-062d85c84db38926.js
.next/static/static/eioZoffBLNxuwBT1ZpBu7/_buildManifest.js
```

而 HTML 引用路径为：

```
/_next/static/chunks/app/page-062d85c84db38926.js
```

导致 **page chunk 404**，浏览器 hydration 失败，Next 显示通用 Application error。  
目录内还混有多个历史 BuildId 的 chunk，说明曾多次不完整覆盖部署。

## 处理

1. `rsync -a .next/static/static/ .next/static/` 提升正确资源到期望路径
2. 用 Node 22 重启 `server.js`（原进程被中断后未自动拉起）
3. 新增 systemd：`tousechat-frontend.service`（enabled + running）
4. 验收：`/` 与关键 `/_next/static/**` 均为 200

## 用户侧

请 **强制刷新**（Cmd/Ctrl+Shift+R）或清站点缓存后再打开。  
若仍有旧 SW/缓存，可在浏览器 Application → Service Workers 注销后重试。

## 后续建议

- 下次部署 standalone：只同步**一次**干净 `.next/static` + `server.js` + `node_modules`（按 Next 文档），避免嵌套 `static/static`
- 部署后自动校验 `page-*.js` 与 HTML 引用 hash 一致
