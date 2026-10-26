# Manifest needs: agentlink

Permissions: `android.permission.INTERNET` (probably already present). Nothing else; Keystore and KeyguardManager need none.

Inside `<application>`:

```xml
<activity
    android:name=".ConfirmActivity"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:exported="false"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:configChanges="orientation|screenSize|keyboardHidden" />
```

ConfirmActivity.request(ctx, cb) starts it with FLAG_ACTIVITY_NEW_TASK, so it also works from a service. Set
`ConfirmActivity.callback` yourself if you start it differently.

Optional, lead-owned MainActivity: to open the app when a `coucou://pair?...` link is scanned with the camera, add to MainActivity

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="coucou" android:host="pair" />
</intent-filter>
```

and pass `intent.dataString` to `parsePairLink` / the `agentPair` flow (always ask the user to confirm before pairing).
The app keeps a foreground service (BuddyService) alive; on Android 16 the first connection asks for local network access.
