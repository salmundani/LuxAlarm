# Room-specific ProGuard rules
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class com.dsalmun.luxalarm.data.** { *; }
-keep class androidx.room.EmptyResultSetException