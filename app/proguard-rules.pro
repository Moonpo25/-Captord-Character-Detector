# Add project specific ProGuard rules here.
# You can control R8 optimizations and obfuscation.

# Keep Compose/Kotlin reflections intact if needed
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room/Datastore classes if needed
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
