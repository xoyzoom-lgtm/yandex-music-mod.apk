// Discord Rich Presence для YM Mod через Discord Social SDK.
//
// Используется режим без авторизации: SetApplicationId + UpdateRichPresence, SDK передаёт
// активность в установленное приложение Discord (Android, Social SDK 1.10+).
// Kotlin-сторона: src/presenceDiscord/java/.../discord/DiscordNative.kt.

#define DISCORDPP_IMPLEMENTATION  // ровно в одном файле проекта
#include "discordpp.h"

#include <android/log.h>
#include <jni.h>

#include <cstdint>
#include <memory>
#include <string>

namespace {

constexpr const char* kTag = "YmModDiscord";

JavaVM* gVm = nullptr;
std::shared_ptr<discordpp::Client> gClient;
jobject gListener = nullptr;
jmethodID gOnResult = nullptr;

std::string FromUtf8(JNIEnv* env, jbyteArray bytes) {
  if (bytes == nullptr) return {};
  const jsize length = env->GetArrayLength(bytes);
  std::string result(static_cast<size_t>(length), '\0');
  env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(result.data()));
  return result;
}

// Колбэки SDK вызываются внутри RunCallbacks, то есть на Kotlin-потоке, уже присоединённом к JVM.
void Report(bool success, const std::string& message) {
  if (gVm == nullptr || gListener == nullptr || gOnResult == nullptr) return;
  JNIEnv* env = nullptr;
  if (gVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return;

  jstring jmessage = env->NewStringUTF(message.c_str());
  env->CallVoidMethod(gListener, gOnResult, static_cast<jboolean>(success), jmessage);
  if (env->ExceptionCheck()) {
    env->ExceptionDescribe();
    env->ExceptionClear();
  }
  env->DeleteLocalRef(jmessage);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
  gVm = vm;
  return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_xoyzoom_ymmod_presence_discord_DiscordNative_init(JNIEnv* env, jclass /* clazz */,
                                                                 jlong applicationId, jobject listener) {
  if (gListener != nullptr) env->DeleteGlobalRef(gListener);
  gListener = env->NewGlobalRef(listener);

  jclass listenerClass = env->GetObjectClass(listener);
  gOnResult = env->GetMethodID(listenerClass, "onResult", "(ZLjava/lang/String;)V");
  env->DeleteLocalRef(listenerClass);

  if (!gClient) gClient = std::make_shared<discordpp::Client>();
  gClient->SetApplicationId(static_cast<uint64_t>(applicationId));
  __android_log_print(ANDROID_LOG_INFO, kTag, "Discord Social SDK initialized");
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_xoyzoom_ymmod_presence_discord_DiscordNative_update(JNIEnv* env, jclass /* clazz */,
                                                                   jbyteArray details, jbyteArray state,
                                                                   jbyteArray largeImage, jbyteArray largeText,
                                                                   jlong startSeconds, jlong endSeconds) {
  if (!gClient) return;

  discordpp::Activity activity;
  activity.SetType(discordpp::ActivityTypes::Listening);
  // В статусе пользователя показываем название трека, а не имя приложения.
  activity.SetStatusDisplayType(discordpp::StatusDisplayTypes::Details);
  activity.SetDetails(FromUtf8(env, details));
  activity.SetState(FromUtf8(env, state));

  if (startSeconds > 0 || endSeconds > 0) {
    discordpp::ActivityTimestamps timestamps;
    if (startSeconds > 0) timestamps.SetStart(static_cast<uint64_t>(startSeconds));
    if (endSeconds > 0) timestamps.SetEnd(static_cast<uint64_t>(endSeconds));
    activity.SetTimestamps(timestamps);
  }

  if (largeImage != nullptr) {
    // Внешний URL обложки (avatars.yandex.net) — Discord поддерживает внешние ссылки на ассеты.
    discordpp::ActivityAssets assets;
    assets.SetLargeImage(FromUtf8(env, largeImage));
    if (largeText != nullptr) assets.SetLargeText(FromUtf8(env, largeText));
    activity.SetAssets(assets);
  }

  gClient->UpdateRichPresence(activity, [](const discordpp::ClientResult& result) {
    Report(result.Successful(), result.Successful() ? std::string() : result.Error());
  });
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_xoyzoom_ymmod_presence_discord_DiscordNative_clear(JNIEnv* /* env */, jclass /* clazz */) {
  if (gClient) gClient->ClearRichPresence();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_xoyzoom_ymmod_presence_discord_DiscordNative_runCallbacks(JNIEnv* /* env */, jclass /* clazz */) {
  discordpp::RunCallbacks();
}
