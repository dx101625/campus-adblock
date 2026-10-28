from pathlib import Path
import json, math, xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[1]
DATA=json.loads(Path(__file__).with_name('lucide-icons.json').read_text(encoding='utf-8'))
MAP={'house':'tab_home','sliders-horizontal':'tab_features','info':'tab_about','settings':'settings','chevron-right':'chevron_right','arrow-left':'back','shield-check':'shield','scan-qr-code':'scan','washing-machine':'device_washer','droplets':'device_water','smartphone':'device_other','wind':'device_hair','book-open':'reference','code-xml':'source_code','circle-check':'success','clock-3':'clock','check':'check','circle':'circle','plus':'plus','minus':'minus','layers':'layers','activity':'activity'}
def shape(e):
    a=e.attrib; tag=e.tag.rsplit('}',1)[-1]
    if tag=='path':return a['d']
    if tag=='line':return f"M{a['x1']} {a['y1']}L{a['x2']} {a['y2']}"
    if tag in ('polyline','polygon'):return 'M'+a['points']+('Z' if tag=='polygon' else '')
    if tag=='circle':
        x,y,r=map(float,(a['cx'],a['cy'],a['r']))
        return f'M{x-r} {y}a{r} {r} 0 1 0 {r*2} 0a{r} {r} 0 1 0 {-r*2} 0'
    if tag=='rect':
        x,y,w,h=map(float,(a.get('x',0),a.get('y',0),a['width'],a['height']));r=min(float(a.get('rx',0)),w/2,h/2)
        if not r:return f'M{x} {y}h{w}v{h}h{-w}Z'
        return f'M{x+r} {y}h{w-2*r}a{r} {r} 0 0 1 {r} {r}v{h-2*r}a{r} {r} 0 0 1 {-r} {r}h{2*r-w}a{r} {r} 0 0 1 {-r} {-r}v{2*r-h}a{r} {r} 0 0 1 {r} {-r}Z'
    raise ValueError(tag)
for name,target in MAP.items():
    svg=E.fromstring(DATA[name]['content'])
    paths='\n'.join('    <path android:fillColor="#00000000" android:strokeColor="#FF000000" android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="'+shape(e)+'" />' for e in svg)
    (ROOT/f'app/src/main/res/drawable/ic_{target}.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<!-- Lucide; ISC / Feather MIT. See assets/licenses/LUCIDE-LICENSE.txt. -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'+paths+'\n</vector>\n',encoding='utf-8')
print('Converted',len(MAP),'official Lucide SVGs into native vectors')
