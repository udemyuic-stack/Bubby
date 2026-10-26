Add inside `<application>`:

```xml
<provider
    android:name=".StickerProvider"
    android:authorities="com.example.buddy.stickers"
    android:exported="false"
    android:grantUriPermissions="true" />
```

No permissions needed.
