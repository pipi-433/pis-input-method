"""Convert clipboard, microphone and translation icon paths into Android vectors."""
import argparse,hashlib,json,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];raw=a.source.read_bytes();icons=json.loads(raw);ns='http://schemas.android.com/apk/res/android';ET.register_namespace('android',ns)
out=root/'app/src/main/res/drawable';records=[]
for name in ['clipboard','mic','translate']:
 icon=icons[name];vector=ET.Element('vector',{f'{{{ns}}}width':'24dp',f'{{{ns}}}height':'24dp',f'{{{ns}}}viewportWidth':'24',f'{{{ns}}}viewportHeight':'24'})
 for layer in icon['layers']:
  attrs={f'{{{ns}}}pathData':layer['path']}
  if layer['draw']=='stroke':attrs.update({f'{{{ns}}}fillColor':'#00000000',f'{{{ns}}}strokeColor':'#FF000000',f'{{{ns}}}strokeWidth':str(icon['strokeWidth']),f'{{{ns}}}strokeLineCap':'round',f'{{{ns}}}strokeLineJoin':'round'})
  else:attrs[f'{{{ns}}}fillColor']='#FF000000'
  if layer.get('evenOdd'):attrs[f'{{{ns}}}fillType']='evenOdd'
  ET.SubElement(vector,'path',attrs)
 path=out/f'pis_legacy_{name}.xml';ET.indent(vector);ET.ElementTree(vector).write(path,encoding='utf-8',xml_declaration=True)
 records.append({'icon':name,'file':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
(root/'app/src/main/assets/pis-licenses/IMPORTED_RESOURCES.json').write_text(json.dumps({'icons':records},ensure_ascii=False,indent=2),encoding='utf-8')
print('Generated clipboard, mic and translate vectors')
