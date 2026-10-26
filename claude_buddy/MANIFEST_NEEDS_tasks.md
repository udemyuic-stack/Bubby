# Manifest needs (tasks)

Permissions:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.READ_CALENDAR"/>
<uses-permission android:name="android.permission.WRITE_CALENDAR"/>
```
USE_EXACT_ALARM is not needed (user grants SCHEDULE_EXACT_ALARM via Settings; Reminders falls back to inexact).

Inside `<application>`:
```xml
<receiver android:name=".ReminderReceiver" android:exported="false"/>
<receiver android:name=".BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
    </intent-filter>
</receiver>
```
Lead: BuddyService must handle onStartCommand action `com.example.buddy.SPEAK` (extra "text") without calling startForeground again.
