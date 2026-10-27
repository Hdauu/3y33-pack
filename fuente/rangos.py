"""Tags de rango en pixel art (estilo nogard.dev) para el pack Java.
Cada rango es UN solo caracter del font default (U+E300...), asi se pone directo en ModernTags/TAB/chat.
Dibuja con Press Start 2P: borde y texto en degradado del color del rango sobre fondo oscuro."""
import io, json, zipfile, hashlib, os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
ZIP = os.path.join(BASE, '..', 'java', '3y33-pack.zip')
FUENTE = os.path.join(BASE, 'fuentes', 'PressStart2P.ttf')

RANGOS = [  # id, texto, color 1, color 2
    ('plata', 'PLATA', '#9E9E9E', '#E8E8E8'),
    ('sheriff', 'SHERIFF', '#FF8C00', '#FFD27F'),
    ('supreme', 'SUPREME', '#00C8FF', '#B5FFFF'),
    ('global', 'GLOBAL', '#FFE259', '#FFA751'),
    ('mod', 'MOD', '#5555FF', '#55FFFF'),
    ('dev', 'DEV', '#AA00AA', '#FF55FF'),
    ('admin', 'ADMIN', '#FF5555', '#FFAAAA'),
    ('owner', 'OWNER', '#AA0000', '#FF5555'),
]
BASE_CHAR = 0xE300


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mezcla(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def oscuro(c, f):
    return tuple(int(v * f) for v in c)


def claro(c, f):
    return tuple(int(v + (255 - v) * f) for v in c)


def dibujar(texto, c1, c2):
    fnt = ImageFont.truetype(FUENTE, 8)
    n = len(texto)
    w = n * 8 + 6          # texto 8px por letra + 3px a cada lado (incluye borde)
    h = 11                 # borde + 1 + 7 de letra + 1 + borde
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = im.load()
    a, b = hexc(c1), hexc(c2)
    for x in range(w):
        t = x / max(1, w - 1)
        col = mezcla(a, b, t)
        for y in range(h):
            borde = x in (0, w - 1) or y in (0, h - 1)
            esquina = (x in (0, w - 1)) and (y in (0, h - 1))
            if esquina:
                continue
            if borde:
                px[x, y] = oscuro(col, 0.85) + (255,)
            else:
                # fondo oscuro con un poco del color, mas claro arriba
                px[x, y] = mezcla(oscuro(col, 0.22), oscuro(col, 0.12), (y - 1) / (h - 3)) + (255,)
    # brillo sutil en el borde superior
    for x in range(1, w - 1):
        px[x, 1] = mezcla(px[x, 1][:3], claro(mezcla(a, b, x / (w - 1)), 0.2), 0.25) + (255,)
    # texto: mascara 1-bit y degradado vertical claro->color
    mask = Image.new('1', (w, h), 0)
    d = ImageDraw.Draw(mask)
    d.fontmode = '1'
    d.text((3, 1), texto, font=fnt, fill=1)
    mp = mask.load()
    # sombra de 1px abajo a la derecha
    for y in range(h):
        for x in range(w):
            if mp[x, y] and x + 1 < w - 1 and y + 1 < h - 1 and not mp[x + 1, y + 1]:
                px[x + 1, y + 1] = oscuro(mezcla(a, b, x / (w - 1)), 0.45) + (255,)
    for y in range(h):
        for x in range(w):
            if mp[x, y]:
                base = mezcla(a, b, x / (w - 1))
                px[x, y] = claro(base, 0.88 - 0.30 * (y - 1) / 6) + (255,)
    return im


def png(im, escala):
    im = im.resize((im.width * escala, im.height * escala), Image.NEAREST)
    b = io.BytesIO()
    im.save(b, 'PNG', optimize=True)
    return b.getvalue(), im


proveedores = []
archivos = {}
mapa = {}
vista = Image.new('RGBA', (170, 14 * len(RANGOS) + 4), (30, 30, 36, 255))
for i, (rid, txt, c1, c2) in enumerate(RANGOS):
    ch = chr(BASE_CHAR + i)
    im = dibujar(txt, c1, c2)
    datos, _ = png(im, 2)  # imagen al doble para que se vea nitido, el font la baja con height
    archivos['assets/3y33/textures/font/rango_%s.png' % rid] = datos
    proveedores.append({"type": "bitmap", "file": "3y33:font/rango_%s.png" % rid, "height": 11, "ascent": 9, "chars": [ch]})
    mapa[rid] = {"char": ch, "texto": txt, "c1": c1, "c2": c2}
    vista.paste(im, (4, 2 + i * 14), im)
vista.resize((vista.width * 4, vista.height * 4), Image.NEAREST).save(os.path.join(BASE, 'rangos_preview.png'))

viejo = zipfile.ZipFile(ZIP)
fuente = json.loads(viejo.read('assets/minecraft/font/default.json'))
fuente['providers'] = [p for p in fuente['providers'] if not str(p.get('file', '')).startswith('3y33:font/rango_')] + proveedores
archivos['assets/minecraft/font/default.json'] = json.dumps(fuente, ensure_ascii=True, indent=1).encode()
mem = io.BytesIO()
with zipfile.ZipFile(mem, 'w', zipfile.ZIP_DEFLATED) as z:
    for it in viejo.infolist():
        if it.filename not in archivos:
            z.writestr(it, viejo.read(it.filename))
    for n, d in archivos.items():
        z.writestr(n, d)
viejo.close()
open(ZIP, 'wb').write(mem.getvalue())
json.dump(mapa, open(os.path.join(BASE, 'rangos.json'), 'w'), indent=1)
print(len(RANGOS), 'rangos', hashlib.sha1(mem.getvalue()).hexdigest(), os.path.getsize(ZIP))
