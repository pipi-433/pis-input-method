#include <jni.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include "llama.h"
#include "chat.h"

namespace {
long long now_ms(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
struct Runtime {
    llama_model* model=nullptr;
    llama_context* context=nullptr;
    common_chat_templates_ptr templates;
    std::atomic<bool> canceled{false};
    std::atomic<long long> deadline{0};
    ~Runtime(){templates.reset();if(context)llama_free(context);if(model)llama_model_free(model);}
};
bool abort_decode(void* data){auto* r=static_cast<Runtime*>(data);return r->canceled.load() || (r->deadline.load()>0 && now_ms()>r->deadline.load());}
std::string bytes(JNIEnv* env,jbyteArray array){if(!array)throw std::runtime_error("缺少输入文本");std::string value(env->GetArrayLength(array),'\0');env->GetByteArrayRegion(array,0,value.size(),reinterpret_cast<jbyte*>(value.data()));return value;}
void fail(JNIEnv* env,const std::exception& error){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),error.what());}
std::once_flag backend;
}

extern "C" JNIEXPORT jlong JNICALL Java_org_fcitx_fcitx5_android_pis_translation_NativeTranslation_create(JNIEnv* env,jclass,jbyteArray path,jint size,jint threads){
    try {
        std::call_once(backend,[]{llama_backend_init();});
        auto r=std::make_unique<Runtime>();auto model_params=llama_model_default_params();model_params.n_gpu_layers=0;
        r->model=llama_model_load_from_file(bytes(env,path).c_str(),model_params);
        if(!r->model)throw std::runtime_error("离线模型加载失败，请重新导入正确的Q4模型");
        auto params=llama_context_default_params();params.n_ctx=size;params.n_batch=256;params.n_ubatch=128;params.n_threads=threads;params.n_threads_batch=threads;
        params.abort_callback=abort_decode;params.abort_callback_data=r.get();
        r->context=llama_init_from_model(r->model,params);
        if(!r->context)throw std::runtime_error("内存不足，无法初始化离线翻译");
        r->templates=common_chat_templates_init(r->model,"");
        return reinterpret_cast<jlong>(r.release());
    }catch(const std::exception& error){fail(env,error);return 0;}
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_fcitx_fcitx5_android_pis_translation_NativeTranslation_translate(JNIEnv* env,jclass,jlong handle,jbyteArray input,jbyteArray language){
    try {
        auto* r=reinterpret_cast<Runtime*>(handle);if(!r)throw std::runtime_error("翻译引擎未启动");
        // Cancellation belongs to the service job. It resets the flag before handing us this request.
        r->deadline.store(now_ms()+120000);
        llama_memory_clear(llama_get_memory(r->context),true);
        common_chat_msg msg;msg.role="user";msg.content="将以下文本翻译为"+bytes(env,language)+"，注意只需要输出翻译后的结果，不要额外解释：\n\n"+bytes(env,input);
        common_chat_templates_inputs options;options.messages.push_back(msg);options.use_jinja=true;options.add_generation_prompt=true;options.force_pure_content=true;
        std::string prompt=common_chat_templates_apply(r->templates.get(),options).prompt;
        const llama_vocab* vocab=llama_model_get_vocab(r->model);
        int count=-llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
        const int budget=768;
        if(count<=0 || count+budget>static_cast<int>(llama_n_ctx(r->context)))throw std::runtime_error("文字过长，请选中一个较短段落再翻译");
        std::vector<llama_token> tokens(count);if(llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),count,true,true)<0)throw std::runtime_error("无法解析文本");
        auto sampler=std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)>(llama_sampler_chain_init(llama_sampler_chain_default_params()),llama_sampler_free);
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),64,1.05f,0,0));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_k(20));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_p(.6f,1));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_temp(.7f));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_dist(42));
        auto batch=std::unique_ptr<llama_batch_ext,decltype(&llama_batch_ext_free)>(llama_batch_ext_init(r->context),llama_batch_ext_free);
        auto decode=[&](const llama_token* sequence,int n,int position){
            if(abort_decode(r))throw std::runtime_error(r->canceled.load()?"翻译已取消":"翻译超时，请缩短文本后重试");
            llama_batch_ext_clear(batch.get());
            for(int i=0;i<n;i++){int index=llama_batch_ext_add_token(batch.get(),0,sequence[i]);llama_pos pos=position+i;llama_batch_ext_set_pos(batch.get(),index,&pos);}
            llama_batch_ext_set_output_logits(batch.get(),n-1,true);
            int result=llama_process(r->context,LLAMA_PROCESS_TYPE_DECODE,batch.get());
            if(result!=0)throw std::runtime_error(abort_decode(r)?"翻译已取消或超时":"离线推理失败");
        };
        for(int start=0;start<count;start+=256)decode(tokens.data()+start,std::min(256,count-start),start);
        std::string output;bool complete=false;
        for(int i=0;i<budget;i++){
            if(abort_decode(r))throw std::runtime_error("翻译已取消或超时");
            llama_token next=llama_sampler_sample(sampler.get(),r->context,-1);
            if(llama_vocab_is_eog(vocab,next) || next==llama_vocab_eos(vocab)){complete=true;break;}
            char buffer[256];int n=llama_token_to_piece(vocab,next,buffer,sizeof(buffer),0,false);
            if(n<0){std::vector<char> larger(-n);n=llama_token_to_piece(vocab,next,larger.data(),larger.size(),0,false);if(n<0)throw std::runtime_error("无法读取译文");output.append(larger.data(),n);}else output.append(buffer,n);
            decode(&next,1,count+i);
        }
        if(!complete)throw std::runtime_error("译文过长，未完成；请缩短原文后重试");
        if(output.empty())throw std::runtime_error("模型没有返回译文");
        auto result=env->NewByteArray(output.size());if(!result)return nullptr;env->SetByteArrayRegion(result,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));return result;
    }catch(const std::exception& error){fail(env,error);return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_org_fcitx_fcitx5_android_pis_translation_NativeTranslation_cancel(JNIEnv*,jclass,jlong handle){if(handle)reinterpret_cast<Runtime*>(handle)->canceled.store(true);}
extern "C" JNIEXPORT void JNICALL Java_org_fcitx_fcitx5_android_pis_translation_NativeTranslation_reset(JNIEnv*,jclass,jlong handle){if(handle)reinterpret_cast<Runtime*>(handle)->canceled.store(false);}
extern "C" JNIEXPORT void JNICALL Java_org_fcitx_fcitx5_android_pis_translation_NativeTranslation_close(JNIEnv*,jclass,jlong handle){delete reinterpret_cast<Runtime*>(handle);}

