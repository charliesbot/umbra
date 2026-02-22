# Keep NativeBridge native methods from ProGuard stripping
-keep class com.charliesbot.terminal.NativeBridge {
    native <methods>;
}
