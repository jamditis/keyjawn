# JSch uses reflection internally
-keep class com.jcraft.jsch.** { *; }

# AndroidX security-crypto
-keep class androidx.security.crypto.** { *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }

# JSch optional dependencies (not present on Android)
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

# Google Tink (used by security-crypto) annotations
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn javax.annotation.concurrent.GuardedBy
