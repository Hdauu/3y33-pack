"""Fuente de chat con la bandera argentina, hecha con las letras de Minecraft (fuentes/ascii.png y accented.png, no se suben al repo).
Cada letra va en tres franjas horizontales (celeste, blanco, celeste). Genera tres variantes (3y33:bandera_0/1/2) que solo cambian
el 'ascent', asi las letras suben y bajan formando una onda."""
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


def franjas(src, ancho, alto, arriba):
    """arriba = fila donde empieza el cuerpo de la letra (7 filas): 2 celestes, 3 blancas, 2 celestes (y lo de abajo celeste)."""
    out = Image.new('RGBA', (ancho, alto), (0, 0, 0, 0))
    sp, px = src.load(), out.load()
    for y in range(alto):
        col = BLANCO if (y - arriba) in (2, 3, 4) else CELESTE
        for x in range(ancho):
            if sp[x, y][3] > 127:
                px[x, y] = col
    return out


def glifo(ch):
    if ch == '\u0000':
        return Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    cx, cy = (ord(ch) % 16) * 8, (ord(ch) // 16) * 8
    return franjas(HOJA.crop((cx, cy, cx + 8, cy + 8)), 8, 8, 0)


def glifo_acc(ch):
    if ch == '\u0000':
        return Image.new('RGBA', (9, 12), (0, 0, 0, 0))
    r, c = pos_acc(ch)
    return franjas(ACC.crop((c * 9, r * 12, c * 9 + 9, r * 12 + 12)), 9, 12, 3)


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


img1, img2 = hoja(rows, 8, 8, glifo), hoja(filas_acc, 9, 12, glifo_acc)
vista = Image.new('RGBA', (img1.width, img1.height + img2.height), (40, 40, 48, 255))
vista.alpha_composite(img1, (0, 0)); vista.alpha_composite(img2, (0, img1.height))
vista.resize((vista.width * 6, vista.height * 6), Image.NEAREST).save(os.path.join(BASE, 'bandera_preview.png'))

archivos = {'assets/3y33/textures/font/bandera.png': png(img1), 'assets/3y33/textures/font/bandera_acc.png': png(img2)}
for n, asc in enumerate((8, 7, 6)):
    archivos['assets/3y33/font/bandera_%d.json' % n] = json.dumps({"providers": [
        {"type": "space", "advances": {" ": 4}},
        {"type": "bitmap", "file": "3y33:font/bandera.png", "height": 8, "ascent": asc, "chars": ["".join(f) for f in rows]},
        {"type": "bitmap", "file": "3y33:font/bandera_acc.png", "height": 12, "ascent": asc + 3, "chars": ["".join(f) for f in filas_acc]},
        {"type": "reference", "id": "minecraft:default"}]}, ensure_ascii=False, indent=1).encode()

viejo = zipfile.ZipFile(ZIP)
mem = io.BytesIO()
with zipfile.ZipFile(mem, 'w', zipfile.ZIP_DEFLATED) as z:
    for it in viejo.infolist():
        if it.filename not in archivos:
            z.writestr(it, viejo.read(it.filename))
    for n, d in archivos.items():
        z.writestr(n, d)
viejo.close()
open(ZIP, 'wb').write(mem.getvalue())
print(hashlib.sha1(mem.getvalue()).hexdigest(), len(mem.getvalue()))
