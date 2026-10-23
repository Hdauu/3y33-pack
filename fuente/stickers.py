"""Stickers pixel 8x8 propios de 3y33 para títulos y mensajes de terrenos.
Genera assets/3y33/font/sticker.json (tamaño texto) y sticker_g.json (tamaño título) en el pack Java,
y stickers.json (código -> carácter) para el plugin 3y33Tierras."""
import io, json, zipfile, hashlib, os
from PIL import Image

BASE = os.path.dirname(os.path.abspath(__file__))
ZIP = os.path.join(BASE, '..', 'java', '3y33-pack.zip')

P = {  # paleta
    '.': None, 'k': (24, 24, 32), 'w': (255, 255, 255), 'r': (230, 50, 60), 'R': (150, 20, 35), 'p': (255, 140, 160),
    'y': (255, 214, 50), 'Y': (200, 140, 20), 'o': (255, 140, 30), 'g': (90, 200, 80), 'G': (40, 120, 50),
    'b': (80, 170, 255), 'B': (30, 90, 200), 'c': (120, 235, 230), 'C': (30, 160, 170), 's': (200, 205, 215),
    'S': (120, 125, 140), 'n': (140, 90, 50), 'N': (90, 55, 30), 'v': (180, 110, 240), 'V': (110, 50, 170),
    'l': (255, 240, 170),
}

ICONOS = {
    'corazon': ["........", ".RR..RR.", "RrrRRrrR", "RrprrrrR", "RrrrrrrR", ".RrrrrR.", "..RrrR..", "...RR..."],
    'estrella': ["...yy...", "...yy...", "yyyllyyy", ".yyllyy.", "..yyyy..", ".yyYYyy.", ".yY..Yy.", "Y......Y"],
    'espada': [".......s", "......ss", ".....ss.", "....ss..", "N..ss...", ".Nss....", "..NN....", ".N..N..."],
    'pico': [".ccccc..", "c.....c.", "....nc.c", "...n...c", "..n.....", ".n......", "n.......", "........"],
    'fuego': ["...o....", "..oo..o.", "..oyo.o.", ".oyyooo.", ".oylyyo.", "oyllllyo", "oyllllyo", ".oyyyyo."],
    'corona': ["y..y..y.", "yy.y.yy.", "yyyyyyy.", "yrybyry.", "yyyyyyy.", "YYYYYYY.", "........", "........"],
    'casa': ["...rr...", "..rrrr..", ".rrrrrr.", "rrrrrrrr", ".llllll.", ".lnnlbl.", ".lnnlll.", ".lnnlll."],
    'flor': ["..pp....", ".pyyp...", "..pp....", "...g..p.", "...g.pyp", "..gG..p.", "...g.g..", "...gg..."],
    'calavera': [".ssssss.", "ssssssss", "skksskks", "skksskks", "ssssssss", ".ssskss.", ".s.s.s..", ".SSSSS.."],
    'diamante': ["..cccc..", ".cwcccc.", "cwcccccC", "CccccccC", ".CccccC.", "..CccC..", "...CC...", "........"],
    'nota': ["...bbbb.", "...b..b.", "...b..b.", "...b..b.", ".bbb.bb.", "bBbbbBb.", "bbb.bbb.", "........"],
    'hoja': [".....gg.", "...ggGg.", "..ggGgg.", ".ggGggg.", ".gGggg..", ".Ggg....", "G.......", "........"],
    'pez': ["........", "..bbb..b", ".bwkbbbb", "bbbbbbB.", ".BBBB.bb", "..BB...b", "........", "........"],
    'luna': ["..yyy...", ".yy.....", "yy......", "yy......", "yy......", ".yy.....", "..yyy...", "........"],
    'sol': ["y..y..y.", ".y.y.y..", "..yyy...", "yyyly yy".replace(' ', 'y'), "..yyy...", ".y.y.y..", "y..y..y.", "........"],
    'rayo': ["....yy..", "...yy...", "..yy....", ".yyyyy..", "...yy...", "..yy....", ".yy.....", "y......."],
    'trofeo': ["yyyyyyy.", "y.yyy.y.", "y.yyy.y.", ".yyyyy..", "..yyy...", "...y....", "..YYY...", ".YYYYY.."],
    'escudo': ["BBBBBBB.", "BbbwbbB.", "BbwwwbB.", "BbbwbbB.", ".BbbbB..", ".BbbbB..", "..BbB...", "...B...."],
    'gema': ["........", "..vvvv..", ".vwvvvV.", "vvvvvvvV", ".VvvvvV.", "..VvvV..", "...VV...", "........"],
    'manzana': ["....n...", "...n.g..", ".rrrrr..", "rrprrrr.", "rprrrrr.", "rrrrrrr.", ".rrrrr..", "..r.r..."],
}
FALLBACK = {  # cómo se ve sin el pack (Bedrock o pack rechazado)
    'corazon': '❤', 'estrella': '★', 'espada': '⚔', 'pico': '⛏', 'fuego': '✹', 'corona': '♛', 'casa': '⌂', 'flor': '✿',
    'calavera': '☠', 'diamante': '◆', 'nota': '♪', 'hoja': '☘', 'pez': '≈', 'luna': '☾', 'sol': '☀', 'rayo': '⚡',
    'trofeo': '♜', 'escudo': '⛨', 'gema': '◇', 'manzana': '●',
}


def icono(filas):
    im = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for y, fila in enumerate(filas):
        for x, ch in enumerate(fila[:8]):
            c = P.get(ch)
            if c:
                im.putpixel((x, y), c + (255,))
    return im


nombres = list(ICONOS)
chars = [chr(0xE200 + i) for i in range(len(nombres))]


def hoja(escala):
    c = 8 * escala
    img = Image.new('RGBA', (c * len(nombres), c), (0, 0, 0, 0))
    for i, n in enumerate(nombres):
        g = icono(ICONOS[n]).resize((c, c), Image.NEAREST)
        img.paste(g, (i * c, 0), g)
    b = io.BytesIO()
    img.save(b, 'PNG', optimize=True)
    return b.getvalue(), img


def proveedor(tex, alto, ascent):
    return {"providers": [{"type": "bitmap", "file": tex, "height": alto, "ascent": ascent, "chars": ["".join(chars)]}]}


chico, _ = hoja(1)
grande, img = hoja(2)
img.resize((img.width * 3, img.height * 3), Image.NEAREST).save(os.path.join(BASE, 'stickers_preview.png'))

archivos = {
    'assets/3y33/textures/font/sticker.png': chico,
    'assets/3y33/textures/font/sticker_g.png': grande,
    'assets/3y33/font/sticker.json': json.dumps(proveedor('3y33:font/sticker.png', 8, 7)).encode(),
    'assets/3y33/font/sticker_g.json': json.dumps(proveedor('3y33:font/sticker_g.png', 16, 14)).encode(),
}
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

mapa = {n: {"char": chars[i], "fallback": FALLBACK[n]} for i, n in enumerate(nombres)}
out = os.path.join(BASE, '..', 'plugins', '3y33Tierras', 'src', 'main', 'resources', 'stickers.json')
open(out, 'w', encoding='utf-8').write(json.dumps(mapa, ensure_ascii=False, indent=1))
print(len(nombres), hashlib.sha1(mem.getvalue()).hexdigest())
