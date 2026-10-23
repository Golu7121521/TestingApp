# Add project specific ProGuard rules here.

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

# Media3 / ExoPlayer ships its own consumer-rules.pro (kept automatically),
# this just silences harmless warnings from optional codec/network classes
# that this app never uses at runtime.
-dontwarn org.checkerframework.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
