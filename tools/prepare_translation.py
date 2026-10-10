"""Pinned llama.cpp and Hy-MT2 translation backend."""
import argparse,hashlib,os,subprocess,shutil,urllib.request,sys
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('--abi',choices=['arm64-v8a','x86_64'],required=True);args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
commit='50a6c5cf7c09ea5ea9c1937e4895ce88331c2869'
source=Path(os.environ.get('PIS_LLAMA_SOURCE',str(root/'.pis-local/llama.cpp')))
if not source.exists():
    source.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run(['git','clone','--depth','1','--branch','b11481','https://github.com/ggml-org/llama.cpp.git',str(source)],check=True)
assert subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()==commit,'Wrong llama.cpp revision'
sdk=Path(os.environ['ANDROID_HOME']);ndk=sdk/'ndk/28.0.13004108';build=root/'build/translation-native'/args.abi
subprocess.run(['cmake','-S',str(root/'native-translation'),'-B',str(build),'-G','Ninja','-DCMAKE_BUILD_TYPE=Release','-DLLAMA_SOURCE_DIR='+str(source),'-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),'-DANDROID_ABI='+args.abi,'-DANDROID_PLATFORM=android-26','-DANDROID_STL=c++_static','-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON'],check=True)
subprocess.run(['cmake','--build',str(build),'--target','pis_translation','-j','8'],check=True)
out=root/'app/build/generated/pis-translation-jni'/args.abi;out.mkdir(parents=True,exist_ok=True)
host='windows-x86_64' if os.name=='nt' else 'darwin-x86_64' if sys.platform=='darwin' else 'linux-x86_64'
strip=ndk/'toolchains/llvm/prebuilt'/host/'bin'/('llvm-strip.exe' if os.name=='nt' else 'llvm-strip')
subprocess.run([str(strip),'--strip-unneeded','-o',str(out/'libpis_translation.so'),str(build/'libpis_translation.so')],check=True)
name='Hy-MT2-1.8B-Q4_K_M.gguf';expected='dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699'
target=root/'app/build/generated/pis-translation-assets/translation'/name
if not target.exists():
    model=Path(os.environ.get('PIS_MODEL_FILE',str(root/'.pis-local/models'/name)))
    if not model.exists():
        model.parent.mkdir(parents=True,exist_ok=True)
        temporary=model.with_suffix('.part')
        urllib.request.urlretrieve('https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/a0c709d9fac510f2c807aa3af52872340dc37a4a/'+name,temporary)
        model=temporary
    digest=hashlib.sha256()
    with model.open('rb') as stream:
        while block:=stream.read(8*1024*1024):digest.update(block)
    assert model.stat().st_size==1133080448 and digest.hexdigest()==expected,'Wrong translation model'
    target.parent.mkdir(parents=True,exist_ok=True)
    try:os.link(model,target)
    except OSError:shutil.copyfile(model,target)
print('Verified translation backend and model ready:',args.abi)
