# 拾光小铺

Spring Boot + Vue + MySQL 校园生活选物商城，包含商品浏览、账号、购物袋、订单交易、发货通知与销售管理。

## 作者与地址

- 姓名：马嘉玲
- 学号：202430451254
- GitHub：https://github.com/MJL629/shiguang-campus-shop
- 本机运行地址：http://127.0.0.1:18080
- 正式线上地址：待完成云端部署和校园网验收。

## 已实现功能

- 107件商品，分类、搜索、分页与详情，逐项保留资料来源。
- 注册、登录、注销，BCrypt密码哈希、Session及CSRF保护。
- 购物袋增删改查，服务端计算订单金额。
- 下单事务、库存条件扣减、幂等处理、演示付款、取消及确认收货。
- 管理员商品添加、编辑、下架，订单查询与发货。
- 邮件事务队列、延迟重试及发送记录，本机SMTP测试邮箱。
- 销售统计、近14天销售趋势和畅销商品。
- 可选32笔预置演示订单，显式标记，不冒充真实经营历史。

付款不产生实际扣款或物流。邮件状态CAPTURED表示到达本地测试邮箱，不代表外部邮箱已送达。正式验收需接入实际SMTP，并核实付款要求。

## 此电脑启动

工具和数据库已配置，运行 `python scripts/local.py start`，再打开本机地址。

管理员用户名为 `admin`，体验顾客为 `demo`。随机密码位于本机 `.local/runtime.json`，不得提交到仓库，也可以使用虚构资料注册新顾客。

修改代码后运行 `python scripts/local.py build`，然后运行 `python scripts/local.py restart`。构建输出与运行副本分开。`stop`只停止本项目后端，不删除数据库。

## 另一台Windows电脑配置

需要Python 3.9+及Node.js 24。依次运行：

1. `python scripts/setup-local.py`
2. `python scripts/local.py build`
3. `python scripts/local.py start`

工具从官方发布地址下载到 `.local/`。脚本不修改系统环境变量，密码不进入源代码。普通Java、Maven和MySQL环境也可按 `backend/pom.xml` 与 `application.yml` 运行。

## 测试与部署

`python scripts/local.py build` 运行前端构建与后端测试。`python scripts/smoke_test.py` 直接检查运行中的HTTP服务、MySQL及SMTP测试收件。

`MySqlIntegrationTest` 在配置MYSQL_TEST_URL、MYSQL_TEST_USER和MYSQL_TEST_PASSWORD时启用，其余自动测试默认使用H2兼容模式。GitHub Actions自动验证构建、数据库与部署镜像。

`Dockerfile`、`compose.yaml` 和 `deploy/Caddyfile` 提供应用、持久化MySQL及HTTPS入口。复制 `.env.example` 为 `.env`，填写域名、数据库及测试账号密码、SMTP信息后部署。正式云端与校园网长期访问仍待验收，本机地址不能代替线上地址。

## 目录说明

| 路径 | 用途 |
| --- | --- |
| frontend/src | Vue页面、接口调用、账号与购物状态 |
| frontend/public/images | 原创插画及本地商品图片 |
| backend/src/main/java | 商品、账号权限、购物袋、订单、邮件、管理模块 |
| backend/src/main/resources/db/migration | 数据库版本迁移与商品快照 |
| backend/src/test | 集成测试及事务测试 |
| scripts | 环境配置、构建、启动、数据导入、HTTP验收 |
| data/catalog.json | 商品来源记录 |
| docs | 需求、设计、验证与AI使用记录 |
| design | 最初的风格样稿 |

## 数据与AI使用

商品资料包含原创文创、DummyJSON原型目录及Open Library真实书目。本站价格、库存及交易均为课程演示，详见 `docs/data-sources.md`。

AI辅助代码已注释标记，使用记录在 `docs/ai-usage.md`。提交者仍需理解核心逻辑，并按课程规定补充本人的审阅和修改情况。
