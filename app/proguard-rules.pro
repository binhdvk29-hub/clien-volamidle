# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class com.example.webview.GameTabManager$AndroidGameBridge {
    public *;
}

-keepattributes JavascriptInterface
-keepattributes *Annotation*
