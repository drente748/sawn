# Room / Kotlin metadata is handled by AGP defaults.
# Keep VpnService + AccessibilityService entry points referenced only from the manifest.
-keep class com.sawn.app.vpn.** { *; }
-keep class com.sawn.app.accessibility.** { *; }

# TF Lite Task Vision: AutoValue-generated classes reference the annotation at compile
# time only; R8 needs the (unshipped) dependency declared as don't-warn.
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue

# Strip all logging from release builds (defense against accidental sensitive logging).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
