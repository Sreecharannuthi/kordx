#include <jni.h>
#include <string>
#include "android/log_macros.h"
#include "tfile.h"
#include "tfilestream.h"
#include "tpropertymap.h"
#include "fileref.h"
#include "TagLibHelper.h"

jclass audioMetadataParserClass = nullptr;
jmethodID audioMetadataParserPutTagMethodId = nullptr;
jmethodID audioMetadataParserPutPictureMethodId = nullptr;
jmethodID audioMetadataParserPutAudioPropertyMethodId = nullptr;

extern "C" {
JNIEXPORT jint
JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    const auto _audioMetadataParserClass = env->FindClass(
            "com/android/rockages/kordx/metaphony/AudioMetadataParser");
    audioMetadataParserClass = reinterpret_cast<jclass>(env->NewGlobalRef(
            _audioMetadataParserClass));
    audioMetadataParserPutTagMethodId = env->GetMethodID(
            audioMetadataParserClass,
            "putTag", "(Ljava/lang/String;Ljava/lang/String;)V");
    audioMetadataParserPutPictureMethodId = env->GetMethodID(
            audioMetadataParserClass,
            "putPicture", "(Ljava/lang/String;Ljava/lang/String;[B)V");
    audioMetadataParserPutAudioPropertyMethodId = env->GetMethodID(
            audioMetadataParserClass,
            "putAudioProperty", "(Ljava/lang/String;I)V");
    env->DeleteLocalRef(_audioMetadataParserClass);
    return JNI_VERSION_1_6;
}

JNIEXPORT void
JNI_OnUnload(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return;
    }
    env->DeleteGlobalRef(audioMetadataParserClass);
    audioMetadataParserClass = nullptr;
    audioMetadataParserPutTagMethodId = nullptr;
    audioMetadataParserPutPictureMethodId = nullptr;
    audioMetadataParserPutAudioPropertyMethodId = nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_android_rockages_kordx_metaphony_AudioMetadataParser_readMetadataNative(
        JNIEnv *env,
        jobject thiz,
        jstring filename,
        jint fd) {
    // Create a local-reference frame so tag-heavy files cannot overflow the
    // 512-entry JNI local-ref table. 32 slots is plenty for one loop iteration;
    // explicit DeleteLocalRef calls below keep the frame tidy even if the parser
    // is called many times from a long-running scan.
    if (env->PushLocalFrame(32) != JNI_OK) {
        return static_cast<jboolean>(false);
    }

    const char *filenameChars = env->GetStringUTFChars(filename, nullptr);
    if (!filenameChars) {
        env->PopLocalFrame(nullptr);
        return static_cast<jboolean>(false);
    }

    const auto stream = std::make_unique<TagLib::FileStream>(fd, true);
    const auto rawFile = TagLibHelper::detectParser(
            filenameChars,
            stream.get(),
            true,
            TagLib::AudioProperties::ReadStyle::Accurate);
    env->ReleaseStringUTFChars(filename, filenameChars);

    if (!rawFile) {
        env->PopLocalFrame(nullptr);
        return static_cast<jboolean>(false);
    }
    const std::unique_ptr<TagLib::File> file(rawFile);

    if (file->tag()) {
        const auto tags = file->properties();
        if (!tags.isEmpty()) {
            for (const auto &[key, values]: tags) {
                const auto jKey = env->NewStringUTF(key.toCString(true));
                if (!jKey) {
                    env->PopLocalFrame(nullptr);
                    return static_cast<jboolean>(false);
                }
                for (const auto &value: values) {
                    const auto jValue = env->NewStringUTF(value.toCString(true));
                    if (!jValue) {
                        env->PopLocalFrame(nullptr);
                        return static_cast<jboolean>(false);
                    }
                    env->CallVoidMethod(
                            thiz,
                            audioMetadataParserPutTagMethodId,
                            jKey,
                            jValue);
                    env->DeleteLocalRef(jValue);
                    if (env->ExceptionCheck()) {
                        env->PopLocalFrame(nullptr);
                        return static_cast<jboolean>(false);
                    }
                }
                env->DeleteLocalRef(jKey);
            }
        }

        const auto picture = file->complexProperties("PICTURE");
        for (const auto &x: picture) {
            const auto pictureType = x["pictureType"].toString();
            const auto mimeType = x["mimeType"].toString();
            const auto data = x["data"].toByteVector();
            const auto jPictureType = env->NewStringUTF(pictureType.toCString(true));
            const auto jMimeType = env->NewStringUTF(mimeType.toCString(true));
            const auto jDataSize = static_cast<jint>(data.size());
            const auto jData = env->NewByteArray(jDataSize);
            if (!jPictureType || !jMimeType || !jData) {
                if (jPictureType) env->DeleteLocalRef(jPictureType);
                if (jMimeType) env->DeleteLocalRef(jMimeType);
                if (jData) env->DeleteLocalRef(jData);
                env->PopLocalFrame(nullptr);
                return static_cast<jboolean>(false);
            }
            env->SetByteArrayRegion(
                    jData,
                    0,
                    jDataSize,
                    reinterpret_cast<const jbyte *>(data.data()));
            env->CallVoidMethod(
                    thiz,
                    audioMetadataParserPutPictureMethodId,
                    jPictureType,
                    jMimeType,
                    jData);
            env->DeleteLocalRef(jPictureType);
            env->DeleteLocalRef(jMimeType);
            env->DeleteLocalRef(jData);
            if (env->ExceptionCheck()) {
                env->PopLocalFrame(nullptr);
                return static_cast<jboolean>(false);
            }
        }
    }

    const auto audioProperties = file->audioProperties();
    if (audioProperties) {
        const auto putAudioProperty = [&](const char *key, jint value) -> bool {
            const auto jKey = env->NewStringUTF(key);
            if (!jKey) {
                env->PopLocalFrame(nullptr);
                return false;
            }
            env->CallVoidMethod(
                    thiz,
                    audioMetadataParserPutAudioPropertyMethodId,
                    jKey,
                    value);
            env->DeleteLocalRef(jKey);
            if (env->ExceptionCheck()) {
                env->PopLocalFrame(nullptr);
                return false;
            }
            return true;
        };
        if (!putAudioProperty("BITRATE", static_cast<jint>(audioProperties->bitrate()))) {
            return static_cast<jboolean>(false);
        }
        if (!putAudioProperty("LENGTH_SECONDS", static_cast<jint>(audioProperties->lengthInSeconds()))) {
            return static_cast<jboolean>(false);
        }
        if (!putAudioProperty("SAMPLE_RATE", static_cast<jint>(audioProperties->sampleRate()))) {
            return static_cast<jboolean>(false);
        }
        if (!putAudioProperty("CHANNELS", static_cast<jint>(audioProperties->channels()))) {
            return static_cast<jboolean>(false);
        }
    }

    env->PopLocalFrame(nullptr);
    return static_cast<jboolean>(true);
}
}