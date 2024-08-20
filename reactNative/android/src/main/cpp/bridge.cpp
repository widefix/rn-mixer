#include <jni.h>
#include <android/log.h>

#include "SimpleMultiPlayer.h"
#include "stream/FileInputStream.h"
#include "wav/WavStreamReader.h"
#include "SampleBuffer.h"
#include "SampleSource.h"

#include "vector"
#include "fstream"
#include <fcntl.h>

static iolib::SimpleMultiPlayer sPlayer;
static std::vector<iolib::SampleBuffer*> buffers;
static std::vector<iolib::OneShotSampleSource*> sources;

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
    buffers.clear();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_armsaudio_ArmsaudioModule_loadTrack(JNIEnv *env, jobject thiz, jstring fileName) {
    auto f = open(env->GetStringUTFChars(fileName, 0), O_RDONLY);
    auto stream = parselib::FileInputStream(f);
    auto reader = parselib::WavStreamReader(&stream);
    reader.parse();
    auto buffer = new iolib::SampleBuffer();
    buffer->loadSampleData(&reader);
    buffers.push_back(buffer);
    auto source = new iolib::OneShotSampleSource(buffer, 1);
    sources.push_back(source);
    sPlayer.addSampleSource(source, buffer);

    close(f);

    return buffers.size() - 1;
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
JNIEXPORT void JNICALL
Java_com_armsaudio_ArmsaudioModule_setPosition(JNIEnv *env, jobject thiz, jfloat position) {
    sPlayer.pause();
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