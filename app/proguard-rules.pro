# TensorFlow Lite (JNI + GPU delegate use reflection/native lookups)
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# PDFBox-Android (fonts, filters and resources are loaded reflectively)
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
-dontwarn java.awt.**

# Keep line numbers for readable crash traces
-keepattributes SourceFile,LineNumberTable
