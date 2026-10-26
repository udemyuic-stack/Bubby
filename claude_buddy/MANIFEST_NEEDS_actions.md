# Manifest needs (actions owner)

Permissions (top level):
```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="com.android.alarm.permission.SET_ALARM" />
```
(Torch, volume, media keys need no permission. Calendar/notification/exact-alarm perms belong to the tasks owner.)

Package visibility:
```xml
<queries>
    <intent><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent>
    <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="https" /></intent>
    <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="geo" /></intent>
    <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="tel" /></intent>
    <intent><action android:name="android.media.action.MEDIA_PLAY_FROM_SEARCH" /></intent>
    <intent><action android:name="android.intent.action.SET_ALARM" /></intent>
    <intent><action android:name="android.intent.action.SET_TIMER" /></intent>
</queries>
```

Service (inside `<application>`):
```xml
<service
    android:name=".BuddyAccess"
    android:exported="true"
    android:label="Claude Buddy"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_config" />
</service>
```
