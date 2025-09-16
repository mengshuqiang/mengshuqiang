# 前台服务插件 (FOREGROUND_SERVICE_TYPE_NONE)

## 简介

本插件为 uni-app x 提供 Android 前台服务功能，使用 `UTSAndroid.getAppContext()` API 实现，完全兼容 Android 14+ 的 `FOREGROUND_SERVICE_TYPE_NONE` 要求。

## 特性

- ✅ 使用 `UTSAndroid.getAppContext()!.startForegroundService()` 启动服务
- ✅ 支持 Android 14 (API 34) 及以上版本
- ✅ 自动处理不同 Android 版本的兼容性
- ✅ 提供简单易用的 API 接口
- ✅ 完整的 TypeScript 类型支持

## 安装

### 通过 uni_modules 安装

1. 将 `uni_modules/foreground-service` 目录复制到你的项目中
2. 在 HBuilderX 中重新编译项目

## 使用方法

### 1. 导入插件

```typescript
import { 
  startForegroundService, 
  stopForegroundService, 
  isForegroundServiceRunning 
} from '@/uni_modules/foreground-service/utssdk/app-android/index.uts';
```

### 2. 启动前台服务

```typescript
// 启动服务
const success = startForegroundService();
if (success) {
  console.log('前台服务已启动');
} else {
  console.error('启动失败');
}
```

### 3. 停止前台服务

```typescript
// 停止服务
const success = stopForegroundService();
if (success) {
  console.log('前台服务已停止');
}
```

### 4. 检查服务状态

```typescript
// 检查服务是否运行
const isRunning = isForegroundServiceRunning();
console.log('服务状态:', isRunning ? '运行中' : '已停止');
```

## 核心实现

插件内部使用 `UTSAndroid.getAppContext()` 获取应用上下文：

```typescript
// 启动前台服务的核心代码
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    UTSAndroid.getAppContext()!.startForegroundService(serviceIntent);
} else {
    UTSAndroid.getAppContext()!.startService(serviceIntent);
}
```

## 配置说明

### manifest.json 配置

```json
{
  "app-plus": {
    "distribute": {
      "android": {
        "permissions": [
          "android.permission.FOREGROUND_SERVICE",
          "android.permission.FOREGROUND_SERVICE_TYPE_NONE",
          "android.permission.POST_NOTIFICATIONS"
        ],
        "targetSdkVersion": 34
      }
    }
  }
}
```

### 服务类型说明

- `TYPE_NONE (0)`: 不指定特定类型，适用于短期任务
- 系统限制：可能在几分钟后停止服务
- 适用场景：临时性后台操作

## API 文档

### startForegroundService()

启动前台服务

**返回值**
- `boolean`: 启动成功返回 true，失败返回 false

### stopForegroundService()

停止前台服务

**返回值**
- `boolean`: 停止成功返回 true，失败返回 false

### isForegroundServiceRunning()

检查服务是否正在运行

**返回值**
- `boolean`: 运行中返回 true，否则返回 false

## 注意事项

### Android 14+ 要求

- 必须在 manifest 中声明 `FOREGROUND_SERVICE_TYPE_NONE` 权限
- 必须指定 `foregroundServiceType`
- 必须显示前台通知

### 使用限制

1. **时间限制**: TYPE_NONE 服务可能在几分钟后被系统停止
2. **不适合长期任务**: 长时间运行建议使用其他服务类型
3. **需要通知权限**: Android 13+ 需要用户授予通知权限

### 最佳实践

1. 尽快完成任务并主动停止服务
2. 提供清晰的通知信息
3. 处理服务异常情况
4. 考虑使用 WorkManager 替代长时间任务

## 兼容性

| 平台 | 支持版本 | 说明 |
|------|---------|------|
| Android | 5.0+ (API 21+) | 完全支持 |
| iOS | - | 不支持 |
| Web | - | 不支持 |

## 常见问题

### Q: 服务启动失败？

检查以下几点：
1. 确认已添加必要权限
2. 确认 targetSdkVersion >= 34
3. 检查通知权限是否已授予

### Q: 服务自动停止？

TYPE_NONE 有时间限制，如需长时间运行：
1. 考虑使用 `specialUse` 类型
2. 使用 WorkManager 替代
3. 分解为多个短期任务

### Q: 如何调试？

使用 Android Studio 查看 Logcat：
```bash
adb logcat | grep -E "ForegroundService|MyForegroundService"
```

## 更新日志

### v1.0.0 (2024-01)
- 初始版本发布
- 支持 FOREGROUND_SERVICE_TYPE_NONE
- 使用 UTSAndroid.getAppContext() API

## 许可证

MIT License

## 联系方式

如有问题或建议，请提交 Issue。