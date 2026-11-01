"""Fuente de chat con la bandera argentina, hecha con las letras de Minecraft (fuentes/ascii.png y accented.png, no se suben al repo).
Cada letra va en tres franjas horizontales (celeste, blanco, celeste). Genera tres variantes (3y33:bandera_0/1/2): las letras quedan quietas en la misma linea
base y lo que se corre 1 pixel es la franja blanca, asi el color ondea como una bandera."""
import io, json, zipfile, hashlib, os
from PIL import Image

BASE = os.path.dirname(os.path.abspath(__file__))
ZIP = os.path.join(BASE, '..', 'java', '3y33-pack.zip')
CELESTE = (116, 172, 223, 255)
BLANCO = (255, 255, 255, 255)

HOJA = Image.open(os.path.join(BASE, 'fuentes', 'ascii.png')).convert('RGBA')      # celdas de 8x8
ACC = Image.open(os.path.join(BASE, 'fuentes', 'accented.png')).convert('RGBA')    # celdas de 9x12
ACC_FILAS = [p for p in json.load(open(os.path.join(BASE, 'fuentes', 'default_font.json')))['providers'] if 'accented' in str(p.get('file'))][0]['chars']
ACENTOS = list('áéíóúÁÉÍÓÚñÑüÜ')

chars = [chr(c) for c in range(33, 127)]
rows = [chars[i:i + 16] for i in range(0, len(chars), 16)]
rows[-1] += ['\u0000'] * (16 - len(rows[-1]))
filas_acc = [ACENTOS[i:i + 16] for i in range(0, len(ACENTOS), 16)]
filas_acc[-1] += ['\u0000'] * (16 - len(filas_acc[-1]))


def pos_acc(ch):
    for r, fila in enumerate(ACC_FILAS):
        if ch in fila:
            return r, fila.index(ch)
    raise KeyError(ch)


def franjas(src, ancho, alto, base, fase=0):
    """Reparte el cuerpo de la letra (hasta la linea base 'base') en tres franjas: celeste, blanco, celeste.
    Lo que queda arriba (puntos, tildes) y abajo (colas) va en celeste."""
    sp = src.load()
    vacia = [all(sp[x, y][3] <= 127 for x in range(ancho)) for y in range(alto)]
    top = base
    while top - 1 >= 0 and not vacia[top - 1]:
        top -= 1
    if vacia[base]:  # letra sin pixeles en la linea base: busca el bloque mas cercano
        top = base + 1
    n = base - top + 1
    azul = max(1, round(n / 3)) if n >= 3 else 0
    out = Image.new('RGBA', (ancho, alto), (0, 0, 0, 0))
    px = out.load()
    w = n - 2 * azul
    ini = min(max(top + azul + fase, top + 1), base - w + 1) if w > 0 else 0
    for y in range(alto):
        if w > 0 and ini <= y < ini + w:
            col = BLANCO
        else:
            col = CELESTE
        for x in range(ancho):
            if sp[x, y][3] > 127:
                px[x, y] = col
    return out


def negrita(img, ancho, alto):
    """Engrosa el trazo 1 pixel a la derecha (como la negrita de Minecraft) para que las franjas se lean."""
    out, p, o = img.copy(), img.load(), None
    o = out.load()
    for y in range(alto):
        for x in range(ancho - 1):
            if p[x, y][3] > 127:
                o[x + 1, y] = p[x, y]
    return out


def glifo(ch, fase=0):
    if ch == '\u0000':
        return Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    cx, cy = (ord(ch) % 16) * 8, (ord(ch) // 16) * 8
    return franjas(negrita(HOJA.crop((cx, cy, cx + 8, cy + 8)), 8, 8), 8, 8, 6, fase)


def glifo_acc(ch, fase=0):
    if ch == '\u0000':
        return Image.new('RGBA', (9, 12), (0, 0, 0, 0))
    r, c = pos_acc(ch)
    return franjas(negrita(ACC.crop((c * 9, r * 12, c * 9 + 9, r * 12 + 12)), 9, 12), 9, 12, 9, fase)


def hoja(filas, ancho, alto, f):
    img = Image.new('RGBA', (16 * ancho, len(filas) * alto), (0, 0, 0, 0))
    for r, fila in enumerate(filas):
        for k, ch in enumerate(fila):
            g = f(ch)
            img.paste(g, (k * ancho, r * alto), g)
    return img


def png(img):
    b = io.BytesIO()
    img.save(b, 'PNG', optimize=True)
    return b.getvalue()


FASES = (0, 0, 0)  # franjas fijas: las tres variantes son iguales (el chat no se puede animar)
imgs = [(hoja(rows, 8, 8, lambda c, f=f: glifo(c, f)), hoja(filas_acc, 9, 12, lambda c, f=f: glifo_acc(c, f))) for f in FASES]
vista = Image.new('RGBA', (imgs[0][0].width, sum(a.height + b.height for a, b in imgs)), (40, 40, 48, 255))
y0 = 0
for a, b in imgs:
    vista.alpha_composite(a, (0, y0)); vista.alpha_composite(b, (0, y0 + a.height)); y0 += a.height + b.height
vista.resize((vista.width * 4, vista.height * 4), Image.NEAREST).save(os.path.join(BASE, 'bandera_preview.png'))

archivos = {}
for n, (a, b) in enumerate(imgs):
    archivos['assets/3y33/textures/font/bandera_%d.png' % n] = png(a)
    archivos['assets/3y33/textures/font/bandera_acc_%d.png' % n] = png(b)
    archivos['assets/3y33/font/bandera_%d.json' % n] = json.dumps({"providers": [
        {"type": "space", "advances": {" ": 4}},
        {"type": "bitmap", "file": "3y33:font/bandera_%d.png" % n, "height": 8, "ascent": 8, "chars": ["".join(f) for f in rows]},
        {"type": "bitmap", "file": "3y33:font/bandera_acc_%d.png" % n, "height": 12, "ascent": 11, "chars": ["".join(f) for f in filas_acc]},
        {"type": "reference", "id": "minecraft:default"}]}, ensure_ascii=False, indent=1).encode()
VIEJOS = ('assets/3y33/textures/font/bandera.png', 'assets/3y33/textures/font/bandera_acc.png')

viejo = zipfile.ZipFile(ZIP)
mem = io.BytesIO()
with zipfile.ZipFile(mem, 'w', zipfile.ZIP_DEFLATED) as z:
    for it in viejo.infolist():
        if it.filename not in archivos and it.filename not in VIEJOS:
            z.writestr(it, viejo.read(it.filename))
    for n, d in archivos.items():
        z.writestr(n, d)
viejo.close()
open(ZIP, 'wb').write(mem.getvalue())
print(hashlib.sha1(mem.getvalue()).hexdigest(), len(mem.getvalue()))
