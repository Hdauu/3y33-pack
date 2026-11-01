"""Fuente de chat con la bandera argentina: cada letra va en tres franjas horizontales (celeste, blanco, celeste).
Genera tres variantes (3y33:bandera_0/1/2) que solo cambian el 'ascent', asi las letras suben y bajan formando una onda.
Letra: Press Start 2P (SIL OFL 1.1)."""
import io, json, zipfile, hashlib, os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
TTF = os.path.join(BASE, 'fuentes', 'PressStart2P.ttf')
ZIP = os.path.join(BASE, '..', 'java', '3y33-pack.zip')
CELESTE = (116, 172, 223, 255)
BLANCO = (255, 255, 255, 255)

chars = [chr(c) for c in range(33, 127)] + list('¡¿ÁÉÍÓÚÜÑáéíóúüñ«»·°')
COLS = 16
rows = [chars[i:i + COLS] for i in range(0, len(chars), COLS)]
rows[-1] += ['\u0000'] * (COLS - len(rows[-1]))
font = ImageFont.truetype(TTF, 8)


def glifo(ch):
    if ch == '\u0000':
        return Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    tmp = Image.new('L', (12, 14), 0)
    d = ImageDraw.Draw(tmp)
    d.fontmode = '1'
    d.text((1, 3), ch, font=font, fill=255)
    tmp = tmp.crop((1, 3, 9, 11))
    out = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    px, mp = out.load(), tmp.load()
    for y in range(8):
        # franjas: filas 0-2 celeste, 3-4 blanco, 5-7 celeste (la letra ocupa mas o menos las filas 0-6)
        col = BLANCO if y in (3, 4) else CELESTE
        for x in range(8):
            if mp[x, y] > 127:
                px[x, y] = col
    return out


img = Image.new('RGBA', (COLS * 8, len(rows) * 8), (0, 0, 0, 0))
for r, fila in enumerate(rows):
    for k, ch in enumerate(fila):
        g = glifo(ch)
        img.paste(g, (k * 8, r * 8), g)
b = io.BytesIO()
img.save(b, 'PNG', optimize=True)
png = b.getvalue()
img.resize((img.width * 6, img.height * 6), Image.NEAREST).save(os.path.join(BASE, 'bandera_preview.png'))

archivos = {'assets/3y33/textures/font/bandera.png': png}
for n, asc in enumerate((8, 7, 6)):
    archivos['assets/3y33/font/bandera_%d.json' % n] = json.dumps({"providers": [
        {"type": "space", "advances": {" ": 4}},
        {"type": "bitmap", "file": "3y33:font/bandera.png", "height": 8, "ascent": asc, "chars": ["".join(f) for f in rows]},
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
