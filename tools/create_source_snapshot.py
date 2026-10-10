"""Create a release source snapshot without keys, caches or Git history."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--archive-cache', type=Path, default=ROOT/'.pis-local/release-docs')
parser.add_argument('--llama-source', type=Path, default=os.environ.get('PIS_LLAMA_SOURCE'))
args = parser.parse_args()
output = args.output.resolve()
if output.exists():
    raise SystemExit('Snapshot exists; choose a new output path to preserve it.')
temporary = output.with_suffix(output.suffix+'.part')
if temporary.exists():
    raise SystemExit('Partial snapshot exists; preserve/inspect it before retrying.')
output.parent.mkdir(parents=True, exist_ok=True)

def git(path, *arguments):
    return subprocess.check_output(['git','-C',str(path),*arguments])

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        while block := stream.read(8*1024*1024): h.update(block)
    return h.hexdigest()

catalog = json.loads((ROOT/'docs/compliance/SOURCE_REPOSITORIES.json').read_text(encoding='utf8'))
external = json.loads((ROOT/'docs/compliance/EXTERNAL_SOURCE_ARCHIVES.json').read_text(encoding='utf8'))
inputs = {}
excluded = []

def add_checkout(directory, prefix):
    for rel in set(git(directory,'ls-files','-z','--cached','--others','--exclude-standard').decode('utf8').strip('\0').split('\0')):
        if not rel: continue
        path = directory/rel
        name = (Path(prefix)/rel).as_posix() if prefix else rel
        parts = Path(name).parts
        if any(x in parts for x in ('.git','.pis-local','.gradle','.cxx','build','verification')):
            continue
        if path == output or path == temporary: continue
        if path.suffix.lower() in ('.jks','.keystore','.apk','.aab','.aar','.so','.a','.log','.pem','.p12','.pfx','.gguf','.onnx') or path.name in ('local.properties','credentials.xml'):
            excluded.append(name); continue
        if not path.is_file(): continue  # Git links are handled below, not as a nested .git copy.
        if path.is_symlink() and not path.resolve().is_relative_to(directory.resolve()):
            raise SystemExit('Refusing external file symlink: '+name)
        if name in inputs and inputs[name] != path:
            raise SystemExit('Duplicate source member: '+name)
        inputs[name] = path

add_checkout(ROOT, '')
for entry in catalog['repositories']:
    if entry.get('external_checkout'):
        if args.llama_source is None: raise SystemExit('Set PIS_LLAMA_SOURCE or --llama-source to the pinned Git checkout.')
        directory = args.llama_source
    else:
        directory = ROOT/entry['path']
    if git(directory,'rev-parse','HEAD').decode().strip() != entry['commit']:
        raise SystemExit('Source revision differs from record: '+entry['name'])
    add_checkout(directory, entry['path'])

for entry in external:
    path = args.archive_cache/entry['file']
    if not path.is_file() or digest(path) != entry['sha256']:
        raise SystemExit('Source archive differs from record: '+entry['file'])
    inputs[entry['snapshot_path']] = path

file_records = []
for name, path in sorted(inputs.items()):
    record = {'file':name,'bytes':path.stat().st_size,'sha256':digest(path)}
    file_records.append(record)

metadata = {
    'product':"pis'input method",'version':'2.0.0','date':'2026-10-10',
    'release_apk_sha256':catalog['release_apk_sha256'],
    'upstream_commit':catalog['upstream_commit'],
    'source_state':'current worktree, including uncommitted pis modifications',
    'documentation_added_after_apk_build':True,
    'independent_snapshot_rebuild_performed':False,
    'excluded_project_signing_material_and_caches':True,
    'excluded_members':sorted(excluded),'files':file_records,
}
hashes=''.join(f"{entry['sha256']}  {entry['file']}\n" for entry in file_records)
main_patch=git(ROOT,'diff','--binary',catalog['upstream_commit'],'--','.',':(exclude)lib/fcitx5-chinese-addons/src/main/cpp/fcitx5-chinese-addons')
with zipfile.ZipFile(temporary,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=4,allowZip64=True) as archive:
    for name,path in sorted(inputs.items()):
        compression=zipfile.ZIP_STORED if name.startswith('vendor-source-archives/') else zipfile.ZIP_DEFLATED
        archive.write(path,name,compress_type=compression)
    archive.writestr('SOURCE_SNAPSHOT.json',json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
    archive.writestr('SOURCE_FILES.sha256',hashes)
    archive.writestr('UPSTREAM_DIFF.patch',main_patch)
with zipfile.ZipFile(temporary) as archive:
    names=archive.namelist()
    assert len(names)==len(set(names))==len(inputs)+3
    assert all(not Path(name).is_absolute() and '..' not in Path(name).parts for name in names)
    assert not any('/.git/' in '/'+name or '/.pis-local/' in '/'+name for name in names)
    assert not any(name.endswith(('.jks','.keystore','.p12','.pfx')) or Path(name).name=='credentials.xml' for name in names)
os.replace(temporary,output)
result={'snapshot':str(output),'bytes':output.stat().st_size,'sha256':digest(output),'source_files':len(inputs),'private_project_signing_files':'ABSENT'}
output.with_suffix('.manifest.json').write_text(json.dumps({**result,'metadata':{k:v for k,v in metadata.items() if k!='files'}},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
output.with_suffix('.sha256').write_text(f"{result['sha256']}  {output.name}\n",encoding='ascii')
print(json.dumps(result,ensure_ascii=True,indent=2))
