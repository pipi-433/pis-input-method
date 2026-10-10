"""Apply the recorded pis user-frequency extension to the fixed upstream submodule."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1];repo=root/'lib/fcitx5-chinese-addons/src/main/cpp/fcitx5-chinese-addons';patch=root/'patches/pis-word-frequency.patch'
def check(*args):return subprocess.run(['git','-C',str(repo),'apply',*args,str(patch)],capture_output=True).returncode==0
if check('--reverse','--check'):print('pis pinyin extension already applied')
elif check('--check'):subprocess.run(['git','-C',str(repo),'apply',str(patch)],check=True);print('Applied pis pinyin extension')
else:raise SystemExit('Pinyin sources differ from the recorded patch; inspect the local changes before building')
