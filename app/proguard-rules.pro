# JSch uses reflection internally
-keep class com.jcraft.jsch.** { *; }

# AndroidX security-crypto
-keep class androidx.security.crypto.** { *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }
