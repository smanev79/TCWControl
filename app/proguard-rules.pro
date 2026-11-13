# kotlinx.serialization keeps generated serializers via its own consumer rules.
-dontwarn org.slf4j.**

# Classes of the original Java app's saved state (files/teracom.state), read with Java
# serialization: names and fields must stay as they are.
-keep class cc.teracom.app.android.model.** { *; }

# Tink (used by androidx.security-crypto) refers to annotations not present at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
