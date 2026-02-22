#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "TerminalJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_charliesbot_terminal_NativeBridge_nativeCreateSession(
        JNIEnv* env,
        jobject /* this */,
        jint cols,
        jint rows) {
    LOGI("createSession: cols=%d rows=%d", cols, rows);
    // TODO: Allocate real VT state machine via libghostty-vt
    // Return a dummy session handle for now
    return 1L;
}

JNIEXPORT void JNICALL
Java_com_charliesbot_terminal_NativeBridge_nativeProcessInput(
        JNIEnv* env,
        jobject /* this */,
        jlong sessionId,
        jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    LOGI("processInput: session=%lld bytes=%d", (long long)sessionId, len);
    // TODO: Feed bytes into libghostty-vt
}

JNIEXPORT void JNICALL
Java_com_charliesbot_terminal_NativeBridge_nativeResize(
        JNIEnv* env,
        jobject /* this */,
        jlong sessionId,
        jint cols,
        jint rows) {
    LOGI("resize: session=%lld cols=%d rows=%d", (long long)sessionId, cols, rows);
    // TODO: Trigger reflow in VT and Vulkan viewport resize
}

JNIEXPORT jstring JNICALL
Java_com_charliesbot_terminal_NativeBridge_nativeGetSessionState(
        JNIEnv* env,
        jobject /* this */,
        jlong sessionId) {
    LOGI("getSessionState: session=%lld", (long long)sessionId);
    // TODO: Return real session metadata from libghostty-vt
    std::string state = R"({"title":"","cursorRow":0,"cursorCol":0,"bell":false})";
    return env->NewStringUTF(state.c_str());
}

JNIEXPORT void JNICALL
Java_com_charliesbot_terminal_NativeBridge_nativeDestroy(
        JNIEnv* env,
        jobject /* this */,
        jlong sessionId) {
    LOGI("destroy: session=%lld", (long long)sessionId);
    // TODO: Tear down VT state, free Vulkan resources
}

} // extern "C"
