#include <jni.h>
#include "react-native-armnativesaudio.h"

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_armnativesaudio_ArmnativesaudioModule_nativeMultiply(JNIEnv *env, jclass type, jdouble a, jdouble b) {
    return armnativesaudio::multiply(a, b);
}
