"""Ventanas de las tiendas con tema "taberna": madera, toldo a rayas, cartel colgado y farolitos. Pixel art propio dibujado a 1x (sin suavizado).
Mismas medidas y casilleros que tienda_gui.py (176 x 114+18*filas), asi el plugin no cambia: se reemplazan los PNG tienda/ajustes/admins/historial.
Uso: python3 tienda_taberna.py <carpeta de salida> [hoja <salida.png>]"""
import sys, os, random
from PIL import Image, ImageDraw, ImageFont

W = 176
FONT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fuentes', 'PressStart2P.ttf')

# madera
M_OSC, M_BASE, M_CLARA, M_VETA = (62, 36, 22), (150, 98, 56), (176, 120, 70), (128, 82, 46)
M_PANEL, M_PANEL_L, M_PANEL_V = (104, 64, 38), (126, 80, 48), (92, 56, 33)
HUECO, HUECO_OSC, HUECO_LUZ = (66, 40, 26), (38, 22, 14), (146, 98, 60)
CONTORNO = (30, 16, 10)
ORO, ORO_OSC, ORO_LUZ = (238, 190, 80), (150, 100, 36), (255, 238, 170)
CREMA, CREMA_OSC = (240, 226, 196), (196, 170, 128)
ROJO, ROJO_OSC, ROJO_LUZ = (188, 38, 48), (128, 22, 32), (224, 84, 88)


def pto(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height: im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def rect(im, x0, y0, x1, y1, c):
    """Rectangulo relleno, x1 e y1 inclusive."""
    ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], fill=c + (255,) if len(c) == 3 else c)


def borde(im, x0, y0, x1, y1, c):
    ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], outline=c + (255,))


def tablones(im, x0, y0, x1, y1, base, clara, veta, osc, alto=11, semilla=1):
    """Pared de tablones horizontales con veta y juntas oscuras."""
    r = random.Random(semilla)
    y, k = y0, 0
    while y <= y1:
        h = min(alto, y1 - y + 1)
        col = base if k % 2 == 0 else tuple(int(v * 0.94) for v in base)
        rect(im, x0, y, x1, y + h - 1, col)
        rect(im, x0, y, x1, y, clara)                    # luz arriba del tablon
        if y + h - 1 <= y1: rect(im, x0, y + h - 1, x1, y + h - 1, osc)   # junta
        for _ in range(7):                                # veta
            vx = r.randint(x0, x1 - 8); vy = r.randint(y + 2, max(y + 2, y + h - 3)); n = r.randint(3, 9)
            for i in range(n): pto(im, vx + i, vy, veta)
        # clavos
        for cx in (x0 + 3, x1 - 3):
            if h > 6: pto(im, cx, y + h // 2, osc); pto(im, cx + 1, y + h // 2, clara)
        y += h; k += 1


def hueco(im, x, y):
    """Casillero hundido de 18x18 (el item va en el centro de 16x16)."""
    rect(im, x, y, x + 17, y + 17, HUECO)
    rect(im, x, y, x + 17, y, HUECO_OSC); rect(im, x, y, x, y + 17, HUECO_OSC)           # sombra arriba/izquierda
    rect(im, x + 1, y + 1, x + 17, y + 1, tuple(int(v * 0.7) for v in HUECO))
    rect(im, x, y + 17, x + 17, y + 17, HUECO_LUZ); rect(im, x + 17, y, x + 17, y + 17, HUECO_LUZ)  # luz abajo/derecha
    pto(im, x + 17, y, HUECO_OSC); pto(im, x, y + 17, HUECO_OSC)


def toldo(im):
    """Toldo a rayas rojo y crema con festones, sobre una viga de madera."""
    ax0, ax1, y0, y1 = 7, 168, 6, 13
    # viga
    rect(im, 5, 3, 170, 6, M_BASE); rect(im, 5, 3, 170, 3, M_CLARA); rect(im, 5, 6, 170, 6, M_OSC)
    borde(im, 5, 3, 170, 6, CONTORNO)
    for x in range(9, 170, 14): pto(im, x, 4, M_OSC); pto(im, x, 5, M_OSC)
    n = 18; ancho = (ax1 - ax0 + 1) / n
    for i in range(n):
        xa = int(round(ax0 + i * ancho)); xb = int(round(ax0 + (i + 1) * ancho)) - 1
        base, osc, luz = (ROJO, ROJO_OSC, ROJO_LUZ) if i % 2 == 0 else (CREMA, CREMA_OSC, (255, 250, 232))
        rect(im, xa, y0 + 1, xb, y1, base)
        rect(im, xa, y0 + 1, xb, y0 + 1, luz)
        rect(im, xa, y1 - 2, xb, y1 - 1, osc) if False else None
        # festón redondeado: la tela cuelga 3 filas mas en el medio de cada franja
        mid = (xa + xb) / 2.0; semi = (xb - xa + 1) / 2.0
        for x in range(xa, xb + 1):
            d = abs(x - mid) / semi
            largo = int(round(3 * (1 - d * d) ** 0.5)) if d < 1 else 0
            for k in range(1, largo + 1): pto(im, x, y1 + k, base)
            low = y1 + largo
            pto(im, x, low, osc)                         # borde de abajo, en sombra
            pto(im, x, low + 1, CONTORNO) if largo > 0 else pto(im, x, y1 + 1, CONTORNO)
        pto(im, xa, y0 + 1, tuple(int(v * 0.8) for v in base))
    # sombra sutil del toldo sobre el cartel / panel
    for x in range(ax0, ax1 + 1): pto(im, x, y1 + 5, (96, 58, 32, 255))


def farol(im, cx, y):
    """Farolito colgado de la viga: cadena, tapa, vidrio con luz amarilla y base."""
    for k in range(3): pto(im, cx, y + k, (40, 30, 24))
    rect(im, cx - 2, y + 3, cx + 2, y + 4, (44, 34, 28))
    rect(im, cx - 3, y + 5, cx + 3, y + 11, (255, 214, 96))
    rect(im, cx - 2, y + 6, cx + 2, y + 10, (255, 244, 176))
    pto(im, cx, y + 8, (255, 255, 235))
    for yy in range(y + 5, y + 12): pto(im, cx - 3, yy, (44, 34, 28)); pto(im, cx + 3, yy, (44, 34, 28))
    rect(im, cx - 3, y + 12, cx + 3, y + 12, (44, 34, 28))
    pto(im, cx - 1, y + 13, (44, 34, 28)); pto(im, cx, y + 13, (44, 34, 28)); pto(im, cx + 1, y + 13, (44, 34, 28))
    # resplandor
    halo = Image.new('RGBA', im.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hd.ellipse([cx - 8, y + 2, cx + 8, y + 18], fill=(255, 214, 96, 34))
    hd.ellipse([cx - 5, y + 4, cx + 5, y + 14], fill=(255, 224, 120, 40))
    im.alpha_composite(halo)
    # que el farol quede por delante del resplandor
    rect(im, cx - 3, y + 5, cx + 3, y + 11, (255, 214, 96)); rect(im, cx - 2, y + 6, cx + 2, y + 10, (255, 244, 176)); pto(im, cx, y + 8, (255, 255, 235))
    for yy in range(y + 5, y + 12): pto(im, cx - 3, yy, (44, 34, 28)); pto(im, cx + 3, yy, (44, 34, 28))


def engranaje(im, cx, cy):
    """Engranaje de hierro de 11x11."""
    gris, osc, luz = (122, 126, 136), (52, 54, 62), (190, 194, 204)
    for dx, dy in ((0, -5), (0, 5), (-5, 0), (5, 0), (-4, -4), (4, -4), (-4, 4), (4, 4)):
        rect(im, cx + dx - (1 if dx == 0 else 0), cy + dy - (1 if dy == 0 else 0), cx + dx + (1 if dx == 0 else 0), cy + dy + (1 if dy == 0 else 0), osc)
    d = ImageDraw.Draw(im)
    d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=osc + (255,))
    d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=gris + (255,))
    d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=osc + (255,))
    pto(im, cx - 2, cy - 2, luz); pto(im, cx - 1, cy - 3, luz)


def libro(im, cx, cy):
    """Libro rojo cerrado de 11x10."""
    rect(im, cx - 5, cy - 4, cx + 5, cy + 4, ROJO); rect(im, cx - 5, cy - 4, cx + 5, cy - 4, ROJO_LUZ)
    rect(im, cx - 5, cy + 4, cx + 5, cy + 4, ROJO_OSC); rect(im, cx - 5, cy - 4, cx - 4, cy + 4, ROJO_OSC)
    borde(im, cx - 5, cy - 4, cx + 5, cy + 4, CONTORNO)
    rect(im, cx - 5, cy + 5, cx + 4, cy + 5, CREMA); pto(im, cx + 5, cy + 5, CREMA_OSC)
    for (x, y) in ((cx, cy - 1), (cx - 1, cy), (cx + 1, cy), (cx, cy + 1), (cx, cy)): pto(im, x, y, ORO)


def moneda(im, cx, cy):
    """Moneda de oro de 9x9."""
    d = ImageDraw.Draw(im)
    d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=ORO_OSC + (255,), outline=CONTORNO + (255,))
    d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=ORO + (255,))
    pto(im, cx - 2, cy - 2, ORO_LUZ); pto(im, cx - 1, cy - 3, ORO_LUZ)
    for k in range(-2, 3): pto(im, cx, cy + k, ORO_OSC)


def cartel(im, texto, y0=15, y1=33):
    """Cartel de madera colgado del toldo con el nombre de la ventana."""
    f = ImageFont.truetype(FONT, 8)
    d = ImageDraw.Draw(im); d.fontmode = '1'
    tw = int(d.textlength(texto, font=f))
    ancho = max(84, tw + 24)
    x0 = (W - ancho) // 2; x1 = x0 + ancho - 1
    # sogas
    for sx in (x0 + 6, x1 - 6):
        for yy in range(13, y0 + 1): pto(im, sx, yy, (150, 120, 76)); pto(im, sx + 1, yy, (96, 72, 42))
    rect(im, x0 + 1, y0 + 1, x1 + 1, y1 + 1, (30, 16, 10, 110) if False else (40, 22, 14))
    rect(im, x0, y0, x1, y1, (112, 68, 38))
    tablones(im.crop((x0 + 1, y0 + 1, x1, y1)) if False else im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, (128, 80, 44), (158, 106, 62), (104, 64, 34), (84, 50, 28), alto=9, semilla=5)
    borde(im, x0, y0, x1, y1, CONTORNO)
    borde(im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, (200, 150, 78))
    for (nx, ny) in ((x0 + 3, y0 + 3), (x1 - 3, y0 + 3), (x0 + 3, y1 - 3), (x1 - 3, y1 - 3)):
        pto(im, nx, ny, ORO); pto(im, nx + 1, ny + 1, ORO_OSC)
    d = ImageDraw.Draw(im); d.fontmode = '1'
    tx = (W - tw) // 2; ty = y0 + (y1 - y0 + 1 - 8) // 2
    d.text((tx + 1, ty + 1), texto, font=f, fill=(40, 20, 8, 255))
    d.text((tx, ty), texto, font=f, fill=(255, 236, 168, 255))
    return x0, x1


def generar(filas, texto, botones, salida, grupos=(), adornos=('farol', 'farol')):
    """filas: filas de la ventana; botones: casilleros con marco (fila, columna) con la fila 0 en el banner."""
    H = 114 + 18 * filas
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    # marco exterior y fondo de tablones
    rect(im, 0, 0, W - 1, H - 1, CONTORNO)
    tablones(im, 1, 1, W - 2, H - 2, M_BASE, M_CLARA, M_VETA, M_OSC, alto=12, semilla=len(texto) + filas)
    borde(im, 1, 1, W - 2, H - 2, (96, 58, 32)); borde(im, 2, 2, W - 3, H - 3, M_CLARA)
    # esquinas redondeadas (transparentes)
    for (x, y) in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)): im.putpixel((x, y), (0, 0, 0, 0))
    toldo(im)

    # panel del contenido: madera oscura hundida
    top, bot = 35, 17 + 18 * filas + 1
    rect(im, 6, top, 169, bot, CONTORNO)
    tablones(im, 7, top + 1, 168, bot - 1, M_PANEL, M_PANEL_L, M_PANEL_V, (78, 46, 28), alto=9, semilla=11 + filas)
    rect(im, 7, top + 1, 168, top + 2, (74, 44, 26))                 # sombra que tira el toldo
    rect(im, 6, bot + 1, 169, bot + 1, M_CLARA)                      # luz del borde de abajo
    # grupos de casilleros: una chapa de madera mas clara
    for (r0, c0, r1, c1) in grupos:
        gx0, gy0, gx1, gy1 = 7 + 18 * c0 - 2, 17 + 18 * r0 - 2, 7 + 18 * (c1 + 1) + 1, 17 + 18 * (r1 + 1) + 1
        rect(im, gx0, gy0, gx1, gy1, (150, 98, 56)); borde(im, gx0, gy0, gx1, gy1, CONTORNO)
        rect(im, gx0 + 1, gy0 + 1, gx1 - 1, gy0 + 1, M_CLARA)
        for (nx, ny) in ((gx0 + 2, gy0 + 2), (gx1 - 2, gy0 + 2), (gx0 + 2, gy1 - 2), (gx1 - 2, gy1 - 2)): pto(im, nx, ny, ORO_OSC)
    for (r, c) in botones: hueco(im, 7 + 18 * c, 17 + 18 * r)

    # separador: cinta crema para el "Inventario" que escribe el juego + una vara de madera
    sep = 17 + 18 * filas + 3
    rect(im, 62, sep + 3, 170, sep + 6, M_BASE); rect(im, 62, sep + 3, 170, sep + 3, M_CLARA); rect(im, 62, sep + 6, 170, sep + 6, M_OSC)
    borde(im, 62, sep + 3, 170, sep + 6, CONTORNO)
    rect(im, 5, sep - 1, 62, sep + 10, CREMA); borde(im, 5, sep - 1, 62, sep + 10, CONTORNO)
    rect(im, 6, sep, 61, sep, (255, 250, 232)); rect(im, 6, sep + 9, 61, sep + 9, CREMA_OSC)
    rect(im, 3, sep, 5, sep + 9, M_CLARA); rect(im, 3, sep, 3, sep + 9, CONTORNO); rect(im, 5, sep, 5, sep + 9, M_OSC)   # rollo
    # inventario del jugador, en una bandeja de madera oscura
    base = 84 + 18 * (filas - 3)
    rect(im, 6, base - 2, 169, base + 58 + 18 + 1, CONTORNO)
    tablones(im, 7, base - 1, 168, base + 58 + 18, M_PANEL, M_PANEL_L, M_PANEL_V, (78, 46, 28), alto=9, semilla=3)
    for r in range(3):
        for c in range(9): hueco(im, 7 + 18 * c, base + 18 * r)
    for c in range(9): hueco(im, 7 + 18 * c, base + 58)
    rect(im, 7, base + 55, 168, base + 56, (74, 44, 26))             # division entre inventario y barra rapida

    # cartel y adornos del toldo
    x0, x1 = cartel(im, texto) if texto else (60, 115)
    izq, der = adornos
    for lado, (kind, cx) in (('i', (izq, 17)), ('d', (der, 158))):
        if kind == 'farol': farol(im, cx, 7)
        elif kind == 'engranaje': rect(im, cx, 14, cx, 17, (60, 46, 36)); engranaje(im, cx, 25)
        elif kind == 'libro': rect(im, cx, 14, cx, 17, (60, 46, 36)); libro(im, cx, 24)
        elif kind == 'moneda': rect(im, cx, 14, cx, 17, (60, 46, 36)); moneda(im, cx, 24)
    im.save(salida)
    return im.size


def todos(carpeta):
    tienda = {(1, c) for c in range(1, 8)} | {(2, 0), (2, 4), (2, 6), (2, 8)}
    ajustes = ({(1, 1), (1, 2), (2, 1), (2, 2)} | {(1, 4), (2, 4), (3, 4), (4, 4)} | {(1, 6), (1, 7), (2, 6), (2, 7), (3, 6), (3, 7)} | {(4, 0)})
    grupos = [(1, 1, 2, 2), (1, 4, 4, 4), (1, 6, 2, 7), (3, 6, 3, 7)]
    admins = {(1, c) for c in range(1, 8)} | {(2, c) for c in range(1, 8)} | {(3, 0), (3, 4)}
    historial = {(1, c) for c in range(1, 8)} | {(2, c) for c in range(1, 8)} | {(3, c) for c in range(1, 8)} | {(4, 0)}
    out = {}
    out['tienda_gui.png'] = (3, 'TIENDA', tienda, [], ('farol', 'farol'))
    out['ajustes_gui.png'] = (5, 'AJUSTES', ajustes, grupos, ('farol', 'engranaje'))
    out['admins_gui.png'] = (4, 'ADMINS', admins, [(1, 1, 2, 7)], ('engranaje', 'engranaje'))
    out['historial_gui.png'] = (5, 'HISTORIAL', historial, [(1, 1, 3, 7)], ('libro', 'moneda'))
    res = {}
    for nombre, (filas, texto, bot, gr, ad) in out.items():
        res[nombre] = generar(filas, texto, bot, os.path.join(carpeta, nombre), gr, ad)
    return res


if __name__ == '__main__':
    carpeta = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(carpeta, exist_ok=True)
    print(todos(carpeta))
    if len(sys.argv) > 3 and sys.argv[2] == 'hoja':
        ims = [Image.open(os.path.join(carpeta, n)) for n in ('tienda_gui.png', 'ajustes_gui.png', 'admins_gui.png', 'historial_gui.png')]
        K = 3
        sh = Image.new('RGBA', (sum(i.width * K + 12 for i in ims) + 12, max(i.height for i in ims) * K + 24), (110, 110, 118, 255))
        x = 12
        for i in ims:
            sh.alpha_composite(i.resize((i.width * K, i.height * K), Image.NEAREST), (x, 12)); x += i.width * K + 12
        sh.save(sys.argv[3])
