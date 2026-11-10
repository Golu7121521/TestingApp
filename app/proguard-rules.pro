-keep class androidx.media3.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

## NewPipeExtractor
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.**
# Rhino's JSON support optionally does java.beans introspection, which doesn't exist on Android
# and isn't actually reachable through NewPipeExtractor's usage of Rhino; safe to just ignore.
-dontwarn java.beans.**
-dontwarn java.applet.**
-dontwarn javax.swing.**
