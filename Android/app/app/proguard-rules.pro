# OkHttp/Okio platform compat — these warnings are safe to silence.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep our serializable model fields by name so org.json reflection / Glance
# state restoration don't break under R8 in release builds.
-keepclassmembers class edu.bmu.attendance.data.** {
    <fields>;
}
