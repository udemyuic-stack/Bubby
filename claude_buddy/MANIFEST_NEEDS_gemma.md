# Manifest / Gradle needs (gemma)

Gradle (app): `implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")`

Permissions:
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

Service:
```xml
<service android:name=".ModelDownloadService" android:exported="false"
    android:foregroundServiceType="dataSync"/>
```

Application (LiteRT-LM GPU backend loads vendor OpenCL libs):
```xml
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
<uses-native-library android:name="libOpenCL-pixel.so" android:required="false"/>
```
