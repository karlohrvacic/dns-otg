-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Glance creates widget actions by class name, so shrinking must keep them (and their no-argument constructors).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager (used by Glance) creates its Room database by class name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
