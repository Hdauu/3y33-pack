"""Fondo de la ventana "Camino de recompensas" (176 x 222, 6 filas) con el MISMO estilo que las ventanas de Trabajos (pergamino crema, marco rojo/dorado,
casilleros marrones). Se parte de trabajos_gui.png (camino_base_trabajos.png) y en el panel se dibuja un caminito de tinta/tierra que serpentea,
con un casillero en cada nodo. Los casilleros caen donde los usa el plugin:
  filas 0, 2 y 4: nodos en las columnas 0, 2, 4, 6, 8 (en la fila 2 el camino va de derecha a izquierda)
  fila 1: el camino baja por la columna 8 | fila 3: baja por la columna 0
  fila 5: botones (volver 0, anterior 2, info 4, siguiente 6, boosts 8)
Uso: python3 camino_gui.py <salida.png> [vista_x4.png]"""
import sys, os, random
from PIL import Image, ImageDraw

W, H = 176, 222
AQUI = os.path.dirname(os.path.abspath(__file__))
TIERRA, TIERRA_L, TIERRA_O, BORDE = (222, 190, 140), (234, 207, 163), (206, 170, 118), (160, 112, 66)
TINTA, TINTA_L = (128, 90, 56), (170, 128, 84)
HOJA, HOJA_L, HOJA_O = (116, 138, 78), (150, 170, 104), (84, 106, 58)
ROCA, ROCA_L = (176, 160, 138), (206, 192, 170)
ROJO = (176, 38, 42)


def centro(r, c):
    return 7 + 18 * c + 9, 17 + 18 * r + 9


def ruta():
    pts = [centro(0, c) for c in range(0, 9)] + [centro(1, 8)]
    pts += [centro(2, c) for c in range(8, -1, -1)] + [centro(3, 0)]
    pts += [centro(4, c) for c in range(0, 9)]
    return pts


def pto(im, x, y, col):
    if 0 <= x < W and 0 <= y < H: im.putpixel((x, y), tuple(col) + (255,))


def rect(im, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1): pto(im, x, y, col)


def camino(im):
    pts = ruta()
    medio = 5
    for mg, col in ((medio + 1, BORDE), (medio, TIERRA)):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            rect(im, min(x0, x1) - mg, min(y0, y1) - mg, max(x0, x1) + mg, max(y0, y1) + mg, col)
    r = random.Random(11)
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        xa, xb, ya, yb = min(x0, x1) - medio, max(x0, x1) + medio, min(y0, y1) - medio, max(y0, y1) + medio
        if y0 == y1:
            rect(im, xa, ya, xb, ya, TIERRA_L); rect(im, xa, yb, xb, yb, TIERRA_O)
        for _ in range(max(2, (xb - xa + yb - ya) // 8)):
            pto(im, r.randint(xa + 1, xb - 1), r.randint(ya + 1, yb - 1), TIERRA_O if r.random() < .5 else TIERRA_L)


def arbol(im, x, y):
    rect(im, x - 1, y - 4, x + 1, y, TINTA)
    d = ImageDraw.Draw(im)
    d.ellipse([x - 6, y - 14, x + 6, y - 3], fill=HOJA + (255,), outline=HOJA_O + (255,))
    d.ellipse([x - 4, y - 12, x + 1, y - 7], fill=HOJA_L + (255,))
    pto(im, x - 3, y - 11, (200, 214, 150))


def arbusto(im, x, y):
    d = ImageDraw.Draw(im)
    d.ellipse([x - 6, y - 5, x + 6, y + 2], fill=HOJA + (255,), outline=HOJA_O + (255,))
    d.ellipse([x - 4, y - 4, x + 1, y], fill=HOJA_L + (255,))
    pto(im, x + 3, y - 2, ROJO)


def roca(im, x, y):
    d = ImageDraw.Draw(im)
    d.ellipse([x - 4, y - 3, x + 4, y + 2], fill=ROCA + (255,), outline=TINTA + (255,))
    pto(im, x - 2, y - 2, ROCA_L); pto(im, x - 1, y - 2, ROCA_L)


def cartel(im, x, y):
    rect(im, x, y - 12, x + 1, y, TINTA)
    rect(im, x - 5, y - 13, x + 6, y - 8, TIERRA_L)
    for (a, b) in ((x - 5, x + 6),):
        rect(im, a, y - 13, b, y - 13, TINTA); rect(im, a, y - 8, b, y - 8, TINTA)
    rect(im, x - 5, y - 13, x - 5, y - 8, TINTA); rect(im, x + 6, y - 13, x + 6, y - 8, TINTA)
    pto(im, x - 3, y - 11, ROJO); pto(im, x - 1, y - 11, TINTA_L); pto(im, x + 1, y - 11, TINTA_L); pto(im, x + 3, y - 11, TINTA_L)


def generar(salida, base=None):
    im = Image.open(base or os.path.join(AQUI, 'camino_base_trabajos.png')).convert('RGBA')
    slot = im.crop((7, 107, 25, 125))                              # casillero marron de las ventanas de Trabajos (18x18)
    # fila de botones: la base trae casilleros en las columnas 0, 3, 5 y 8 -> dejar el pergamino liso y poner 0, 2, 4, 6, 8
    tira = im.crop((25, 107, 43, 125))
    for c in (3, 5): im.paste(tira, (7 + 18 * c, 107))
    # senda y decorado
    camino(im)
    for (r, c) in ((1, 1), (1, 4)): arbol(im, 7 + 18 * c + 9, 17 + 18 * r + 16)
    arbusto(im, 7 + 18 * 2 + 9, 17 + 18 * 1 + 13); arbusto(im, 7 + 18 * 6 + 9, 17 + 18 * 1 + 14)
    roca(im, 7 + 18 * 3 + 9, 17 + 18 * 1 + 13); roca(im, 7 + 18 * 5 + 6, 17 + 18 * 1 + 9)
    cartel(im, 7 + 9, 17 + 18 * 1 + 15)
    for c in (2, 5, 7): arbol(im, 7 + 18 * c + 9, 17 + 18 * 3 + 16)
    for c in (1, 4, 8): arbusto(im, 7 + 18 * c + 9, 17 + 18 * 3 + 14)
    roca(im, 7 + 18 * 3 + 12, 17 + 18 * 3 + 15)
    for r in (0, 2, 4):
        for c in range(0, 9, 2): im.paste(slot, (7 + 18 * c, 17 + 18 * r))
    for c in (2, 4, 6): im.paste(slot, (7 + 18 * c, 107))
    im.save(salida)
    return im.size


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'camino_gui.png'
    print(generar(out))
    if len(sys.argv) > 2: Image.open(out).resize((W * 4, H * 4), Image.NEAREST).save(sys.argv[2])
