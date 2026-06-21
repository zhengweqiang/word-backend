# Word Atelier Admin

管理端子应用，位于统一 `frontend/` 项目内，使用 `SolidJS + Tailwind + shadcn-solid` 风格组件体系实现，面向管理员和老师。

## 功能范围

- 登录与登录态守卫
- 管理员总览 / 老师工作台
- 用户管理
- 班级管理
- 词书资源管理与班级分配
- 学习计划创建、发布、概览查看
- 词书批量导入中心

## 本地开发

```bash
cd frontend/admin
npm install
npm run dev
```

默认开发端口是 `4174`，并通过 Vite 代理把 `/api` 转发到 `http://127.0.0.1:8080`。

如果后端不在这个地址启动，可以临时覆盖：

```bash
ADMIN_FRONTEND_PROXY_TARGET=http://127.0.0.1:8080 npm run dev
```

## 生产构建

```bash
cd frontend/admin
npm run build
```

## Docker 运行

主 `docker-compose.yml` 只保留一个 `frontend` 服务。根应用和管理端子应用由同一个镜像构建并由同一个 nginx 容器服务。

```bash
docker-compose up -d --build frontend
```

启动后访问：

- `http://localhost:8083/`
- `http://localhost:8083/admin/`

## 说明

- 当前阶段由 `frontend/admin/` 承接管理端入口，统一 `frontend` 容器对外暴露端口。
- UI 组件采用 shadcn-solid 的 manual installation 方式组织，后续可以继续往 `src/components/ui/` 扩展。
