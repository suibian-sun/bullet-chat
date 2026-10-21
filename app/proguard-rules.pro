# 系统通过清单文件绑定的组件：避免被裁剪或重命名
-keep class com.suibian.bulletchat.service.BulletNotificationListener { *; }
-keep class com.suibian.bulletchat.service.OverlayService { *; }
-keep class com.suibian.bulletchat.service.BootReceiver { *; }
-keep class com.suibian.bulletchat.MainActivity { *; }
-keep class com.suibian.bulletchat.AppPickerActivity { *; }
-keep class com.suibian.bulletchat.ChangelogActivity { *; }

# 设置项枚举通过 valueOf 字符串还原
-keepclassmembers enum com.suibian.bulletchat.data.DisplayOrientation {
    public static ** valueOf(java.lang.String);
}

# 反射调用的自由窗口 API 依赖参数类型，保留方法签名即可（平台类不受影响）
-dontwarn android.app.ActivityOptions
