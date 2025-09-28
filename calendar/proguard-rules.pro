# Add project specific ProGuard rules here.

# Keep the Calendar module classes
-keep class com.phad.chatapp.features.calendar.** { *; }

# Keep model classes that will be serialized/deserialized
-keepclassmembers class com.phad.chatapp.features.calendar.models.** { *; }
-keepclassmembers class com.phad.chatapp.features.calendar.repository.CalendarEvent { *; }

# Preserve the special static methods that are required in view model classes
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Preserve the special static methods that are required in ViewModelProvider.Factory
-keepclassmembers class * extends androidx.lifecycle.ViewModelProvider$Factory {
    <init>(...);
}

# Preserve all annotations
-keepattributes *Annotation*

# Preserve all public classes
-keep public class * {
    public protected *;
} 