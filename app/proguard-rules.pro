# kotlinx.serialization DTOs are kept via the library's consumer rules; Retrofit/OkHttp/Room/Hilt ship their own.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class com.hxrsh.sawari.data.remote.**$$serializer { *; }
-keepclassmembers class com.hxrsh.sawari.data.remote.** { *** Companion; }
-keepclasseswithmembers class com.hxrsh.sawari.data.remote.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
