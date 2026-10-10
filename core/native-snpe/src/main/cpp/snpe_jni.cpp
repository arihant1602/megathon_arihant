// Fine-tuned Laya on the Hexagon NPU through the SNPE C API. The Java SNPE API only takes float/TF8/TF16
// tensors; Laya's inputs are int32 token ids (quantizing them would corrupt them), so int32 user buffers are
// bound here. The network is built once and kept warm; each run copies ids/mask in and the logits out.
#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <string>
#include <vector>
#include "DlContainer/DlContainer.h"
#include "DlSystem/DlError.h"
#include "DlSystem/IUserBuffer.h"
#include "DlSystem/RuntimeList.h"
#include "DlSystem/StringList.h"
#include "DlSystem/TensorShape.h"
#include "DlSystem/UserBufferMap.h"
#include "DlSystem/IBufferAttributes.h"
#include "SNPE/SNPE.h"
#include "SNPE/SNPEBuilder.h"
#include "SNPE/SNPEUtil.h"

namespace {
struct Buffer { std::string name; std::vector<uint8_t> data; Snpe_IUserBuffer_Handle_t handle = nullptr; };
struct Net {
    Snpe_SNPE_Handle_t snpe = nullptr;
    Snpe_UserBufferMap_Handle_t inputs = nullptr, outputs = nullptr;
    std::vector<Buffer> in, out;
};

void fail(JNIEnv *env, const std::string &what) {
    std::string message = what + ": " + Snpe_ErrorCode_GetLastErrorString();
    __android_log_print(ANDROID_LOG_ERROR, "incident_snpe", "%s", message.c_str());
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}

void release(Net *net) {
    if (!net) return;
    for (auto *list : {&net->in, &net->out}) for (auto &b : *list) if (b.handle) Snpe_IUserBuffer_Delete(b.handle);
    if (net->inputs) Snpe_UserBufferMap_Delete(net->inputs);
    if (net->outputs) Snpe_UserBufferMap_Delete(net->outputs);
    if (net->snpe) Snpe_SNPE_Delete(net->snpe);
    delete net;
}

// Tightly packed buffer for one tensor; int32 for inputs, float32 for outputs (SNPE dequantizes).
bool bind(Net *net, const char *name, bool input, Snpe_UserBufferMap_Handle_t map) {
    auto attrs = Snpe_SNPE_GetInputOutputBufferAttributes(net->snpe, name);
    if (!attrs) return false;
    auto dims = Snpe_IBufferAttributes_GetDims(attrs);
    size_t rank = Snpe_TensorShape_Rank(dims), count = 1;
    for (size_t i = 0; i < rank; ++i) count *= Snpe_TensorShape_At(dims, i);
    std::vector<size_t> strides(rank);
    size_t stride = 4;
    for (size_t i = rank; i-- > 0;) { strides[i] = stride; stride *= Snpe_TensorShape_At(dims, i); }
    auto strideShape = Snpe_TensorShape_CreateDimsSize(strides.data(), rank);
    auto encoding = input ? Snpe_UserBufferEncodingIntN_Create(32) : Snpe_UserBufferEncodingFloat_Create();
    auto &list = input ? net->in : net->out;
    list.push_back(Buffer{name, std::vector<uint8_t>(count * 4)});
    auto &buffer = list.back();
    buffer.handle = Snpe_Util_CreateUserBuffer(buffer.data.data(), buffer.data.size(), strideShape, encoding);
    if (input) Snpe_UserBufferEncodingIntN_Delete(encoding); else Snpe_UserBufferEncodingFloat_Delete(encoding);
    Snpe_TensorShape_Delete(strideShape); Snpe_TensorShape_Delete(dims); Snpe_IBufferAttributes_Delete(attrs);
    if (!buffer.handle) return false;
    Snpe_UserBufferMap_Add(map, name, buffer.handle);
    return true;
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_org_seedhecode_incidentassistant_engine_NativeSnpe_load(JNIEnv *env, jobject, jstring path) {
    const char *file = env->GetStringUTFChars(path, nullptr);
    auto container = Snpe_DlContainer_Open(file);
    env->ReleaseStringUTFChars(path, file);
    if (!container) { fail(env, "Cannot open DLC"); return 0; }
    auto runtimes = Snpe_RuntimeList_Create();
    Snpe_RuntimeList_Add(runtimes, SNPE_RUNTIME_DSP);   // HTP on the 8 Elite; no silent CPU fallback
    auto builder = Snpe_SNPEBuilder_Create(container);
    Snpe_SNPEBuilder_SetRuntimeProcessorOrder(builder, runtimes);
    Snpe_SNPEBuilder_SetUseUserSuppliedBuffers(builder, 1);
    Snpe_SNPEBuilder_SetPerformanceProfile(builder, SNPE_PERFORMANCE_PROFILE_BURST);
    auto net = new Net();
    net->snpe = Snpe_SNPEBuilder_Build(builder);
    Snpe_SNPEBuilder_Delete(builder); Snpe_RuntimeList_Delete(runtimes); Snpe_DlContainer_Delete(container);
    if (!net->snpe) { delete net; fail(env, "Cannot build SNPE network on the DSP"); return 0; }
    net->inputs = Snpe_UserBufferMap_Create(); net->outputs = Snpe_UserBufferMap_Create();
    for (int pass = 0; pass < 2; ++pass) {
        auto names = pass == 0 ? Snpe_SNPE_GetInputTensorNames(net->snpe) : Snpe_SNPE_GetOutputTensorNames(net->snpe);
        bool ok = names != nullptr;
        for (size_t i = 0; ok && i < Snpe_StringList_Size(names); ++i)
            ok = bind(net, Snpe_StringList_At(names, i), pass == 0, pass == 0 ? net->inputs : net->outputs);
        if (names) Snpe_StringList_Delete(names);
        if (!ok) { release(net); fail(env, "Cannot bind SNPE user buffers"); return 0; }
    }
    return reinterpret_cast<jlong>(net);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_seedhecode_incidentassistant_engine_NativeSnpe_run(JNIEnv *env, jobject, jlong handle, jintArray ids, jintArray mask) {
    auto net = reinterpret_cast<Net *>(handle);
    if (!net || net->out.size() != 1) { fail(env, "Invalid SNPE handle"); return nullptr; }
    for (auto &b : net->in) {
        jintArray source = b.name == "input_ids" ? ids : b.name == "attention_mask" ? mask : nullptr;
        if (!source || env->GetArrayLength(source) * 4 != (jsize)b.data.size()) { fail(env, "Unexpected input " + b.name); return nullptr; }
        env->GetIntArrayRegion(source, 0, env->GetArrayLength(source), reinterpret_cast<jint *>(b.data.data()));
    }
    if (Snpe_SNPE_ExecuteUserBuffers(net->snpe, net->inputs, net->outputs) != SNPE_SUCCESS) { fail(env, "SNPE execute failed"); return nullptr; }
    auto &o = net->out[0];
    jfloatArray result = env->NewFloatArray(o.data.size() / 4);
    env->SetFloatArrayRegion(result, 0, o.data.size() / 4, reinterpret_cast<const jfloat *>(o.data.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_org_seedhecode_incidentassistant_engine_NativeSnpe_release(JNIEnv *, jobject, jlong handle) {
    release(reinterpret_cast<Net *>(handle));
}
