// ZeroCloudAssist: vectores de multilingual-e5-small para buscar en el manual (plan 02, paso 2.3).
// Fichero nuevo, no viene del ejemplo. Sigue examples/embedding/embedding.cpp de b10941, con sus
// propias variables globales: el modelo de chat de ai_chat.cpp y este conviven cargados.
#include <jni.h>
#include <algorithm>
#include <string>
#include <unistd.h>
#include <vector>

#include "common.h"
#include "llama.h"
#include "logging.h"  // después de llama.h: usa ggml_log_level sin incluir ggml.h

// e5-small admite 512 posiciones; una pregunta del técnico no pasa de unas decenas de tokens.
constexpr int EMBD_CONTEXT_SIZE = 512;

static llama_model   * g_embd_model;
static llama_context * g_embd_context;

static void free_embedder() {
    llama_free(g_embd_context);
    g_embd_context = nullptr;
    llama_model_free(g_embd_model);
    g_embd_model = nullptr;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_arm_aichat_Embedder_nativeLoad(JNIEnv *env, jobject /*unused*/, jstring jmodel_path) {
    free_embedder();

    llama_model_params model_params = llama_model_default_params();
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;  // el fichero queda respaldado por mmap

    const auto *model_path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGi("%s: Loading embedding model from %s", __func__, model_path);
    g_embd_model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!g_embd_model) {
        LOGe("%s: llama_model_load_from_file() failed", __func__);
        return JNI_FALSE;
    }

    // Mismos hilos que el chat (ai_chat.cpp): min(6, núcleos - 2).
    const int n_threads = std::max(2, std::min(6, (int) sysconf(_SC_NPROCESSORS_ONLN) - 2));
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true;
    ctx_params.pooling_type = LLAMA_POOLING_TYPE_MEAN;
    ctx_params.n_ctx = EMBD_CONTEXT_SIZE;
    ctx_params.n_batch = EMBD_CONTEXT_SIZE;   // e5 no es causal: el lote entero de una vez
    ctx_params.n_ubatch = EMBD_CONTEXT_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    g_embd_context = llama_init_from_model(g_embd_model, ctx_params);
    if (!g_embd_context) {
        LOGe("%s: llama_init_from_model() failed", __func__);
        free_embedder();
        return JNI_FALSE;
    }
    LOGi("%s: Embedding model ready, %d threads, pooling %d", __func__, n_threads,
         (int) llama_pooling_type(g_embd_context));
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_arm_aichat_Embedder_nativeEmbed(JNIEnv *env, jobject /*unused*/, jstring jtext) {
    if (!g_embd_context) return nullptr;

    const auto *text = env->GetStringUTFChars(jtext, nullptr);
    // Con tokens especiales: e5 (XLM-R) espera <s> … </s>, igual que llama-server en el PC.
    auto tokens = common_tokenize(llama_model_get_vocab(g_embd_model), text, true, true);
    env->ReleaseStringUTFChars(jtext, text);
    if (tokens.empty()) return nullptr;
    if ((int) tokens.size() > EMBD_CONTEXT_SIZE) {
        LOGw("%s: %d tokens, truncating to %d", __func__, (int) tokens.size(), EMBD_CONTEXT_SIZE);
        tokens.resize(EMBD_CONTEXT_SIZE);
    }

    llama_batch batch = llama_batch_init((int) tokens.size(), 0, 1);
    for (int i = 0; i < (int) tokens.size(); i++) {
        common_batch_add(batch, tokens[i], i, {0}, true);
    }
    const int result = llama_encode(g_embd_context, batch);  // e5 es solo codificador
    llama_batch_free(batch);
    if (result != 0) {
        LOGe("%s: llama_encode() failed w/ %d", __func__, result);
        return nullptr;
    }

    const float *embd = llama_get_embeddings_seq(g_embd_context, 0);
    if (!embd) {
        LOGe("%s: llama_get_embeddings_seq() returned null", __func__);
        return nullptr;
    }
    const int n_embd = llama_model_n_embd_out(g_embd_model);
    std::vector<float> normalized(n_embd);
    common_embd_normalize(embd, normalized.data(), n_embd, 2);  // 2 = norma L2

    jfloatArray out = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(out, 0, n_embd, normalized.data());
    return out;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_Embedder_nativeUnload(JNIEnv * /*unused*/, jobject /*unused*/) {
    free_embedder();
}
