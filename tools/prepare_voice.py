"""Fetch official sherpa runtime/model assets and stage only verified speech components."""
import hashlib,json,os,shutil,tarfile,urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1];cache=root/'.pis-local/voice';cache.mkdir(parents=True,exist_ok=True)
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  while block:=f.read(8*1024*1024):h.update(block)
 return h.hexdigest()
aar=cache/'sherpa-onnx-1.13.8.aar'
if not aar.exists():urllib.request.urlretrieve('https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/'+aar.name,aar)
assert sha(aar)=='633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96','Wrong official sherpa AAR'
runtime=root/'app/build/generated/pis-voice-runtime';runtime.mkdir(parents=True,exist_ok=True)
shutil.copyfile(aar,runtime/aar.name)
models=cache/'models';models.mkdir(exist_ok=True)
expected={
 'encoder-epoch-99-avg-1.int8.onnx':'909090f5df6874ea7be9aa76b57d5bb5b40d2ae1f7342cf7c3f6f0e14d0f8535',
 'decoder-epoch-99-avg-1.onnx':'89be509a83175261695bdef5fd1c7b9ab1129a663d1284e7ba9f8507b21e0906',
 'joiner-epoch-99-avg-1.onnx':'bcbcf0cc1b449bc32b7777084ab5a65d1199b822e2aae149d3a43adc9f4b5775',
 'tokens.txt':'a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3',
 'punctuation/model.int8.onnx':'65a3fb9f5ad7bfb96bf69e0dc4481df97f6ee60513c1d94ce981ba6effd524b1'}
def unpack(archive,url,wanted):
 if not archive.exists():urllib.request.urlretrieve(url,archive)
 pending=set(wanted)
 with tarfile.open(archive) as tar:
  for entry in tar:
   for name in tuple(pending):
    if entry.isfile() and Path(entry.name).name==Path(name).name:
     out=models/name;out.parent.mkdir(parents=True,exist_ok=True)
     with tar.extractfile(entry) as f,out.open('wb') as o:shutil.copyfileobj(f,o)
     pending.remove(name)
   if not pending:break
 assert not pending,pending
missing=[n for n in expected if not (models/n).exists()]
speech=[n for n in missing if not n.startswith('punctuation/')]
if speech:unpack(cache/'asr-models.tar.bz2','https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16.tar.bz2',speech)
if 'punctuation/model.int8.onnx' in missing:unpack(cache/'punctuation.tar.bz2','https://github.com/k2-fsa/sherpa-onnx/releases/download/punctuation-models/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8.tar.bz2',['punctuation/model.int8.onnx'])
assets=root/'app/build/generated/pis-voice-assets/voice';assets.mkdir(parents=True,exist_ok=True)
for name,checksum in expected.items():
 source=models/name;assert sha(source)==checksum,('Wrong public speech model',name)
 target=assets/name;target.parent.mkdir(parents=True,exist_ok=True)
 if target.exists():assert sha(target)==checksum
 else:
  try:os.link(source,target)
  except OSError:shutil.copyfile(source,target)
print('Official speech runtime and five model files verified')
