# Add project specific ProGuard rules here.

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

-dontwarn org.checkerframework.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# libVLC talks to its native (C/C++) engine over JNI, which looks up these
# Java classes/fields/methods by name - they must not be renamed or stripped.
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**
