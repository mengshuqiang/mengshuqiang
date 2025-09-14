# uni-app x FOREGROUND_SERVICE_TYPE_NONE 配置指南

## 概述

本项目演示了如何在 uni-app x 中配置和使用 `FOREGROUND_SERVICE_TYPE_NONE` 前台服务类型。这是 Android 14 (API 34) 引入的新要求。

## 什么是 FOREGROUND_SERVICE_TYPE_NONE？

`FOREGROUND_SERVICE_TYPE_NONE` 是 Android 14+ 中的一种前台服务类型，适用于：
- 不符合其他特定服务类型的短期任务
- 临时性的后台操作
- 过渡性的服务需求

## 配置步骤

### 1. manifest.json 配置

在 `manifest.json` 中添加必要的权限和配置：

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
        "targetSdkVersion": 34,
        "foregroundServiceTypes": ["none"]
      }
    }
  }
}
```

### 2. AndroidManifest.xml 配置

在 `nativeResources/android/AndroidManifest.xml` 中声明服务：

```xml
<service
    android:name=".service.MyForegroundService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="none">
</service>
```

### 3. 服务实现

创建 Java 服务类，使用 `ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE`：

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    startForeground(NOTIFICATION_ID, notification, 
        ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE);
}
```

## 使用限制

### TYPE_NONE 的限制
- **时间限制**：系统可能在几分钟后停止服务
- **不适合长期任务**：仅用于短期、临时性操作
- **需要通知**：必须显示前台通知

### 替代方案

如果需要长时间运行，考虑使用：

1. **specialUse** - 特殊用途服务
   ```xml
   android:foregroundServiceType="specialUse"
   ```

2. **dataSync** - 数据同步
   ```xml
   android:foregroundServiceType="dataSync"
   ```

3. **WorkManager** - 推荐用于后台任务
   ```java
   WorkManager.getInstance(context)
       .enqueue(new OneTimeWorkRequest.Builder(MyWorker.class).build());
   ```

## 最佳实践

### 1. 快速完成任务
```java
// 设置超时机制
Handler handler = new Handler();
handler.postDelayed(() -> {
    stopSelf(); // 自动停止服务
}, 30000); // 30秒超时
```

### 2. 提供清晰的通知
```java
NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
    .setContentTitle("正在处理")
    .setContentText("任务将很快完成")
    .setProgress(100, progress, false);
```

### 3. 优雅降级
```java
if (Build.VERSION.SDK_INT >= 34) {
    // 使用 TYPE_NONE
} else {
    // 使用传统方式
}
```

## 测试要点

1. **权限检查**
   - 确保已授予通知权限
   - 检查前台服务权限

2. **版本兼容**
   - 在 Android 14+ 设备测试
   - 验证低版本兼容性

3. **服务生命周期**
   - 测试服务启动/停止
   - 验证异常情况处理

## 常见问题

### Q: 服务被系统杀死？
A: TYPE_NONE 有时间限制，考虑使用其他服务类型或 WorkManager。

### Q: 通知不显示？
A: 检查通知权限和通道配置。

### Q: 低版本设备崩溃？
A: 添加版本判断，使用兼容性处理。

## 相关资源

- [Android 前台服务文档](https://developer.android.com/develop/background-work/services/foreground-services)
- [uni-app x 文档](https://doc.dcloud.net.cn/uni-app-x/)
- [Android 14 行为变更](https://developer.android.com/about/versions/14/behavior-changes-14)

## 示例代码

完整示例代码请参考：
- `/pages/index/index.uvue` - UI 界面
- `/nativeResources/android/src/MyForegroundService.java` - 服务实现
- `/manifest.json` - 配置文件