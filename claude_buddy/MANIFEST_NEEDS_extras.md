# Manifest additions (inside <manifest>/<application>)

Permissions (optional weather): `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>` (request at runtime), `<uses-permission android:name="android.permission.INTERNET"/>` (likely present).

```xml
<service android:name=".BuddyTile" android:exported="true"
    android:label="Buby" android:icon="@drawable/ic_tile"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE"/></intent-filter>
</service>

<activity android:name=".ShareActivity" android:exported="true"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:excludeFromRecents="true" android:taskAffinity=""
    android:label="@string/share_to_buby">
    <intent-filter>
        <action android:name="android.intent.action.SEND"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <data android:mimeType="text/plain"/>
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.PROCESS_TEXT"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <data android:mimeType="text/plain"/>
    </intent-filter>
</activity>
```
Label: use a string resource (values: «Отправить Buby», values-en: «Send to Buby», values-uz: «Bubyga yuborish»), or literal android:label.

BuddyService must handle action `com.example.buddy.LISTEN` (start voice listening). DeviceEvents needs no manifest entries.
