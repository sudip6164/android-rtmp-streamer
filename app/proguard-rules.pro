# RootEncoder and its encoder classes are used through reflection free
# public APIs, but keep them to be safe in release builds.
-keep class com.pedro.** { *; }
-dontwarn com.pedro.**
