"""Generate the five self-contained README SVG badges. No network or packages."""
from pathlib import Path
from html import escape
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/images/badges'
OUT.mkdir(parents=True, exist_ok=True)
ANDROID = 'M18.4395 5.5586c-.675 1.1664-1.352 2.3318-2.0274 3.498-.0366-.0155-.0742-.0286-.1113-.043-1.8249-.6957-3.484-.8-4.42-.787-1.8551.0185-3.3544.4643-4.2597.8203-.084-.1494-1.7526-3.021-2.0215-3.4864a1.1451 1.1451 0 0 0-.1406-.1914c-.3312-.364-.9054-.4859-1.379-.203-.475.282-.7136.9361-.3886 1.5019 1.9466 3.3696-.0966-.2158 1.9473 3.3593.0172.031-.4946.2642-1.3926 1.0177C2.8987 12.176.452 14.772 0 18.9902h24c-.119-1.1108-.3686-2.099-.7461-3.0683-.7438-1.9118-1.8435-3.2928-2.7402-4.1836a12.1048 12.1048 0 0 0-2.1309-1.6875c.6594-1.122 1.312-2.2559 1.9649-3.3848.2077-.3615.1886-.7956-.0079-1.1191a1.1001 1.1001 0 0 0-.8515-.5332c-.5225-.0536-.9392.3128-1.0488.5449zm-.0391 8.461c.3944.5926.324 1.3306-.1563 1.6503-.4799.3197-1.188.0985-1.582-.4941-.3944-.5927-.324-1.3307.1563-1.6504.4727-.315 1.1812-.1086 1.582.4941zM7.207 13.5273c.4803.3197.5506 1.0577.1563 1.6504-.394.5926-1.1038.8138-1.584.4941-.48-.3197-.5503-1.0577-.1563-1.6504.4008-.6021 1.1087-.8106 1.584-.4941z'
KOTLIN = 'M24 24H0V0h24L12 12Z'

def lucide(name):
    # Existing pinned Lucide paths; ISC / Feather MIT notices ship with the app.
    tree = ET.parse(ROOT / f'app/src/main/res/drawable/ic_{name}.xml')
    key = '{http://schemas.android.com/apk/res/android}pathData'
    paths = ''.join(f'<path d="{escape(n.attrib[key])}"/>' for n in tree.getroot())
    return '<g fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">' + paths + '</g>'

ICONS = {
    'version': '<g fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M11 3H5a2 2 0 0 0-2 2v6l9 9a2 2 0 0 0 3 0l5-5a2 2 0 0 0 0-3Z"/><circle cx="7.5" cy="7.5" r="1" fill="currentColor" stroke="none"/></g>',
    'android': f'<path d="{ANDROID}" fill="currentColor"/>',
    'license': lucide('shield'),
    'compose': f'<path d="{KOTLIN}" fill="currentColor"/>',
    'miuix': lucide('layers'),
}
BADGES = [
    ('version', 'v1.6.1', 91, '#EFF3FF', '#DCE4FF', '#3B55D9', 'Version 1.6.1'),
    ('android', 'Android 7.0+', 126, '#EEF8F2', '#D8EEDF', '#218454', 'Android 7.0 and above'),
    ('license', 'MIT', 72, '#F3F5F8', '#E1E6EE', '#536173', 'MIT License'),
    ('compose', 'Kotlin · Compose', 157, '#F6F0FF', '#E9DEFA', '#7952BC', 'Kotlin and Jetpack Compose'),
    ('miuix', 'Miuix · Glass UI', 151, '#EFF4FF', '#DDE6FB', '#4669C5', 'Miuix with Backdrop glass interface'),
]
for name, text, width, background, border, color, title in BADGES:
    # Icons are inline paths: GitHub needs no third-party image request.
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="30" viewBox="0 0 {width} 30" role="img" aria-label="{escape(title)}">
<title>{escape(title)}</title>
<rect x=".5" y=".5" width="{width-1}" height="29" rx="14.5" fill="{background}" stroke="{border}"/>
<g color="{color}" transform="translate(11 7) scale(.666667)">{ICONS[name]}</g>
<text x="35" y="19.2" fill="{color}" font-family="-apple-system,BlinkMacSystemFont,Segoe UI,Arial,sans-serif" font-size="11.5" font-weight="600">{escape(text)}</text>
</svg>
'''
    (OUT / f'{name}.svg').write_text(svg, encoding='utf-8')
print('Generated five 30px rounded badges with inline icons.')
