# Add project specific ProGuard rules here.
-keep class com.aurora.filemanager.data.local.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn kotlin.**
