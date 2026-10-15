import sys, xml.etree.ElementTree as ET, subprocess, os

NS = '{http://schemas.android.com/apk/res/android}'
src = sys.argv[1]
name = os.path.splitext(os.path.basename(src))[0]

tree = ET.parse(src)
root = tree.getroot()
vw = float(root.get(NS+'viewportWidth')); vh = float(root.get(NS+'viewportHeight'))

def _vis(color):
    return color and not (color.startswith('#00000000') or color == 'transparent')

def path_svg(p, mono=None):
    d = p.get(NS+'pathData')
    fill = p.get(NS+'fillColor') or 'none'
    stroke = p.get(NS+'strokeColor') or 'none'
    if mono:
        fill = mono if _vis(p.get(NS+'fillColor')) else 'none'
        stroke = mono if p.get(NS+'strokeColor') else 'none'
    sw = p.get(NS+'strokeWidth') or '1'
    cap = p.get(NS+'strokeLineCap') or 'butt'
    join = p.get(NS+'strokeLineJoin') or 'miter'
    ft = p.get(NS+'fillType')
    rule = ' fill-rule="evenodd"' if ft == 'evenOdd' else ''
    return (f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" '
            f'stroke-linecap="{cap}" stroke-linejoin="{join}"{rule}/>')

inner = ''.join(path_svg(p) for p in root.iter('path'))
plain = ''.join(path_svg(p, '#17365D') for p in root.iter('path'))

svg_big = (f'<svg xmlns="http://www.w3.org/2000/svg" width="480" height="480" '
           f'viewBox="0 0 {vw} {vh}" preserveAspectRatio="xMidYMid meet">'
           f'<rect width="100%" height="100%" fill="white"/>{inner}</svg>')

svg_badge = (f'<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" '
             f'viewBox="0 0 40 40">'
             f'<rect width="40" height="40" fill="#F5F7FA"/>'
             f'<circle cx="20" cy="20" r="20" fill="#D7E3F4"/>'
             f'<g transform="translate(8,8) scale({24/vw},{24/vh})">{plain}</g></svg>')

out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out')
os.makedirs(out_dir, exist_ok=True)
open(os.path.join(out_dir, f'{name}_big.svg'), 'w').write(svg_big)
open(os.path.join(out_dir, f'{name}_badge.svg'), 'w').write(svg_badge)

render_dir = os.path.dirname(os.path.abspath(__file__))
for tag in ('big', 'badge'):
    s = os.path.join(out_dir, f'{name}_{tag}.svg')
    o = os.path.join(out_dir, f'{name}_{tag}.png')
    r = subprocess.run(['node', 'to_png.js', s, o],
                       cwd=render_dir, capture_output=True, text=True)
    print(tag, r.stdout.strip() or r.stderr.strip())
