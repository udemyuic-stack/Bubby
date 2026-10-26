# LiteRT-LM looks classes up by name from native code (JNI): R8 must not rename or strip them.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
