"""Generate the selected native launcher mark from app/artwork/launcher.svg.

Requires Pillow only for raster exports; it is not a build/runtime dependency.
The foreground geometry is preserved in Android VectorDrawable coordinates.
"""
from pathlib import Path
import re,sys,xml.etree.ElementTree as E
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]
SVG=ROOT/'app/artwork/launcher.svg'
S=16
A='http://schemas.android.com/apk/res/android'
num=r'-?\d+(?:\.\d+)?'
def color(value):return {'white':'#FFFFFF','none':'#00000000'}.get(value,value)
def elements():
    root=E.parse(SVG).getroot()
    def walk(node,inherited):
        attrs=dict(inherited);attrs.update(node.attrib)
        tag=node.tag.rsplit('}',1)[-1]
        if tag in ('svg','g'):
            for child in node:yield from walk(child,attrs)
        else:yield tag,attrs
    return list(walk(root,{}))
def rect_path(a):
    x,y,w,h,r=[float(a.get(k,0)) for k in ('x','y','width','height','rx')]
    return f'M{x+r} {y}h{w-2*r}a{r} {r} 0 0 1 {r} {r}v{h-2*r}a{r} {r} 0 0 1 {-r} {r}h{2*r-w}a{r} {r} 0 0 1 {-r} {-r}v{2*r-h}a{r} {r} 0 0 1 {r} {-r}Z'
def foreground():
    entries=elements()
    assert entries[0][0]=='rect' and entries[0][1]['fill']=='#3B5BFF'
    return entries[1:]
def curve(a,b,c,d):
    return [tuple(((1-t)**3*a[i]+3*(1-t)**2*t*b[i]+3*(1-t)*t*t*c[i]+t**3*d[i])*S for i in (0,1)) for t in [j/128 for j in range(129)]]
def outline(data):
    tokens=re.findall(r'[A-Za-z]|'+num,data);i=0;points=[];current=(0.,0.)
    while i<len(tokens):
        command=tokens[i];i+=1
        if command=='M':
            current=tuple(map(float,tokens[i:i+2]));i+=2;points.append(tuple(v*S for v in current))
        elif command=='C':
            values=list(map(float,tokens[i:i+6]));i+=6
            points+=curve(current,tuple(values[:2]),tuple(values[2:4]),tuple(values[4:6]));current=tuple(values[4:6])
        elif command=='V':
            current=(current[0],float(tokens[i]));i+=1;points.append(tuple(v*S for v in current))
        elif command=='Z':pass
        else:raise ValueError('Unsupported shield path command: '+command)
    return points
def icon_image(round_icon=False):
    im=Image.new('RGBA',(108*S,108*S),'#3B5BFF');d=ImageDraw.Draw(im)
    for tag,a in foreground():
        if tag=='rect':
            box=[round(float(a[k])*S) for k in ('x','y')]
            box += [round((float(a['x'])+float(a['width']))*S),round((float(a['y'])+float(a['height']))*S)]
            d.rounded_rectangle(box,radius=float(a['rx'])*S,fill=a['fill'])
        elif tag=='path' and 'stroke' not in a:d.polygon(outline(a['d']),fill=a['fill'])
        elif tag=='path':
            coords=list(map(float,re.findall(num,a['d'])));assert len(coords)==4
            p1,p2=coords[:2],coords[2:];width=float(a['stroke-width']);ink=a['stroke']
            d.line([tuple(v*S for v in p1),tuple(v*S for v in p2)],fill=ink,width=round(width*S))
            for x,y in (p1,p2):
                r=width*S/2;d.ellipse((x*S-r,y*S-r,x*S+r,y*S+r),fill=ink)
        else:raise ValueError(tag)
    im=im.crop((18*S,18*S,90*S,90*S))
    mask=Image.new('L',im.size);md=ImageDraw.Draw(mask)
    if round_icon:md.ellipse((0,0,im.width-1,im.height-1),fill=255)
    else:md.rounded_rectangle((0,0,im.width-1,im.height-1),radius=18*S,fill=255)
    im.putalpha(mask);return im
def vector():
    E.register_namespace('android',A)
    node=E.Element('vector',{f'{A_}width':'108dp',f'{A_}height':'108dp',f'{A_}viewportWidth':'108',f'{A_}viewportHeight':'108'})
    for tag,a in foreground():
        attrs={'fillColor':color(a.get('fill','none') if 'stroke' not in a else 'none'),'pathData':rect_path(a) if tag=='rect' else a['d']}
        if 'stroke' in a:attrs.update(strokeColor=color(a['stroke']),strokeWidth=a['stroke-width'],strokeLineCap='round',strokeLineJoin='round')
        E.SubElement(node,'path',{A_+k:v for k,v in attrs.items()})
    E.indent(node,space='    ')
    E.ElementTree(node).write(ROOT/'app/src/main/res/drawable/ic_launcher_foreground.xml',encoding='utf-8',xml_declaration=True)
A_='{'+A+'}'
if __name__=='__main__':
    preview=ROOT/'app/artwork/launcher-preview.png'
    icon_image().resize((1024,1024),Image.Resampling.LANCZOS).save(preview)
    if '--preview' not in sys.argv:
        vector()
        for dpi,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
            for rounded in (False,True):
                icon_image(rounded).resize((px,px),Image.Resampling.LANCZOS).save(ROOT/('app/src/main/res/mipmap-'+dpi+'/ic_launcher'+('_round' if rounded else '')+'.png'))
    print('Selected mark exported:',preview)
