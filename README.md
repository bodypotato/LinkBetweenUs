# LinkBetweenUs
> demo视频链接:https://www.bilibili.com/video/BV1F9ux6vEiy/?spm_id_from=333.1387.upload.video_card.click&vd_source=6f676971d37d2da03768ffee98a9923b 

> 即时通讯桌面应用 —— 前后端分离，支持实时消息、好友管理、群聊、AI 智能助手。

---

## 技术栈

### 后端 (LinkBetweenUs)

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.1.0 |
| 语言 | Java | 25 |
| 构建 | Gradle (wrapper) | — |
| ORM | MyBatis-Plus | 3.5.16 |
| 数据库 | MySQL | 8 |
| 缓存 / 状态 | Redis (Lettuce) | — |
| 安全 | Spring Security + JJWT | 0.12.6 |
| 实时通信 | STOMP over WebSocket | — |
| JSON | Jackson (spring-boot-starter-json) | — |
| 校验 | spring-boot-starter-validation | — |

### 前端 (LBU Client)

| 层级 | 技术 | 版本 |
|------|------|------|
| 桌面框架 | Electron | 34 |
| UI 框架 | React | 19 |
| 构建工具 | Vite | 6 |
| 语言 | TypeScript | 5.7 |
| 样式 | Tailwind CSS | 4 |
| 状态管理 | Zustand | 5 |
| 路由 | react-router-dom | 7 (Hash 模式) |
| WebSocket | @stomp/stompjs | 7 |

### 基础设施

- **MySQL 8** — 持久化存储（用户、消息、好友关系、群组）
- **Redis** — 在线状态计数、Token 版本控制（顶号）、用户缓存、Dify 会话 ID 存储
- **Dify** — AI 智能体平台，提供 LLM 驱动的聊天机器人

---

## 总体架构

```
┌─────────────────────────────────────────────────────┐
│                  LBU Client (Electron)               │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────┐ │
│  │  Login    │ │  Chat    │ │  Sidebar             │ │
│  │  Window   │ │  Window  │ │  (Friends/Groups/AI) │ │
│  │ 430×530   │ │ 920×680  │ │                      │ │
│  └──────────┘ └──────────┘ └──────────────────────┘ │
│         │              │              │              │
│         └──────────────┼──────────────┘              │
│                        │                             │
│            REST (JWT)  │  STOMP/WebSocket            │
└────────────────────────┼─────────────────────────────┘
                         │
              ┌──────────┴──────────┐
              │   Spring Boot 4.1   │
              │   (port 8080)       │
              │                     │
              │  ┌───────────────┐  │
              │  │ Spring        │  │
              │  │ Security+JWT  │  │
              │  └───────┬───────┘  │
              │          │          │
              │  ┌───────┴───────┐  │
              │  │  Controllers  │  │
              │  │  auth/chat/   │  │
              │  │  friend/group │  │
              │  │  user/file/ai │  │
              │  └───────┬───────┘  │
              │          │          │
              │  ┌───────┴───────┐  │
              │  │   Services    │  │
              │  └───┬───────┬───┘  │
              │      │       │      │
              │  ┌───┴──┐ ┌──┴───┐  │
              │  │MySQL │ │Redis │  │
              │  └──────┘ └──────┘  │
              │        │            │
              └────────┼────────────┘
                       │
              ┌────────┴────────┐
              │   Dify AI 平台   │
              │ (LLM 智能助手)   │
              └─────────────────┘
```

### 通信模型

- **REST API** (`/api/**`)：JWT Bearer Token 认证，JSON 格式请求/响应
- **WebSocket** (`/ws`)：STOMP 协议，握手时通过 `?token=` 查询参数认证
  - 客户端 → 服务端：`/app/...`
  - 服务端 → 用户：`/user/{account}/queue/private`（点对点）
  - 服务端 → 广播：`/topic/status`（在线状态）
- **认证流程**：注册 → 登录获取 JWT → REST 请求带 `Authorization: Bearer` 头，WebSocket 连接带 `?token=` 参数

---

## 功能介绍

### 1. 用户系统
- **注册 / 登录**：账号+密码注册，BCrypt 加密存储
- **JWT 认证**：24 小时有效期，HMAC-SHA 签名
- **顶号机制**：新登录会使旧 Token 失效（Redis Token 版本号递增）
- **密码重置**：支持修改密码
- **个人资料**：查看/修改昵称（Redis 缓存同步更新）

### 2. 好友管理
- **搜索用户**：按账号搜索用户
- **好友请求**：发送 → 待处理(PENDING) → 接受(ACCEPTED) / 拒绝(REJECTED)
- **好友列表**：显示所有已接受好友，含在线状态
- **删除好友**：解除好友关系(DISSOLVED)
- **实时通知**：好友请求通过 WebSocket 实时推送，事务提交后触发

### 3. 即时通讯
- **一对一私聊**：通过 STOMP WebSocket 实时收发消息
- **消息状态流转**：SENT(0) → DELIVERED(1) → READ(2)
- **离线消息**：离线用户的消息暂存，上线后通过 `/api/message/offline` 批量拉取
- **已读回执**：标记已读后推送 `ReadReceiptDto` 给发送方
- **聊天记录**：分页查询历史消息
- **会话列表**：显示所有私聊会话及最后一条消息

### 4. 群组聊天
- **创建群组**：自定义群名
- **加入群组**：通过群 ID 加入
- **群聊消息**：群内实时广播消息
- **群信息**：查看群成员列表和群详情

### 5. AI 智能助手 (Dify)
- **AI Bot 账号**：系统内置 `ai_bot` 账号，启动时自动创建
- **一键添加**：侧边栏 AI 助手卡片，点击即添加（无需好友验证）
- **智能对话**：接入 Dify 平台的 LLM 工作流，支持会话记忆（30 天 TTL）
- **异步处理**：AI 回复在独立线程池中异步生成，不阻塞用户发送
- **推理过程剥离**：自动去除 DeepSeek-R1 等模型的 `<think>` 标签
- **打字指示器**：Bot 思考时显示"AI 正在思考中…"占位消息
- **降级处理**：Dify 不可用时返回"AI服务暂时不可用"的友好提示

### 6. 文件分享
- **文件上传**：支持图片、音频、视频及任意类型文件（最大 50MB）
- **在线预览**：图片直接展示、音频/视频内联播放、其他文件提供下载链接
- **消息富媒体**：文件作为消息内容通过 STOMP 实时发送

### 7. 在线状态
- **实时在线感知**：CONNECT/DISCONNECT 时通过 `/topic/status` 广播
- **多端计数**：Redis 原子计数器 (`online:count:{account}`)，支持多设备同时在线
- **在线用户集合**：Redis Set 维护当前在线用户列表

### 8. 桌面客户端特性
- **Electron 双窗口模式**：登录小窗 (430×530) → 主窗口 (920×680, 最小 780×540)
- **亮/暗主题**：CSS 变量驱动，一键切换
- **无 Emoji 设计**：文件类型用中文标签（图/音/视/文），头像用首字符

---

## 项目结构

```
pending-project/
├── LinkBetweenUs/                     # Spring Boot 后端
│   ├── build.gradle                   # 依赖配置
│   ├── src/main/java/com/body/linkbetweenus/
│   │   ├── common/                    # Result 响应包装类
│   │   ├── config/                    # SecurityConfig, WebSocketConfig, DifyConfig...
│   │   ├── dto/                       # 通用 DTO
│   │   ├── entity/                    # MyBatis-Plus 实体
│   │   ├── mvc/
│   │   │   ├── ai/                    # Dify AI 集成 (client/dto/service/init)
│   │   │   ├── auth/                  # 认证 (注册/登录/JWT)
│   │   │   ├── chat/                  # 聊天 (STOMP 控制器 + REST API)
│   │   │   ├── file/                  # 文件上传
│   │   │   ├── friend/                # 好友管理 (搜索/请求/删除)
│   │   │   ├── group/                 # 群组 (创建/加入/消息)
│   │   │   ├── mapper/                # MyBatis-Plus Mapper 接口
│   │   │   ├── online/                # 在线状态 (Redis 计数器)
│   │   │   └── user/                  # 用户资料
│   │   └── util/                      # JwtUtil, SnowflakeIdGenerator...
│   └── src/main/resources/
│       ├── application.yaml           # 主配置
│       ├── application-local.yaml     # 本地密钥 (gitignored)
│       └── db/init.sql                # 数据库初始化脚本
│
├── LBU Client/                        # Electron + React 前端
│   ├── package.json
│   ├── electron/                      # Electron 主进程
│   │   ├── main.ts                    # 窗口管理 + IPC
│   │   └── preload.ts                 # contextBridge API
│   └── src/
│       ├── components/
│       │   ├── chat/                  # ChatWindow, ChatHeader, ChatInput,
│       │   │                          #   MessageList, MessageBubble
│       │   ├── sidebar/               # Sidebar, FriendList, GroupList,
│       │   │                          #   SearchBar, FriendRequests, AiBotCard, UserInfo
│       │   ├── group/                 # CreateGroupModal, JoinGroupModal, GroupInfoPanel
│       │   ├── settings/              # SettingsModal
│       │   └── common/                # 通用组件
│       ├── pages/                     # LoginPage, ResetPasswordPage, MainLayout
│       ├── stores/                    # Zustand stores (auth/chat/friend/group/online/dify)
│       ├── services/                  # api.ts (axios), stomp.ts (STOMP), auth.ts
│       ├── hooks/                     # React hooks
│       └── types/                     # TypeScript 类型定义
│
└── README.md
```

---

## 快速开始

### 前置条件

- JDK 25
- MySQL 8 (数据库名: `Link_Between_Us`, 字符集: utf8mb4)
- Redis (默认 `localhost:6379`)
- Node.js 20+ (前端)
- (可选) Dify 平台实例 (AI 助手功能)

### 后端启动

```bash
cd LinkBetweenUs

# 1. 初始化数据库
mysql -u root -p < src/main/resources/db/init.sql

# 2. 创建本地配置 (填入密钥)
cat > src/main/resources/application-local.yaml << EOF
DB_USERNAME: root
DB_PASSWORD: your_db_password
REDIS_PASSWORD: your_redis_password
JWT_SECRET: your_jwt_secret_key
DIFY_BASE_URL: http://localhost/v1
DIFY_API_KEY: app-your-dify-app-key
EOF

# 3. 启动
./gradlew bootRun
# 服务运行在 http://localhost:8080
```

### 前端启动

```bash
cd "LBU Client"
npm install
npm run electron:dev
# 登录窗口将自动打开
```

### 构建 Windows 安装包

```bash
cd "LBU Client"
npm run build:win
# 输出在 dist-release/ 目录
```

---

## 数据库表

| 表名 | 说明 |
|------|------|
| `LBU_User` | 用户表 (account PK, BCrypt 密码, 昵称, 创建时间) |
| `LBU_Friend` | 好友关系 (account_a < account_b 字典序, UNIQUE 约束) |
| `LBU_Friend_Request` | 好友请求 (状态: 0=PENDING, 1=ACCEPTED, 2=REJECTED, 3=DISSOLVED) |
| `LBU_Message` | 消息 (状态: 0=SENT, 1=DELIVERED, 2=READ) |
| `LBU_Group` | 群组信息 |
| `LBU_Group_Member` | 群成员关系 |
| `LBU_Group_Message` | 群聊消息 |

---

## 关键设计决策

- **令牌版本化（顶号）**：每次登录递增 Redis 中的 Token 版本号，旧 Token 自动失效，实现强制下线
- **好友对标准化**：好友关系始终以字典序存储 (`account_a < account_b`)，避免双向重复记录
- **事务后推送**：WebSocket 通知使用 `TransactionSynchronization.afterCommit()`，确保数据库提交成功后才推送
- **批量查询**：所有用户查询使用 `selectBatchIds()`，避免 N+1 问题
- **主键策略**：MyBatis-Plus 使用 `id-type: input`，由应用层 Snowflake 算法生成 ID
