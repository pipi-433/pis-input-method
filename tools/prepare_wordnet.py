"""Build an offline indexed English-definition asset from the pinned official WordNet 3.1 archive."""
import hashlib,json,re,sqlite3,tarfile,urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1];cache=root/'.pis-local/wordnet';cache.mkdir(parents=True,exist_ok=True)
url='https://wordnetcode.princeton.edu/wn3.1.dict.tar.gz';checksum='3f7d8be8ef6ecc7167d39b10d66954ec734280b5bdcd57f7d9eafe429d11c22a'
archive=cache/'wn3.1.dict.tar.gz'
if not archive.exists():urllib.request.urlretrieve(url,archive)
assert hashlib.sha256(archive.read_bytes()).hexdigest()==checksum,'Wrong WordNet archive'
out=root/'app/build/generated/pis-wordnet-assets';data=out/'pis-input/wordnet31.db';data.parent.mkdir(parents=True,exist_ok=True)
stamp=out/'wordnet-build.json';script_sha=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
if data.exists() and stamp.exists() and json.loads(stamp.read_text()).get('script_sha256')==script_sha:
 print('Verified WordNet asset already prepared');raise SystemExit()
temporary=data.with_suffix('.building.db')
# Use a fresh uniquely named build file; never overwrite a partially populated database.
import tempfile,os
handle,name=tempfile.mkstemp(prefix='wordnet-',suffix='.db',dir=data.parent);os.close(handle);temporary=Path(name)
db=sqlite3.connect(temporary);db.executescript('''
PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA page_size=4096;
CREATE TABLE synset(id TEXT PRIMARY KEY,pos TEXT NOT NULL,definition TEXT NOT NULL,examples TEXT NOT NULL,synonyms TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE sense(word TEXT NOT NULL,pos TEXT NOT NULL,rank INTEGER NOT NULL,synset TEXT NOT NULL,PRIMARY KEY(word,pos,rank)) WITHOUT ROWID;
CREATE TABLE form(word TEXT NOT NULL,pos TEXT NOT NULL,lemma TEXT NOT NULL,PRIMARY KEY(word,pos,lemma)) WITHOUT ROWID;
CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL) WITHOUT ROWID;
''')
license_lines=[]
with tarfile.open(archive) as tar:
 for category,pos in [('noun','n'),('verb','v'),('adj','a'),('adv','r')]:
  for raw in tar.extractfile('dict/data.'+category):
   line=raw.decode('ascii').rstrip()
   if line.startswith('  '):
    if category=='noun':license_lines.append(re.sub(r'^\s*\d+\s?', '',line))
    continue
   if not line:continue
   header,gloss=line.split('|',1);fields=header.split();count=int(fields[3],16)
   words=[re.sub(r'\((?:a|p|ip)\)$','',fields[4+2*i]).replace('_',' ') for i in range(count)]
   examples=re.findall(r'"([^"]*)"',gloss)
   definition=re.sub(r';?\s*"[^"]*"','',gloss).strip(' ;')
   db.execute('INSERT INTO synset VALUES(?,?,?,?,?)',(pos+':'+fields[0],pos,definition,'\n'.join(examples),'; '.join(words)))
  for raw in tar.extractfile('dict/index.'+category):
   if raw.startswith(b'  '):continue
   fields=raw.decode('ascii').split()
   if not fields:continue
   count=int(fields[2]);word=fields[0].replace('_',' ')
   for rank,offset in enumerate(fields[-count:],1):db.execute('INSERT INTO sense VALUES(?,?,?,?)',(word,pos,rank,pos+':'+offset))
  for raw in tar.extractfile('dict/'+category+'.exc'):
   fields=raw.decode('ascii').split()
   if fields:
    for lemma in fields[1:]:db.execute('INSERT OR IGNORE INTO form VALUES(?,?,?)',(fields[0].replace('_',' '),pos,lemma.replace('_',' ')))
db.executemany('INSERT INTO metadata VALUES(?,?)',[('version','3.1'),('source',url),('source_sha256',checksum),('schema','1')]);db.commit()
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
counts={t:db.execute('SELECT count(*) FROM '+t).fetchone()[0] for t in ['synset','sense','form']}
assert counts['synset']>100000 and counts['sense']>200000
assert db.execute("SELECT definition FROM sense JOIN synset ON sense.synset=synset.id WHERE word='hello'").fetchone()[0]=='an expression of greeting'
db.execute('VACUUM');db.close();temporary.replace(data)
notice=out/'pis-licenses/WordNet-3.1.txt';notice.parent.mkdir(parents=True,exist_ok=True);notice.write_text('\n'.join(license_lines)+'\n',encoding='utf8')
report={'version':'WordNet 3.1','source':url,'source_sha256':checksum,'script_sha256':script_sha,'bytes':data.stat().st_size,'sha256':hashlib.sha256(data.read_bytes()).hexdigest(),**counts}
stamp.write_text(json.dumps(report,indent=2),encoding='utf8');print(json.dumps(report,indent=2))
