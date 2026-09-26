# Open Automotive Assistant — keep reflective Car API access
-keep class android.car.** { *; }
-dontwarn android.car.**

# ServiceLoader SPI (integrations + plugins)
-keep interface cc.opencar.assistant.api.VehicleIntegration { *; }
-keep interface cc.opencar.assistant.api.plugin.OaaPlugin { *; }
-keep class * implements cc.opencar.assistant.api.VehicleIntegration { <init>(); }
-keep class * implements cc.opencar.assistant.api.plugin.OaaPlugin { <init>(); }
-keepclassmembers class * implements cc.opencar.assistant.api.VehicleIntegration { public <init>(); }
-keepclassmembers class * implements cc.opencar.assistant.api.plugin.OaaPlugin { public <init>(); }
-keep resource META-INF/services/**
