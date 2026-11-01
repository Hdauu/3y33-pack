"""Fuente de chat con la bandera argentina, hecha con las letras de Minecraft (fuentes/ascii.png, no se sube al repo): cada letra va en tres franjas horizontales (celeste, blanco, celeste).
Genera tres variantes (3y33:bandera_0/1/2) que solo cambian el 'ascent', asi las letras suben y bajan formando una onda.
Letra: Press Start 2P (SIL OFL 1.1)."""
import io, json, zipfile, hashlib, os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
TTF = os.path.join(BASE, 'fuentes', 'PressStart2P.ttf')
ZIP = os.path.join(BASE, '..', 'java', '3y33-pack.zip')
CELESTE = (116, 172, 223, 255)
BLANCO = (255, 255, 255, 255)

# letras originales de Minecraft (ascii.png, orden CP437) recoloreadas en franjas
HOJA = Image.open(os.path.join(BASE, 'fuentes', 'ascii.png')).convert('RGBA')
EXTRA = {'á': 0xA0, 'í': 0xA1, 'ó': 0xA2, 'ú': 0xA3, 'ñ': 0xA4, 'Ñ': 0xA5, '¿': 0xA8, '¡': 0xAD, 'é': 0x82, 'ü': 0x81, 'Ü': 0x9A, 'É': 0x90, '«': 0xAE, '»': 0xAF, '°': 0xF8, '·': 0xFA}
chars = [chr(c) for c in range(33, 127)] + list(EXTRA)
COLS = 16
rows = [chars[i:i + COLS] for i in range(0, len(chars), COLS)]
rows[-1] += ['\u0000'] * (COLS - len(rows[-1]))


def glifo(ch):
    out = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    if ch == '\u0000':
        return out
    cp = EXTRA.get(ch, ord(ch))
    cx, cy = (cp % 16) * 8, (cp // 16) * 8
    src = HOJA.crop((cx, cy, cx + 8, cy + 8))
    sp, px = src.load(), out.load()
    for y in range(8):
        col = BLANCO if y in (2, 3, 4) else CELESTE  # 2 filas celeste, 3 blanco, 3 celeste (la letra ocupa filas 0-6)
        for x in range(8):
            if sp[x, y][3] > 127:
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
