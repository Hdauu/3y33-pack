"""Agrega al pack Java las fuentes 3y33:titulo (grande) y 3y33:texto (normal) a partir de Press Start 2P (SIL OFL 1.1)."""
import io, json, zipfile, hashlib, os, sys
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
TTF = os.path.join(BASE, 'fuentes', 'PressStart2P.ttf')
ZIP = os.path.join(BASE, '..', 'java', '3y33-pack.zip')

chars = [chr(c) for c in range(33, 127)] + list('¡¿ÁÉÍÓÚÜÑáéíóúüñ«»·°')
COLS = 16
rows = [chars[i:i + COLS] for i in range(0, len(chars), COLS)]
rows[-1] += ['\u0000'] * (COLS - len(rows[-1]))  # relleno

font = ImageFont.truetype(TTF, 8)


def glifo(ch):
    im = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    if ch == '\u0000':
        return im
    tmp = Image.new('L', (12, 14), 0)
    d = ImageDraw.Draw(tmp)
    d.fontmode = '1'
    d.text((1, 3), ch, font=font, fill=255)
    tmp = tmp.crop((1, 3, 9, 11))  # filas 3..10 -> 8 filas, base en la fila 6
    im.putalpha(tmp)
    blanco = Image.new('RGBA', (8, 8), (255, 255, 255, 255))
    blanco.putalpha(tmp)
    return blanco


def hoja(escala):
    c = 8 * escala
    img = Image.new('RGBA', (COLS * c, len(rows) * c), (0, 0, 0, 0))
    for r, fila in enumerate(rows):
        for k, ch in enumerate(fila):
            g = glifo(ch).resize((c, c), Image.NEAREST)
            img.paste(g, (k * c, r * c), g)
    b = io.BytesIO()
    img.save(b, 'PNG', optimize=True)
    return b.getvalue()


def proveedor(textura, alto, ascent, espacio):
    return {"providers": [
        {"type": "space", "advances": {" ": espacio}},
        {"type": "bitmap", "file": textura, "height": alto, "ascent": ascent,
         "chars": ["".join(f) for f in rows]}]}


archivos = {
    'assets/3y33/textures/font/titulo.png': hoja(2),
    'assets/3y33/textures/font/texto.png': hoja(1),
    'assets/3y33/font/titulo.json': json.dumps(proveedor('3y33:font/titulo.png', 16, 14, 7), ensure_ascii=False, indent=1).encode(),
    'assets/3y33/font/texto.json': json.dumps(proveedor('3y33:font/texto.png', 8, 7, 4), ensure_ascii=False, indent=1).encode(),
    'licencias/PressStart2P-OFL.txt': open(os.path.join(BASE, 'fuentes', 'OFL.txt'), 'rb').read(),
}

viejo = zipfile.ZipFile(ZIP)
mem = io.BytesIO()
with zipfile.ZipFile(mem, 'w', zipfile.ZIP_DEFLATED) as z:
    for it in viejo.infolist():
        if it.filename in archivos:
            continue
        data = viejo.read(it.filename)
        if it.filename == 'CREDITS.txt':
            t = data.decode()
            if 'Press Start 2P' not in t:
                t += '\nFuente de titulos: Press Start 2P (c) 2012 The Press Start 2P Project Authors, SIL Open Font License 1.1\n'
            data = t.encode()
        z.writestr(it, data)
    for n, d in archivos.items():
        z.writestr(n, d)
viejo.close()
open(ZIP, 'wb').write(mem.getvalue())
print(hashlib.sha1(mem.getvalue()).hexdigest(), len(mem.getvalue()))
