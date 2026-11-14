# Keep stack-trace line numbers readable in crash logs
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# security-crypto (Tink) references compile-only annotations that are not in the APK.
# They are never used at runtime, so R8 can ignore them.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
