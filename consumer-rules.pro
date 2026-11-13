# Consumer ProGuard rules for k9o-barbican Android SDK.
# Keep the public wrapper surface + the uniffi/JNA bindings intact (JNA uses
# reflection over the generated interfaces, which R8 must not strip/rename).
-keep class dev.k9o.barbican.** { *; }
-keep class uniffi.k9o_keep_core_ffi_uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
