# 📒 记账单 Bookkeeper

个人记账管理应用，支持 Android 手机端 + Windows 桌面端，数据可同步。

## 技术栈

| 端 | 技术 |
|---|---|
| Android | Kotlin + Jetpack Compose + Room + Material 3 |
| Desktop | Electron + React + TypeScript + SQLite (better-sqlite3) |
| 数据同步 | JSON 导入/导出 (v1) / 自建同步服务 (v2) |

## 项目结构

```
bookkeeper/
├── docs/                    # 项目文档（留痕）
│   ├── ARCHITECTURE.md      # 架构设计
│   ├── DATA_MODEL.md        # 数据模型
│   ├── CHANGELOG.md         # 变更日志
│   └── SYNC_PROTOCOL.md     # 同步协议
├── android/                 # Android 客户端
│   ├── app/
│   │   ├── src/main/java/com/bookkeeper/
│   │   │   ├── data/        # Room 数据层
│   │   │   ├── domain/      # 业务逻辑
│   │   │   ├── ui/          # Compose UI
│   │   │   └── sync/        # 同步模块
│   │   └── build.gradle.kts
│   └── build.gradle.kts
└── desktop/                 # 桌面客户端
    ├── src/
    │   ├── main/            # Electron 主进程
    │   ├── renderer/        # React 渲染进程
    │   └── database/        # SQLite 数据层
    ├── package.json
    └── electron-builder.json
```

## 快速开始

### Android
```bash
cd android
./gradlew installDebug
```

### Desktop
```bash
cd desktop
npm install
npm run dev
```

## 核心功能

- ✅ 快速记账（收入/支出）
- ✅ 分类管理（自定义分类 + 图标）
- ✅ 多账户支持（现金、银行卡、支付宝、微信等）
- ✅ 统计报表（日/周/月/年）
- ✅ 数据导出（CSV/JSON）
- ✅ 数据同步（手机 ↔ 电脑）
- ✅ 预算管理
- ✅ 标签系统
