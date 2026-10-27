"""Tags de rango en pixel art (estilo nogard.dev) para el pack Java.
Cada rango es UN solo caracter del font default (U+E300...), asi se pone directo en ModernTags/TAB/chat.
Dibuja con Press Start 2P: borde y texto en degradado del color del rango sobre fondo oscuro."""
import io, json, zipfile, hashlib, os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
ZIP = os.path.join(BASE, '..', 'java', '3y33-pack.zip')
FUENTE = os.path.join(BASE, 'fuentes', 'PressStart2P.ttf')

RANGOS = [  # id, texto, color 1, color 2 (casi planos: tags solidos)
    ('vip', 'VIP', '#00AEE0', '#12C4F5'),
    ('vipplus', 'VIP+', '#F2A900', '#FFBE1F'),
    ('mod', 'MOD', '#1670D9', '#2A86F0'),
    ('dev', 'DEV', '#7B3FE4', '#9257F5'),
    ('admin', 'ADMIN', '#E02B2B', '#F23D3D'),
    ('owner', 'OWNER', '#A3123A', '#C21A4C'),
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


GLIFOS = {
    'I': ["###", ".#.", ".#.", ".#.", "###"],
    'V': ["#...#", "#...#", "#...#", ".#.#.", "..#.."],
    'P': ["###.", "#..#", "###.", "#...", "#..."],
    '+': ["...", ".#.", "###", ".#.", "..."],
    'M': ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
    'O': [".##.", "#..#", "#..#", "#..#", ".##."],
    'D': ["###.", "#..#", "#..#", "#..#", "###."],
    'A': [".##.", "#..#", "####", "#..#", "#..#"],
    'N': ["#..#", "##.#", "#.##", "#..#", "#..#"],
    'R': ["###.", "#..#", "###.", "#.#.", "#..#"],
    'W': ["#...#", "#...#", "#.#.#", "##.##", "#...#"],
    'E': ["####", "#...", "###.", "#...", "####"],
}


def dibujar(texto, c1, c2):
    """Insignia solida de 10px logicos de alto, pixeles bien definidos (se escala x2 EXACTO con vecino mas cercano)."""
    ancho = sum(len(GLIFOS[c][0]) for c in texto) + (len(texto) - 1)
    w = ancho + 6
    H = 10
    col = hexc(c1)
    im = Image.new('RGBA', (w, H), (0, 0, 0, 0))
    px = im.load()
    for x in range(w):
        for y in range(H):
            if x in (0, w - 1) and y in (0, H - 1):
                continue
            if x in (0, w - 1) or y in (0, H - 1):
                px[x, y] = oscuro(col, 0.5) + (255,)
            elif y == 1:
                px[x, y] = claro(col, 0.35) + (255,)
            else:
                px[x, y] = mezcla(col, oscuro(col, 0.85), (y - 2) / (H - 4)) + (255,)
    x0 = 3
    for c in texto:
        g = GLIFOS[c]
        for gy, fila in enumerate(g):
            for gx, v in enumerate(fila):
                if v == '#':
                    px[x0 + gx, 2 + gy + 1] = oscuro(col, 0.4) + (255,)   # sombra finita
        for gy, fila in enumerate(g):
            for gx, v in enumerate(fila):
                if v == '#':
                    px[x0 + gx, 2 + gy] = (255, 255, 255, 255)
        x0 += len(g[0]) + 1
    return im


def png(im, escala):
    im = im.resize((im.width * escala, im.height * escala), Image.NEAREST)
    b = io.BytesIO()
    im.save(b, 'PNG', optimize=True)
    return b.getvalue(), im


proveedores = []
archivos = {}
mapa = {}
vista = Image.new('RGBA', (170, 17 * len(RANGOS) + 4), (30, 30, 36, 255))
for i, (rid, txt, c1, c2) in enumerate(RANGOS):
    ch = chr(BASE_CHAR + i)
    im = dibujar(txt, c1, c2)
    datos, _ = png(im, 2)  # x2 exacto, vecino mas cercano: alto 20 textura = alto 10 del font
    archivos['assets/3y33/textures/font/rango_%s.png' % rid] = datos
    proveedores.append({"type": "bitmap", "file": "3y33:font/rango_%s.png" % rid, "height": 10, "ascent": 8, "chars": [ch]})
    mapa[rid] = {"char": ch, "texto": txt, "c1": c1, "c2": c2}
    vista.paste(im, (4, 2 + i * 17), im)
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
