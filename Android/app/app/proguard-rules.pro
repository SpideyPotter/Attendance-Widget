# OkHttp/Okio platform compat — these warnings are safe to silence.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink (androidx.security:security-crypto) references Error Prone annotations; not on Android classpath.
-dontwarn com.google.errorprone.annotations.**

# Keep our serializable model fields by name so org.json / SharedPreferences JSON
# doesn't break under R8 in release builds.
-keepclassmembers class edu.bmu.attendance.data.** {
    <fields>;
}

# Glance widgets + click callbacks must survive minify (ActionCallback / receivers
# are resolved reflectively from XML + app widget framework).
-keep class edu.bmu.attendance.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# WorkManager workers are constructed reflectively.
-keep class edu.bmu.attendance.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# Local notification receivers / schedulers.
-keep class edu.bmu.attendance.notify.** { *; }
