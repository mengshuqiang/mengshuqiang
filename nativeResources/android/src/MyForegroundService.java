package io.dcloud.uniapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

public class MyForegroundService extends Service {
    private static final String TAG = "MyForegroundService";
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        
        // 创建通知
        Notification notification = createNotification();
        
        // 启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 需要指定服务类型
            // FOREGROUND_SERVICE_TYPE_NONE = 0
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-13
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE);
        } else {
            // Android 9 及以下
            startForeground(NOTIFICATION_ID, notification);
        }
        
        // 执行后台任务
        performBackgroundWork();
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "前台服务通道",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于前台服务的通知通道");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        // 创建点击通知时的Intent
        Intent notificationIntent = new Intent();
        notificationIntent.setClassName(this, "io.dcloud.PandoraEntry");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("应用正在运行")
            .setContentText("前台服务 (TYPE_NONE) 运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setSound(null)
            .setVibrate(null);
        
        return builder.build();
    }

    private void performBackgroundWork() {
        // 在这里执行你的后台任务
        // 注意：使用 TYPE_NONE 时，系统可能会在一定时间后停止服务
        // 建议：
        // 1. 尽快完成任务
        // 2. 考虑使用 WorkManager 替代长时间运行的任务
        // 3. 如果需要长时间运行，考虑使用其他服务类型
        
        new Thread(() -> {
            try {
                Log.d(TAG, "开始执行后台任务");
                // 模拟一些工作
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(1000);
                    Log.d(TAG, "后台任务进度: " + (i + 1) + "/10");
                }
                Log.d(TAG, "后台任务完成");
                
                // 任务完成后可以选择停止服务
                // stopSelf();
            } catch (InterruptedException e) {
                Log.e(TAG, "后台任务被中断", e);
            }
        }).start();
    }
}