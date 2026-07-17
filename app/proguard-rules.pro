# Add project specific ProGuard rules here.
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-keep class net.sqlcipher.** { *; }
-keep class net.schmizz.** { *; }
-dontwarn net.schmizz.**
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
