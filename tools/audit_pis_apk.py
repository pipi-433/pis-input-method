"""Verify the new product's identity, bundled model, JNI architecture and signing."""
import argparse,hashlib,json,subprocess,zipfile,struct,os,re
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--apk',type=Path,required=True)
parser.add_argument('--release',action='store_true',help='Require production identity, no debug components or experimental WordNet')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1];sdk=Path(os.environ['ANDROID_HOME']);java=Path(os.environ['JAVA_HOME'])/'bin/java.exe'
def run(*command):
    return subprocess.check_output(list(map(str,command)),text=True,encoding='utf8',errors='replace')
def digest(stream):
    h=hashlib.sha256()
    while block:=stream.read(8*1024*1024):h.update(block)
    return h.hexdigest()
with zipfile.ZipFile(args.apk) as archive:
    names=archive.namelist()
    assert not any(n.startswith(('assets/ui/','assets/spec/')) or n.endswith('/libzcengine.so') for n in names),'Excluded UI/native components included'
    for n in names:
        if n.endswith('.dex'):
            dex=archive.read(n)
            assert b'Lcn/zcubed/' not in dex,'Excluded application classes included'
            if args.release:
                for debug_type in (b'PisKeyboardTestActivity;',b'PisMigrationProbe;',b'PisWordFrequencyProbe;',b'PisCandidateProbe;'):
                    assert debug_type not in dex,'Debug class included: '+debug_type.decode()
    entry='assets/translation/Hy-MT2-1.8B-Q4_K_M.gguf';info=archive.getinfo(entry)
    assert info.file_size==1133080448 and info.compress_type==zipfile.ZIP_STORED,'Model must be complete and uncompressed'
    with archive.open(entry) as stream: model_sha=digest(stream)
    assert model_sha=='dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699','Model hash mismatch'
    libs=[n for n in names if n.startswith('lib/') and n.endswith('.so')]
    alignment=[]
    for n in libs:
        elf=archive.read(n);assert elf[:5]==b'\x7fELF\x02',n
        phoff=struct.unpack_from('<Q',elf,32)[0];size,count=struct.unpack_from('<HH',elf,54)
        loads=[struct.unpack_from('<Q',elf,phoff+i*size+48)[0] for i in range(count) if struct.unpack_from('<I',elf,phoff+i*size)[0]==1]
        assert loads and min(loads)>=16384,(n,loads)
        alignment.append({'file':n,'minimum_load_alignment':min(loads)})
    assert any(n.endswith('/libpis_translation.so') for n in libs)
    voice_hashes={
        'encoder-epoch-99-avg-1.int8.onnx':'909090f5df6874ea7be9aa76b57d5bb5b40d2ae1f7342cf7c3f6f0e14d0f8535',
        'decoder-epoch-99-avg-1.onnx':'89be509a83175261695bdef5fd1c7b9ab1129a663d1284e7ba9f8507b21e0906',
        'joiner-epoch-99-avg-1.onnx':'bcbcf0cc1b449bc32b7777084ab5a65d1199b822e2aae149d3a43adc9f4b5775',
        'tokens.txt':'a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3',
        'punctuation/model.int8.onnx':'65a3fb9f5ad7bfb96bf69e0dc4481df97f6ee60513c1d94ce981ba6effd524b1'}
    has_voice='assets/voice/tokens.txt' in names
    has_gloss='assets/pis-input/gloss.db' in names
    if args.release:
        for notice in ('SOURCES.md','Fcitx5-Android-LGPL-2.1.txt','GPL-2.0-or-later.txt','Hy-MT2-Apache-2.0.txt','llama-cpp-MIT.txt','onnxruntime-MIT.txt','APACHE-2.0.txt','IMPORTED_RESOURCES.json'):
            assert 'assets/pis-licenses/'+notice in names,'Missing notice: '+notice
    if has_gloss:
        with archive.open('assets/pis-input/gloss.db') as stream: assert digest(stream)=='58778b70d32cdf1539f2e751b7ff92b8f950784b60c524a6ef9a3adfa898ab62'
    has_wordnet='assets/pis-input/wordnet31.db' in names
    if args.release: assert not has_wordnet,'Unused experimental WordNet database included'
    if has_wordnet:
        with archive.open('assets/pis-input/wordnet31.db') as stream: assert digest(stream)=='0e00d480d330af22638e89bb752d62addb6fda6c2d7a8738ae847dd7480194ae'
        assert 'assets/pis-licenses/WordNet-3.1.txt' in names
    if has_voice:
        for name,expected in voice_hashes.items():
            with archive.open('assets/voice/'+name) as stream: assert digest(stream)==expected,name
        assert any(n.endswith('/libsherpa-onnx-jni.so') for n in libs)
        assert any(n.endswith('/libonnxruntime.so') for n in libs)
        assert 'assets/pis-licenses/pypinyin-MIT.txt' in names
bt=sdk/'build-tools/36.1.0'
signature=run(java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',args.apk)
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
if args.release: assert 'CN=Android Debug' not in signature,'Debug signing certificate used'
run(bt/'zipalign.exe','-c','-P','16','4',args.apk)
badging=run(bt/'aapt.exe','dump','badging',args.apk)
package='io.pis.inputmethod' if args.release else 'io.pis.inputmethod.debug'
assert f"name='{package}'" in badging and "application-label:'pis'input method'" in badging,badging[:500]
manifest=run(bt/'aapt.exe','dump','xmltree',args.apk,'AndroidManifest.xml')
if args.release:
    assert 'application-debuggable' not in badging,'Debuggable release'
    assert 'PisKeyboardTestActivity' not in manifest,'Debug activity in manifest'
    assert has_voice and has_gloss,'Required voice/gloss assets missing'
    assert all(n.startswith('lib/arm64-v8a/') for n in libs),'Non-ARM64 library in phone release'
assert 'android.permission.INTERNET' not in badging
if has_voice: assert 'android.permission.RECORD_AUDIO' in badging
with args.apk.open('rb') as stream: apk_sha=digest(stream)
report={'apk':str(args.apk.resolve()),'bytes':args.apk.stat().st_size,'sha256':apk_sha,'model_sha256':model_sha,'elf_alignment':alignment,'signature':'v2 PASS','zip_alignment':'16KB PASS','excluded_application_components':'ABSENT','app_label':"pis'input method",'internet_permission':'ABSENT'}
report['lexical_resource']='verified gloss.db PASS' if has_gloss else 'not included'
report['english_definition_resource']='verified WordNet 3.1 and original license PASS' if has_wordnet else 'not included'
report['voice_models_sha256']='five verified public assets PASS' if has_voice else 'not included'
report['package']=package
report['build_type']='release' if args.release else 'debug'
report['certificate_sha256']=next(line.split(': ',1)[1] for line in signature.splitlines() if 'certificate SHA-256 digest:' in line)
report['version_name']=re.search(r"versionName='([^']+)'",badging).group(1)
report['version_code']=int(re.search(r"versionCode='(\d+)'",badging).group(1))
report['abis']=sorted({n.split('/')[1] for n in libs})
report['debuggable']='application-debuggable' in badging
report['debug_components']='ABSENT' if args.release else 'debug source set permitted'
out=root/'verification/evidence';out.mkdir(parents=True,exist_ok=True)
(out/'phone-apk-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in report.items() if k!='elf_alignment'},ensure_ascii=False,indent=2))
