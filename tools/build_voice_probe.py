"""Build the independent Android instrumentation probe without app dependencies."""
from pathlib import Path
import os,subprocess,shutil,zipfile
root=Path(__file__).resolve().parents[1];src=root/'verification/voice-probe';out=root/'verification/evidence/voice-probe';out.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME']);bt=sdk/'build-tools/36.1.0';jar=sdk/'platforms/android-36/android.jar'
def run(*args):subprocess.run(list(map(str,args)),check=True)
classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True);assets=out/'assets';assets.mkdir(exist_ok=True)
for name in ['0.wav','1.wav']:shutil.copyfile(root/'.pis-local/voice/models'/name,assets/name)
run(jdk/'bin/javac.exe','--release','11','-encoding','UTF-8','-cp',jar,'-d',classes,*src.glob('*.java'))
run(bt/'d8.bat','--lib',jar,'--min-api','26','--output',dex,*classes.rglob('*.class'))
raw=out/'unsigned.apk';run(bt/'aapt2.exe','link','-I',jar,'--manifest',src/'AndroidManifest.xml','-A',assets,'-o',raw)
with zipfile.ZipFile(raw,'a') as archive:archive.write(dex/'classes.dex','classes.dex')
apk=out/'voice-probe.apk';run(bt/'zipalign.exe','-f','-P','16','4',raw,apk)
run(jdk/'bin/java.exe','-jar',bt/'lib/apksigner.jar','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android',apk)
print(apk)
