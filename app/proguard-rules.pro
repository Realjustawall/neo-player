# Alpha 0.5 release uses R8 only for unreachable bytecode. Keep reflection/JNI-sensitive
# offline speech runtime intact so size optimization never removes user-facing capability.
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Room uses generated adapters, but keep database/entity metadata conservative for migration safety.
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
