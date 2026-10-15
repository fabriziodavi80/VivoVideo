#include <jni.h>
#include "stretch_core.h"

extern "C" JNIEXPORT jfloatArray JNICALL
Java_it_fabriziodavi_vivovideo_NativeStretch_stretch(
        JNIEnv *env, jobject /*thiz*/,
        jfloatArray input, jint channels, jint sampleRate,
        jdoubleArray segEnd, jdoubleArray speed) {
    const jsize n = env->GetArrayLength(input);
    std::vector<float> in(static_cast<size_t>(n));
    env->GetFloatArrayRegion(input, 0, n, in.data());

    vivo::SpeedCurve curve;
    const jsize segs = env->GetArrayLength(segEnd);
    curve.segEnd.resize(size_t(segs));
    curve.speed.resize(size_t(segs));
    env->GetDoubleArrayRegion(segEnd, 0, segs, curve.segEnd.data());
    env->GetDoubleArrayRegion(speed, 0, segs, curve.speed.data());

    std::vector<float> out = vivo::stretchVariable(in, channels, sampleRate, curve);

    jfloatArray result = env->NewFloatArray(jsize(out.size()));
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, jsize(out.size()), out.data());
    }
    return result;
}
