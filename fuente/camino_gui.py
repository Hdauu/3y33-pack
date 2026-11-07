"""Fondo de la ventana "Camino de recompensas" (176 x 222, 6 filas): un caminito de tierra que serpentea por un prado, con una plaquita de piedra
en cada casillero de nodo. Pixel art propio dibujado a 1x. Los casilleros caen donde los usa el plugin:
  filas 0, 2 y 4: nodos en las columnas 0, 2, 4, 6, 8 (en la fila 2 el camino va de derecha a izquierda)
  fila 1: el camino baja por la columna 8 | fila 3: baja por la columna 0
  fila 5: botones (volver 0, anterior 2, info 4, siguiente 6, boosts 8)
Uso: python3 camino_gui.py <salida.png>"""
import sys, os, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont
import tienda_taberna as T
from tienda_taberna import pto, rect, borde, tablones, hueco, CONTORNO, M_BASE, M_CLARA, M_VETA, M_OSC, ORO, ORO_OSC, ORO_LUZ, CREMA, CREMA_OSC

W, H = 176, 222
FILAS = 6
PASTO, PASTO_L, PASTO_O = (92, 154, 66), (112, 176, 80), (70, 126, 54)
TIERRA, TIERRA_L, TIERRA_O, TIERRA_B = (206, 160, 100), (226, 184, 120), (170, 122, 70), (130, 90, 50)
PIEDRA, PIEDRA_L, PIEDRA_O = (150, 152, 160), (190, 192, 198), (92, 94, 104)


def centro(r, c):
    return 7 + 18 * c + 9, 17 + 18 * r + 9


def ruta():
    """Puntos por donde pasa el camino, en orden: serpentina por las 3 filas de nodos."""
    pts = [centro(0, c) for c in range(0, 9)]
    pts += [centro(1, 8)]
    pts += [centro(2, c) for c in range(8, -1, -1)]
    pts += [centro(3, 0)]
    pts += [centro(4, c) for c in range(0, 9)]
    return pts


def pasto(im, x0, y0, x1, y1):
    r = random.Random(7)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            v = r.random()
            pto(im, x, y, PASTO_L if v < 0.10 else PASTO_O if v > 0.88 else PASTO)
    for _ in range(90):                                   # matitas de pasto
        x, y = r.randint(x0 + 1, x1 - 2), r.randint(y0 + 2, y1 - 1)
        pto(im, x, y, PASTO_O); pto(im, x, y - 1, PASTO_L)
    for _ in range(26):                                   # florcitas
        x, y = r.randint(x0 + 2, x1 - 2), r.randint(y0 + 2, y1 - 2)
        col = r.choice(((255, 236, 120), (255, 255, 255), (240, 120, 130), (170, 150, 240)))
        pto(im, x, y, col); pto(im, x, y + 1, PASTO_O)


def camino(im):
    pts = ruta()
    medio = 5                                              # semi-ancho de la senda
    # 1) borde oscuro + 2) tierra, para que el camino tenga contorno parejo
    for capa, mg, col in ((0, medio + 1, TIERRA_B), (1, medio, TIERRA)):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            rect(im, min(x0, x1) - mg, min(y0, y1) - mg, max(x0, x1) + mg, max(y0, y1) + mg, col)
    r = random.Random(11)
    # luz arriba, sombra abajo y piedritas
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        xa, xb, ya, yb = min(x0, x1) - medio, max(x0, x1) + medio, min(y0, y1) - medio, max(y0, y1) + medio
        for x in range(xa, xb + 1):
            if im.getpixel((x, ya - 1))[:3] == PASTO or True: pass
        rect(im, xa, ya, xb, ya, TIERRA_L) if x0 != x1 else None
        rect(im, xa, yb, xb, yb, TIERRA_O) if x0 != x1 else None
        for _ in range(max(2, (xb - xa + yb - ya) // 7)):
            px, py = r.randint(xa + 1, xb - 1), r.randint(ya + 1, yb - 1)
            pto(im, px, py, TIERRA_O if r.random() < 0.5 else TIERRA_L)
    # esquinas redondeadas del camino (se comen los vertices duros)
    for (x, y) in ((pts[8]), pts[10], pts[18], pts[20]):
        pass


def placa(im, cx, cy):
    """Plaquita redonda de piedra donde va el item del nodo (el item es de 16x16, la placa de 20)."""
    d = ImageDraw.Draw(im)
    d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=CONTORNO + (255,))
    d.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], fill=PIEDRA + (255,))
    d.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], outline=PIEDRA_O + (255,))
    d.arc([cx - 8, cy - 8, cx + 8, cy + 8], 180, 300, fill=PIEDRA_L + (255,))
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=(112, 114, 124, 255), outline=PIEDRA_O + (255,))
    d.arc([cx - 6, cy - 6, cx + 6, cy + 6], 200, 290, fill=(70, 72, 82, 255))


def arbol(im, x, y):
    """Arbolito de ~13x15 con el pie en (x, y)."""
    rect(im, x - 1, y - 4, x + 1, y, (104, 66, 36)); rect(im, x - 1, y - 4, x - 1, y, (140, 92, 52)); pto(im, x + 1, y, (70, 42, 22))
    d = ImageDraw.Draw(im)
    d.ellipse([x - 6, y - 14, x + 6, y - 3], fill=(50, 104, 52, 255), outline=(30, 70, 36, 255))
    d.ellipse([x - 4, y - 12, x + 1, y - 7], fill=(78, 142, 70, 255))
    pto(im, x - 3, y - 11, (150, 210, 110)); pto(im, x - 2, y - 10, (150, 210, 110))


def arbusto(im, x, y):
    d = ImageDraw.Draw(im)
    d.ellipse([x - 6, y - 5, x + 6, y + 2], fill=(56, 118, 56, 255), outline=(30, 70, 36, 255))
    d.ellipse([x - 4, y - 4, x + 1, y], fill=(86, 152, 74, 255))
    for (dx, dy) in ((3, -2), (-2, 0)): pto(im, x + dx, y + dy, (240, 120, 130))


def roca(im, x, y):
    d = ImageDraw.Draw(im)
    d.ellipse([x - 4, y - 3, x + 4, y + 2], fill=PIEDRA + (255,), outline=PIEDRA_O + (255,))
    pto(im, x - 2, y - 2, PIEDRA_L); pto(im, x - 1, y - 2, PIEDRA_L)


def cartel_poste(im, x, y):
    rect(im, x, y - 12, x + 1, y, (104, 66, 36))
    rect(im, x - 5, y - 13, x + 6, y - 8, (176, 120, 70)); borde(im, x - 5, y - 13, x + 6, y - 8, CONTORNO)
    pto(im, x - 3, y - 11, ORO); pto(im, x - 1, y - 11, ORO); pto(im, x + 1, y - 11, ORO); pto(im, x + 3, y - 11, ORO)


def bandera(im, x, y):
    rect(im, x, y - 14, x, y, (96, 72, 46))
    for k in range(5): rect(im, x + 1, y - 14 + k, x + 8 - k // 2, y - 14 + k, (200, 40, 56))
    pto(im, x, y - 15, ORO)


def generar(salida):
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    rect(im, 0, 0, W - 1, H - 1, CONTORNO)
    tablones(im, 1, 1, W - 2, H - 2, M_BASE, M_CLARA, M_VETA, M_OSC, alto=12, semilla=21)
    borde(im, 1, 1, W - 2, H - 2, (96, 58, 32)); borde(im, 2, 2, W - 3, H - 3, M_CLARA)
    for (x, y) in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)): im.putpixel((x, y), (0, 0, 0, 0))

    # prado
    top, bot = 16, 17 + 18 * FILAS - 1 + 1
    rect(im, 6, top - 1, 169, bot + 1, CONTORNO)
    pasto(im, 7, top, 168, bot)
    # sombra suave bajo la viga de arriba
    rect(im, 7, top, 168, top + 1, PASTO_O)
    camino(im)
    # decorado en los casilleros que no usa el camino
    for (r, c) in ((1, 1), (1, 4)): arbol(im, 7 + 18 * c + 9, 17 + 18 * r + 16)
    arbusto(im, 7 + 18 * 2 + 9, 17 + 18 * 1 + 13); arbusto(im, 7 + 18 * 6 + 9, 17 + 18 * 1 + 14)
    roca(im, 7 + 18 * 3 + 9, 17 + 18 * 1 + 13); roca(im, 7 + 18 * 5 + 6, 17 + 18 * 1 + 9)
    cartel_poste(im, 7 + 18 * 0 + 9, 17 + 18 * 1 + 15)
    for (c, f) in ((2, arbol), (5, arbol), (7, arbol)): f(im, 7 + 18 * c + 9, 17 + 18 * 3 + 16)
    arbusto(im, 7 + 18 * 1 + 9, 17 + 18 * 3 + 14); arbusto(im, 7 + 18 * 4 + 9, 17 + 18 * 3 + 14); arbusto(im, 7 + 18 * 8 + 9, 17 + 18 * 3 + 14)
    roca(im, 7 + 18 * 3 + 12, 17 + 18 * 3 + 15)
    # plaquitas de los nodos
    for r in (0, 2, 4):
        for c in range(0, 9, 2):
            placa(im, *centro(r, c))

    # fila de botones: una tabla de madera con 5 placas
    y5 = 17 + 18 * 5
    rect(im, 7, y5 - 1, 168, y5 + 18, CONTORNO)
    tablones(im, 8, y5, 167, y5 + 17, (112, 70, 40), M_CLARA, (94, 58, 34), M_OSC, alto=9, semilla=31)
    for c in (0, 2, 4, 6, 8):
        x = 7 + 18 * c
        rect(im, x, y5, x + 17, y5 + 17, (201, 149, 91)); borde(im, x, y5, x + 17, y5 + 17, (150, 100, 50))
        rect(im, x + 1, y5 + 1, x + 16, y5 + 1, (232, 186, 124)); rect(im, x + 1, y5 + 16, x + 16, y5 + 16, (168, 118, 66))

    # cartel de arriba
    f = ImageFont.truetype(T.FONT, 8)
    d = ImageDraw.Draw(im); d.fontmode = '1'
    texto = 'CAMINO'
    tw = int(d.textlength(texto, font=f)); ancho = tw + 22
    x0 = (W - ancho) // 2; x1 = x0 + ancho - 1
    rect(im, x0 + 1, 3, x1 + 1, 15, (40, 22, 14)); rect(im, x0, 2, x1, 14, (112, 68, 38))
    tablones(im, x0 + 1, 3, x1 - 1, 13, (128, 80, 44), (158, 106, 62), (104, 64, 34), (84, 50, 28), alto=9, semilla=5)
    borde(im, x0, 2, x1, 14, CONTORNO); borde(im, x0 + 1, 3, x1 - 1, 13, (200, 150, 78))
    for (nx, ny) in ((x0 + 3, 5), (x1 - 3, 5), (x0 + 3, 11), (x1 - 3, 11)): pto(im, nx, ny, ORO); pto(im, nx + 1, ny + 1, ORO_OSC)
    d = ImageDraw.Draw(im); d.fontmode = '1'
    tx = (W - tw) // 2
    d.text((tx + 1, 5), texto, font=f, fill=(40, 20, 8, 255)); d.text((tx, 4), texto, font=f, fill=(255, 236, 168, 255))

    # separador: cinta crema para el "Inventario" y bandeja del inventario (igual que las tiendas)
    sep = 17 + 18 * FILAS + 3
    rect(im, 62, sep + 3, 170, sep + 6, M_BASE); rect(im, 62, sep + 3, 170, sep + 3, M_CLARA); rect(im, 62, sep + 6, 170, sep + 6, M_OSC)
    borde(im, 62, sep + 3, 170, sep + 6, CONTORNO)
    rect(im, 5, sep - 1, 62, sep + 10, CREMA); borde(im, 5, sep - 1, 62, sep + 10, CONTORNO)
    rect(im, 6, sep, 61, sep, (255, 250, 232)); rect(im, 6, sep + 9, 61, sep + 9, CREMA_OSC)
    rect(im, 3, sep, 5, sep + 9, M_CLARA); rect(im, 3, sep, 3, sep + 9, CONTORNO); rect(im, 5, sep, 5, sep + 9, M_OSC)
    base = 84 + 18 * (FILAS - 3)
    rect(im, 6, base - 2, 169, base + 58 + 18 + 1, CONTORNO)
    tablones(im, 7, base - 1, 168, base + 58 + 18, T.M_PANEL, T.M_PANEL_L, T.M_PANEL_V, (78, 46, 28), alto=9, semilla=3)
    for r in range(3):
        for c in range(9): hueco(im, 7 + 18 * c, base + 18 * r)
    for c in range(9): hueco(im, 7 + 18 * c, base + 58)
    rect(im, 7, base + 55, 168, base + 56, (74, 44, 26))
    im.save(salida)
    return im.size


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'camino_gui.png'
    print(generar(out))
    if len(sys.argv) > 2:
        im = Image.open(out); im.resize((W * 4, H * 4), Image.NEAREST).save(sys.argv[2])
