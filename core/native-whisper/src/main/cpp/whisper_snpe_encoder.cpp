// Whisper's audio encoder on the Hexagon NPU. whisper.cpp calls an external encoder through its ANEForge hook
// (whisper_aneforge_*, enabled when ANEFORGE_ENCODER is set); on Android that file is only a stub, so the build
// replaces it with this SNPE implementation. ANEFORGE_ENCODER points at the FP16 HTP DLC of the encoder with the
// full 30 s window (input "mel" [1, n_mels, 3000], output "embd" [1, 1500, n_state]); WHISPER_NPU_ENCODER_SHORT
// optionally at a 10 s window graph (mel 1000 -> embd 500 x n_state, 18 ms instead of 190 ms), which the JNI selects
// for short turns via audio_ctx = 500. The decoder stays in whisper.cpp.
#include "aneforge/whisper-aneforge.h"
#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include "DlContainer/DlContainer.h"
#include "DlSystem/DlError.h"
#include "DlSystem/IBufferAttributes.h"
#include "DlSystem/IUserBuffer.h"
#include "DlSystem/RuntimeList.h"
#include "DlSystem/StringList.h"
#include "DlSystem/TensorShape.h"
#include "DlSystem/UserBufferMap.h"
#include "SNPE/SNPE.h"
#include "SNPE/SNPEBuilder.h"
#include "SNPE/SNPEUtil.h"

struct Net {
    Snpe_SNPE_Handle_t snpe = nullptr;
    Snpe_UserBufferMap_Handle_t inputs = nullptr, outputs = nullptr;
    Snpe_IUserBuffer_Handle_t in = nullptr, out = nullptr;
    std::vector<float> mel, embd;
};
struct whisper_aneforge_context { std::vector<Net *> nets; };

static void log_error(const char *what) {
    __android_log_print(ANDROID_LOG_ERROR, "whisper_snpe", "%s: %s", what, Snpe_ErrorCode_GetLastErrorString());
}

// Float user buffer for one tensor, tightly packed.
static Snpe_IUserBuffer_Handle_t bind(Snpe_SNPE_Handle_t snpe, const char *name, std::vector<float> &storage,
                                      Snpe_UserBufferMap_Handle_t map) {
    auto attrs = Snpe_SNPE_GetInputOutputBufferAttributes(snpe, name);
    if (!attrs) return nullptr;
    auto dims = Snpe_IBufferAttributes_GetDims(attrs);
    size_t rank = Snpe_TensorShape_Rank(dims), count = 1;
    for (size_t i = 0; i < rank; ++i) count *= Snpe_TensorShape_At(dims, i);
    std::vector<size_t> strides(rank);
    size_t stride = sizeof(float);
    for (size_t i = rank; i-- > 0;) { strides[i] = stride; stride *= Snpe_TensorShape_At(dims, i); }
    auto strideShape = Snpe_TensorShape_CreateDimsSize(strides.data(), rank);
    auto encoding = Snpe_UserBufferEncodingFloat_Create();
    storage.assign(count, 0.0f);
    auto buffer = Snpe_Util_CreateUserBuffer(storage.data(), count * sizeof(float), strideShape, encoding);
    Snpe_UserBufferEncodingFloat_Delete(encoding); Snpe_TensorShape_Delete(strideShape);
    Snpe_TensorShape_Delete(dims); Snpe_IBufferAttributes_Delete(attrs);
    if (buffer) Snpe_UserBufferMap_Add(map, name, buffer);
    return buffer;
}

static void free_net(Net *ctx) {
    if (!ctx) return;
    if (ctx->in) Snpe_IUserBuffer_Delete(ctx->in);
    if (ctx->out) Snpe_IUserBuffer_Delete(ctx->out);
    if (ctx->inputs) Snpe_UserBufferMap_Delete(ctx->inputs);
    if (ctx->outputs) Snpe_UserBufferMap_Delete(ctx->outputs);
    if (ctx->snpe) Snpe_SNPE_Delete(ctx->snpe);
    delete ctx;
}

static Net *load_net(const char *dlc) {
    auto container = Snpe_DlContainer_Open(dlc);
    if (!container) { log_error("cannot open encoder DLC"); return nullptr; }
    auto runtimes = Snpe_RuntimeList_Create();
    Snpe_RuntimeList_Add(runtimes, SNPE_RUNTIME_DSP);
    auto builder = Snpe_SNPEBuilder_Create(container);
    Snpe_SNPEBuilder_SetRuntimeProcessorOrder(builder, runtimes);
    Snpe_SNPEBuilder_SetUseUserSuppliedBuffers(builder, 1);
    Snpe_SNPEBuilder_SetPerformanceProfile(builder, SNPE_PERFORMANCE_PROFILE_BURST);
    auto ctx = new Net();
    ctx->snpe = Snpe_SNPEBuilder_Build(builder);
    Snpe_SNPEBuilder_Delete(builder); Snpe_RuntimeList_Delete(runtimes); Snpe_DlContainer_Delete(container);
    if (!ctx->snpe) { log_error("cannot build encoder on the DSP"); delete ctx; return nullptr; }
    ctx->inputs = Snpe_UserBufferMap_Create(); ctx->outputs = Snpe_UserBufferMap_Create();
    ctx->in = bind(ctx->snpe, "mel", ctx->mel, ctx->inputs);
    ctx->out = bind(ctx->snpe, "embd", ctx->embd, ctx->outputs);
    if (!ctx->in || !ctx->out) { log_error("cannot bind encoder buffers"); free_net(ctx); return nullptr; }
    __android_log_print(ANDROID_LOG_INFO, "whisper_snpe", "NPU encoder ready: %s (mel %zu, embd %zu)", dlc, ctx->mel.size(), ctx->embd.size());
    return ctx;
}

void whisper_aneforge_free(struct whisper_aneforge_context *ctx) {
    if (!ctx) return;
    for (auto *net : ctx->nets) free_net(net);
    delete ctx;
}

struct whisper_aneforge_context *whisper_aneforge_init(const char *dlc) {
    auto ctx = new whisper_aneforge_context();
    Net *full = load_net(dlc);
    if (!full) { delete ctx; return nullptr; }
    ctx->nets.push_back(full);
    if (const char *shortDlc = getenv("WHISPER_NPU_ENCODER_SHORT")) {
        if (Net *net = load_net(shortDlc)) ctx->nets.push_back(net);   // optional: without it every turn uses 30 s
    }
    return ctx;
}

void whisper_aneforge_encode(struct whisper_aneforge_context *actx, int64_t n_mel, int64_t n_len, const float *mel, float *out) {
    // Pick the graph whose window matches the mel the decoder expects (n_len = 2 * audio_ctx).
    Net *ctx = nullptr;
    for (auto *net : actx->nets) if (net->mel.size() == (size_t)(n_mel * n_len)) ctx = net;
    if (!ctx) {
        __android_log_print(ANDROID_LOG_ERROR, "whisper_snpe", "no encoder graph for mel %lld x %lld", (long long)n_mel, (long long)n_len);
        return;
    }
    memcpy(ctx->mel.data(), mel, ctx->mel.size() * sizeof(float));
    if (Snpe_SNPE_ExecuteUserBuffers(ctx->snpe, ctx->inputs, ctx->outputs) != SNPE_SUCCESS) {
        log_error("encoder execute failed");
        memset(out, 0, ctx->embd.size() * sizeof(float));
        return;
    }
    memcpy(out, ctx->embd.data(), ctx->embd.size() * sizeof(float));   // [n_ctx, n_state] = ggml [n_state, n_ctx]
}
