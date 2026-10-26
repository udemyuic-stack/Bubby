Add inside <application>:

```xml
<service
    android:name=".BuddyNotifications"
    android:exported="true"
    android:label="Buddy"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

No extra <uses-permission> needed (user enables access in Settings via BuddyNotifications.openSettings).
