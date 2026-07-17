# AeroSSH ProGuard Rules - Maximum Security

# Keep all security-critical classes with obfuscated names
-keepnames class com.companyname.aerossh.security.** { *; }
-keepnames class com.companyname.aerossh.crypto.** { *; }

# Obfuscate everything
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# Remove logging
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# SSHJ
-keep class net.schmizz.** { *; }
-dontwarn net.schmizz.**

# Security crypto
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Prevent string analysis of encryption keys
-keepclassmembers class com.companyname.aerossh.security.** {
    private *;
}

# Obfuscate SSH credentials
-keepclassmembers class com.companyname.aerossh.SshService {
    private char[] password;
}

# Remove debug info from crypto classes
-keepattributes !SourceFile,!LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile
