BootReceiver intent-filter must also include:
<action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
(BOOT_COMPLETED + RECEIVE_BOOT_COMPLETED already present; ReminderReceiver needs no new filters - snooze/done use explicit intents.)
Optional (Android 11+ package visibility not required: intents are started directly).
