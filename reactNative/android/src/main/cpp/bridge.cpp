#include <jni.h>
#include <android/log.h>

#include "SimpleMultiPlayer.h"
#include "stream/FileInputStream.h"
#include "wav/WavStreamReader.h"
#include "SampleSource.h"

#include "vector"

static iolib::SimpleMultiPlayer sPlayer;
static std::vector<iolib::SampleSource*> sources;

extern "C"
JNIEXPORT jlong JNICALL
Java_com_armsaudio_ArmsaudioModule_testFunction(JNIEnv *env,jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, "test", "Test function successfully called.");
    return 17;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_preparePlayer(JNIEnv *env, jobject thiz) {
    sPlayer.setupAudioStream(2);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_resetPlayer(JNIEnv *env, jobject thiz) {
    sPlayer.unloadSampleData();
    sources.clear();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_armsaudio_ArmsaudioModule_loadTrack(JNIEnv *env, jobject thiz, jstring fileName) {
    auto source = new iolib::SampleSource(env->GetStringUTFChars(fileName, 0), 1);
    sources.push_back(source);

    return sources.size() - 1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_playAudioInternal(JNIEnv *env,jobject obj) {
    sPlayer.startStream();
    for (int i = 0; i < sources.size(); ++i) {
        sPlayer.triggerDown(i);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_pauseAudio(JNIEnv *env, jobject thiz) {
    sPlayer.pause();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_resumeAudio(JNIEnv *env, jobject thiz) {
    sPlayer.resume();
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_armsaudio_ArmsaudioModule_getCurrentPosition(JNIEnv *env, jobject thiz) {
    if (sources.empty())
        return 0;

    return sources[0]->getPosition();
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_armsaudio_ArmsaudioModule_getAmplitudes(JNIEnv *env, jobject thiz) {
    auto length = sources.size();
    auto amplitudesCpp = new float[length];
    for (int i = 0; i < length; i++) {
        amplitudesCpp[i] = sources[i]->getAmplitude();
    }

    auto floatClass = env->FindClass("java/lang/Float");
    auto amplitudesJava = env->NewObjectArray(length, floatClass, nullptr);
    jmethodID floatConstructorID = env->GetMethodID(floatClass, "<init>", "(F)V");
    for(int i = 0; i < length; i++) {
        auto floatObj = env->NewObject(floatClass, floatConstructorID, amplitudesCpp[i]);
        env->SetObjectArrayElement(amplitudesJava, i, floatObj);
    }

    delete[] amplitudesCpp;
    return amplitudesJava;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_setPosition(JNIEnv *env, jobject thiz, jfloat position) {
    sPlayer.pause();
    sPlayer.setPosition(position);
    for (auto &source : sources) {
        source->setPosition(position);
    }

    sPlayer.resume();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_setTrackVolume(
        JNIEnv *env,
        jobject thiz,
        jint track_num,
        jfloat volume) {
    sources[track_num]->setGain(volume);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_setTrackPan(
        JNIEnv *env,
        jobject thiz,
        jint track_num,
        jfloat pan) {
    sources[track_num]->setPan(pan);
}