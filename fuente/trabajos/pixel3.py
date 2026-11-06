import sys, os, math
from PIL import Image, ImageDraw, ImageChops
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixel import FONT, mask, shift, lighten, darken, pintar, contorno, texto

CW, CH = 48, 72
ACC = {'MINERO': (84, 150, 255), 'LEÑADOR': (222, 142, 52), 'GRANJERO': (132, 204, 68), 'CAZADOR': (236, 70, 78), 'PESCADOR': (48, 190, 230)}
ICON = {'MINERO': 'pico', 'LEÑADOR': 'hacha', 'GRANJERO': 'espiga', 'CAZADOR': 'espada', 'PESCADOR': 'pez'}
STEEL = (166, 180, 198); WOOD = (150, 98, 52); GOLD = (236, 190, 80); CREAM = (248, 238, 216); RED = (210, 40, 60)
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

def P(img, m, col): pintar(img, m, col)

def icono(nombre):
    s = 32
    im = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    def M(fn): return mask(s, s, fn)
    if nombre == 'pico':
        P(im, M(lambda d: d.line([(9, 30), (18, 10)], fill=255, width=3)), WOOD)
        for k in range(6): im.putpixel((11 + k * 1, 27 - k * 3 + (0 if k % 2 else 0)), darken(WOOD, .45) + (255,))
        P(im, M(lambda d: d.polygon([(1, 14), (4, 8), (10, 4), (17, 2), (24, 4), (29, 8), (31, 14), (27, 11), (23, 8), (17, 7), (11, 8), (7, 11), (4, 17)], fill=255)), STEEL)
        P(im, M(lambda d: d.polygon([(8, 7), (14, 4), (20, 4), (25, 6), (21, 6), (15, 6), (10, 8)], fill=255)), (214, 226, 240))
        P(im, M(lambda d: d.rectangle([14, 5, 20, 12], fill=255)), (96, 106, 122))
        P(im, M(lambda d: d.rectangle([15, 7, 19, 8], fill=255)), (200, 70, 60))
    elif nombre == 'hacha':
        P(im, M(lambda d: d.line([(4, 30), (16, 9)], fill=255, width=3)), WOOD)
        for k in range(5): im.putpixel((7 + k, 27 - k * 4), darken(WOOD, .45) + (255,))
        P(im, M(lambda d: d.polygon([(6, 7), (12, 5), (14, 12), (8, 13)], fill=255)), (116, 126, 142))
        P(im, M(lambda d: d.polygon([(12, 4), (19, 3), (23, 1), (27, 3), (29, 8), (29, 14), (27, 19), (24, 22), (21, 21), (22, 17), (19, 14), (15, 13)], fill=255)), STEEL)
        P(im, M(lambda d: d.polygon([(24, 3), (27, 5), (28, 9), (28, 14), (26, 18), (26, 12), (25, 7)], fill=255)), (232, 240, 250))
        P(im, M(lambda d: d.polygon([(15, 6), (20, 5), (21, 11), (16, 11)], fill=255)), (96, 106, 122))
        P(im, M(lambda d: d.rectangle([13, 12, 17, 14], fill=255)), (160, 50, 52))
    elif nombre == 'espiga':
        for sx, top in ((8, 7), (16, 3), (24, 7)):
            P(im, M(lambda d, sx=sx, top=top: d.line([(sx, 28), (sx, top + 2)], fill=255, width=1)), (120, 152, 52))
            for i in range(4):
                y = top + 3 + i * 4
                for dx in (-2, 1):
                    P(im, M(lambda d, y=y, dx=dx, sx=sx: d.polygon([(sx + dx, y + 2), (sx + dx + 1, y - 1), (sx + dx + 2, y + 2), (sx + dx + 1, y + 3)], fill=255)), GOLD)
            P(im, M(lambda d, sx=sx, top=top: d.polygon([(sx - 1, top + 3), (sx, top - 2), (sx + 1, top + 3), (sx, top + 4)], fill=255)), GOLD)
        P(im, M(lambda d: d.rectangle([6, 23, 26, 25], fill=255)), (200, 50, 62))
        P(im, M(lambda d: d.polygon([(26, 25), (30, 29), (28, 31), (25, 26)], fill=255)), (170, 36, 50))
        P(im, M(lambda d: d.polygon([(6, 25), (2, 29), (4, 31), (7, 26)], fill=255)), (170, 36, 50))
    elif nombre == 'azada':
        # azada: mismo mango (y misma inclinacion) que el hacha; cabeza con un anillo arriba y una hoja corta y curva que sale de costado
        P(im, M(lambda d: d.line([(4, 30), (16, 10)], fill=255, width=3)), WOOD)
        for k in range(5): im.putpixel((7 + k, 27 - k * 4), darken(WOOD, .45) + (255,))
        for r in ([13, 2, 18, 3], [13, 8, 18, 9], [13, 2, 14, 9], [17, 2, 18, 9]):   # anillo hueco
            P(im, M(lambda d, r=r: d.rectangle(r, fill=255)), (116, 126, 142))
        P(im, M(lambda d: d.polygon([(18, 3), (24, 1), (29, 3), (30, 8), (28, 13), (27, 9), (23, 7), (18, 8)], fill=255)), STEEL)
        P(im, M(lambda d: d.polygon([(19, 3), (24, 2), (28, 4), (24, 4)], fill=255)), (232, 240, 250))
        P(im, M(lambda d: d.polygon([(27, 6), (29, 5), (29, 9), (28, 11)], fill=255)), (232, 240, 250))
        P(im, M(lambda d: d.rectangle([14, 10, 18, 12], fill=255)), (160, 50, 52))
    elif nombre in ('azada_b', 'azada_c', 'azada_d'):
        P(im, M(lambda d: d.line([(4, 30), (16, 10)], fill=255, width=3)), WOOD)
        for k in range(5): im.putpixel((7 + k, 27 - k * 4), darken(WOOD, .45) + (255,))
        LUZ = (232, 240, 250); SOM = (116, 126, 142)
        if nombre == 'azada_b':     # hoja plana en punta que cuelga (azada de deshierbe)
            P(im, M(lambda d: d.polygon([(13, 9), (16, 5), (22, 3), (29, 4), (30, 8), (23, 10)], fill=255)), STEEL)
            P(im, M(lambda d: d.polygon([(22, 8), (30, 7), (29, 14), (27, 23), (24, 14)], fill=255)), STEEL)
            P(im, M(lambda d: d.polygon([(26, 9), (29, 9), (28, 15), (27, 20)], fill=255)), LUZ)
            P(im, M(lambda d: d.polygon([(17, 5), (22, 4), (28, 5), (22, 6)], fill=255)), LUZ)
            P(im, M(lambda d: d.polygon([(22, 9), (24, 9), (25, 14)], fill=255)), SOM)
        elif nombre == 'azada_c':   # cultivador de tres puas
            P(im, M(lambda d: d.polygon([(13, 9), (16, 5), (29, 3), (30, 7), (18, 11)], fill=255)), STEEL)
            for x, y2 in ((19, 19), (23, 21), (27, 19)):
                P(im, M(lambda d, x=x, y2=y2: d.polygon([(x, 8), (x + 3, 7), (x + 3, y2 - 2), (x + 1, y2 + 2), (x - 1, y2 - 2)], fill=255)), STEEL)
                P(im, M(lambda d, x=x, y2=y2: d.polygon([(x + 2, 9), (x + 3, 9), (x + 3, y2 - 3)], fill=255)), LUZ)
            P(im, M(lambda d: d.polygon([(16, 5), (28, 4), (28, 5), (18, 7)], fill=255)), LUZ)
        else:                       # azada clasica: barra y una hoja corta que baja en la punta
            P(im, M(lambda d: d.polygon([(13, 9), (16, 5), (28, 3), (30, 5), (30, 14), (26, 14), (26, 8), (18, 10)], fill=255)), STEEL)
            P(im, M(lambda d: d.polygon([(16, 5), (27, 4), (29, 6), (22, 6)], fill=255)), LUZ)
            P(im, M(lambda d: d.polygon([(28, 7), (29, 7), (29, 13), (28, 13)], fill=255)), LUZ)
        P(im, M(lambda d: d.rectangle([14, 10, 18, 12], fill=255)), (160, 50, 52))
    elif nombre in ('azada_v1', 'azada_v2', 'azada_v3', 'azada_v4'):
        # azadas en vertical: mango recto y centrado, con muescas; cuatro cabezas distintas para elegir
        LUZ = (232, 240, 250); SOM = (116, 126, 142)
        ox = {'azada_v1': -4, 'azada_v2': -4, 'azada_v3': 0, 'azada_v4': 0}[nombre]
        X = lambda pts: [(x + ox, y) for x, y in pts]
        hx = 16 + ox
        P(im, M(lambda d: d.rectangle([hx - 1, 9, hx + 1, 30], fill=255)), WOOD)
        for y in (27, 23, 19, 15): im.putpixel((hx + 1, y), darken(WOOD, .45) + (255,)); im.putpixel((hx + 1, y - 1), darken(WOOD, .45) + (255,))
        for y in range(10, 30): im.putpixel((hx - 1, y), lighten(WOOD, .25) + (255,))
        if nombre == 'azada_v1':     # anillo arriba y hoja curva que sale de costado
            for r in ([10, 1, 15, 2], [10, 8, 15, 9], [10, 1, 11, 9], [14, 1, 15, 9]):
                P(im, M(lambda d, r=r: d.rectangle(X([(r[0] + 0, r[1]), (r[2] + 0, r[3])]), fill=255)), SOM)
            P(im, M(lambda d: d.polygon(X([(15, 2), (21, 0), (27, 2), (29, 6), (27, 12), (26, 8), (22, 6), (15, 8)]), fill=255)), STEEL)
            P(im, M(lambda d: d.polygon(X([(16, 2), (21, 1), (26, 3), (21, 3)]), fill=255)), LUZ)
            P(im, M(lambda d: d.polygon(X([(26, 6), (28, 6), (27, 10)]), fill=255)), LUZ)
            P(im, M(lambda d: d.rectangle(X([(11, 9), (15, 11)]), fill=255)), (160, 50, 52))
        elif nombre == 'azada_v2':   # barra con una hoja que baja en la punta
            P(im, M(lambda d: d.polygon(X([(12, 10), (14, 5), (28, 4), (30, 6), (30, 15), (26, 15), (26, 9), (19, 10)]), fill=255)), STEEL)
            P(im, M(lambda d: d.polygon(X([(15, 5), (27, 5), (29, 7), (20, 7)]), fill=255)), LUZ)
            P(im, M(lambda d: d.polygon(X([(28, 8), (29, 8), (29, 14), (28, 14)]), fill=255)), LUZ)
            P(im, M(lambda d: d.rectangle(X([(13, 10), (19, 12)]), fill=255)), (160, 50, 52))
        elif nombre == 'azada_v3':   # hoja en corazon, de frente
            P(im, M(lambda d: d.polygon(X([(8, 3), (24, 3), (27, 6), (23, 12), (16, 17), (9, 12), (5, 6)]), fill=255)), STEEL)
            P(im, M(lambda d: d.polygon(X([(9, 4), (23, 4), (25, 6), (7, 6)]), fill=255)), LUZ)
            P(im, M(lambda d: d.polygon(X([(17, 8), (23, 7), (22, 11), (17, 15)]), fill=255)), SOM)
            P(im, M(lambda d: d.rectangle(X([(13, 16), (19, 18)]), fill=255)), (160, 50, 52))
        else:                        # azadon: punta de un lado y hoja plana del otro
            P(im, M(lambda d: d.polygon(X([(3, 8), (8, 3), (15, 3), (15, 9), (8, 8)]), fill=255)), STEEL)
            P(im, M(lambda d: d.polygon(X([(17, 3), (23, 3), (29, 5), (29, 13), (23, 11), (17, 9)]), fill=255)), STEEL)
            P(im, M(lambda d: d.rectangle(X([(14, 3), (18, 10)]), fill=255)), SOM)
            P(im, M(lambda d: d.polygon(X([(18, 4), (23, 4), (28, 6), (22, 6)]), fill=255)), LUZ)
            P(im, M(lambda d: d.polygon(X([(8, 4), (14, 4), (14, 5), (7, 5)]), fill=255)), LUZ)
            P(im, M(lambda d: d.rectangle(X([(14, 10), (18, 12)]), fill=255)), (160, 50, 52))
    elif nombre in ('pico_v', 'hacha_v'):
        # herramientas en vertical (mango recto y centrado, con muescas), a juego con la azada vertical
        LUZ = (232, 240, 250); SOM = (96, 106, 122)
        ox = 0 if nombre == 'pico_v' else -4
        X = lambda pts: [(x + ox, y) for x, y in pts]
        hx = 16 + ox
        P(im, M(lambda d: d.rectangle([hx - 1, 9, hx + 1, 30], fill=255)), WOOD)
        for y in (27, 23, 19, 15): im.putpixel((hx + 1, y), darken(WOOD, .45) + (255,)); im.putpixel((hx + 1, y - 1), darken(WOOD, .45) + (255,))
        for y in range(10, 30): im.putpixel((hx - 1, y), lighten(WOOD, .25) + (255,))
        if nombre == 'pico_v':
            P(im, M(lambda d: d.polygon([(3, 14), (6, 8), (10, 4), (16, 2), (22, 4), (26, 8), (29, 14), (26, 11), (22, 8), (16, 7), (10, 8), (6, 11)], fill=255)), STEEL)
            P(im, M(lambda d: d.polygon([(8, 8), (11, 5), (16, 3), (21, 5), (24, 8), (21, 6), (16, 5), (11, 6)], fill=255)), (214, 226, 240))
            P(im, M(lambda d: d.rectangle([13, 4, 19, 12], fill=255)), SOM)
            P(im, M(lambda d: d.rectangle([14, 7, 18, 8], fill=255)), (200, 70, 60))
        else:
            P(im, M(lambda d: d.polygon(X([(9, 5), (15, 4), (16, 12), (10, 12)]), fill=255)), (116, 126, 142))
            P(im, M(lambda d: d.polygon(X([(15, 3), (22, 1), (27, 3), (30, 7), (30, 13), (28, 18), (25, 22), (22, 21), (23, 16), (20, 13), (16, 12)]), fill=255)), STEEL)
            P(im, M(lambda d: d.polygon(X([(26, 4), (28, 7), (29, 12), (27, 17), (26, 11), (25, 7)]), fill=255)), LUZ)
            P(im, M(lambda d: d.polygon(X([(15, 5), (20, 4), (21, 11), (16, 11)]), fill=255)), SOM)
            P(im, M(lambda d: d.rectangle(X([(13, 12), (19, 14)]), fill=255)), (160, 50, 52))
    elif nombre in ('hacha_v2', 'hacha_v3'):
        LUZ = (232, 240, 250); SOM = (96, 106, 122)
        ox = -4 if nombre == 'hacha_v2' else 0
        X = lambda pts: [(x + ox, y) for x, y in pts]
        hx = 16 + ox
        P(im, M(lambda d: d.rectangle([hx - 1, 9, hx + 1, 30], fill=255)), WOOD)
        for y in (27, 23, 19, 15): im.putpixel((hx + 1, y), darken(WOOD, .45) + (255,)); im.putpixel((hx + 1, y - 1), darken(WOOD, .45) + (255,))
        for y in range(10, 30): im.putpixel((hx - 1, y), lighten(WOOD, .25) + (255,))
        if nombre == 'hacha_v2':     # hacha de una hoja: nuca recta pegada al mango y filo curvo hacia la derecha
            P(im, M(lambda d: d.polygon(X([(12, 4), (18, 2), (24, 1), (28, 3), (30, 8), (29, 15), (26, 20), (22, 20), (24, 15), (22, 11), (18, 10), (12, 10)]), fill=255)), STEEL)
            P(im, M(lambda d: d.polygon(X([(27, 4), (29, 8), (28, 14), (26, 18), (26, 12), (25, 7)]), fill=255)), LUZ)
            P(im, M(lambda d: d.polygon(X([(13, 4), (20, 3), (24, 3), (19, 5), (13, 6)]), fill=255)), LUZ)
            P(im, M(lambda d: d.polygon(X([(13, 7), (19, 7), (19, 10), (13, 10)]), fill=255)), SOM)
            P(im, M(lambda d: d.rectangle(X([(13, 10), (19, 12)]), fill=255)), (160, 50, 52))
        else:                        # hacha de dos hojas, simetrica, a juego con el pico
            for lado in (0, 1):
                f = (lambda x: x) if lado == 0 else (lambda x: 32 - x)
                pts = [(14, 5), (9, 3), (4, 4), (2, 9), (3, 15), (6, 19), (10, 20), (9, 15), (11, 11), (14, 10)]
                P(im, M(lambda d, f=f, pts=pts: d.polygon([(f(x), y) for x, y in pts], fill=255)), STEEL)
                lz = [(4, 6), (3, 9), (4, 14), (6, 17), (6, 11), (5, 8)]
                P(im, M(lambda d, f=f, lz=lz: d.polygon([(f(x), y) for x, y in lz], fill=255)), LUZ)
            P(im, M(lambda d: d.rectangle([13, 3, 19, 12], fill=255)), SOM)
            P(im, M(lambda d: d.rectangle([14, 12, 18, 14], fill=255)), (160, 50, 52))
    elif nombre == 'espada':
        P(im, M(lambda d: d.polygon([(16, 0), (20, 4), (20, 21), (12, 21), (12, 4)], fill=255)), (206, 218, 232))
        P(im, M(lambda d: d.polygon([(16, 3), (17, 5), (17, 19), (15, 19), (15, 5)], fill=255)), (240, 246, 252))
        P(im, M(lambda d: d.polygon([(4, 21), (8, 23), (24, 23), (28, 21), (28, 25), (24, 26), (8, 26), (4, 25)], fill=255)), GOLD)
        P(im, M(lambda d: d.ellipse([14, 22, 18, 26], fill=255)), RED)
        P(im, M(lambda d: d.rectangle([14, 26, 18, 30], fill=255)), (110, 62, 38))
        for y in (27, 29): im.putpixel((15, y), darken((110, 62, 38), .5) + (255,)); im.putpixel((17, y), darken((110, 62, 38), .5) + (255,))
        P(im, M(lambda d: d.ellipse([13, 29, 19, 32], fill=255)), GOLD)
    elif nombre == 'pez':
        P(im, M(lambda d: d.polygon([(21, 14), (29, 7), (29, 21), (22, 18)], fill=255)), (30, 130, 178))
        P(im, M(lambda d: d.ellipse([3, 8, 26, 24], fill=255)), (86, 200, 238))
        P(im, M(lambda d: d.polygon([(11, 9), (15, 3), (21, 5), (20, 10)], fill=255)), (30, 130, 178))
        P(im, M(lambda d: d.polygon([(11, 22), (16, 27), (20, 22)], fill=255)), (30, 130, 178))
        P(im, M(lambda d: d.ellipse([6, 14, 22, 24], fill=255)), (214, 242, 252))
        for (x, y) in ((11, 13), (14, 12), (17, 13), (13, 16), (16, 16), (19, 16)): im.putpixel((x, y), (30, 130, 178, 255))
        P(im, M(lambda d: d.ellipse([6, 13, 9, 16], fill=255)), (250, 250, 250))
        im.putpixel((7, 14), (14, 8, 12, 255)); im.putpixel((8, 14), (14, 8, 12, 255))
        # burbujitas en lugar de la linea de pesca
        for (x, y, r) in ((2, 6, 1), (4, 3, 1)):
            P(im, M(lambda d, x=x, y=y, r=r: d.ellipse([x - r, y - r, x + r, y + r], fill=255)), (200, 240, 252))
    if nombre == 'pez_v':
        return icono('pez').rotate(-90, resample=Image.NEAREST)
    return contorno(im)

def fondo(acc, glow, rays, tono, tinte=0.0):
    img = Image.new('RGBA', (CW, CH), (0, 0, 0, 255)); px = img.load()
    cx, cy = CW / 2, 34
    top, bot = tono
    for y in range(CH):
        t = y / (CH - 1)
        for x in range(CW):
            c = [int(top[i] * (1 - t) + bot[i] * t) for i in range(3)]
            c = [int(c[i] * (1 - tinte) + acc[i] * tinte * (1 - t * .6)) for i in range(3)]
            r = math.hypot(x - cx, y - cy)
            g = max(0.0, 1 - r / 28.0)
            ang = math.atan2(y - cy, x - cx)
            rayo = 0.18 if (rays and int((ang + math.pi) / (math.pi / 12)) % 2 == 0 and r < 30) else 0
            v = (g * 0.55 * glow + rayo * g) * 4
            base_l = int(v); frac = v - base_l
            if frac * 16 > BAYER[y % 4][x % 4]: base_l += 1
            k = min(base_l, 4) / 4 * 0.5
            c = [int(c[i] * (1 - k) + acc[i] * k) for i in range(3)]
            px[x, y] = tuple(c) + (255,)
    return img

ESTILOS = {
 'oro_rayos': dict(glow=1.6, rays=True, ring=None, esquinas=1, remate=True, doble=False, cinta='punta', estrellas=8, tono=((30, 10, 18), (10, 4, 8)), borde='oro', tinte=0.16),
 'oro_rayos_anillo': dict(glow=1.5, rays=True, ring='puntos', esquinas=1, remate=True, doble=False, cinta='punta', estrellas=6, tono=((30, 10, 18), (10, 4, 8)), borde='oro', tinte=0.16),
 'oro':    dict(glow=1.0, rays=True, ring='puntos', esquinas=1, remate=True, doble=False, cinta='punta', estrellas=0, tono=((36, 14, 22), (12, 6, 10)), borde='oro'),
 'rayos':  dict(glow=1.6, rays=True, ring=None, esquinas=1, remate=True, doble=False, cinta='punta', estrellas=8, tono=((30, 10, 18), (10, 4, 8)), borde='acento', tinte=0.16),
 'vitral': dict(glow=0.9, rays=False, ring=None, esquinas=1, remate=True, doble=False, cinta='recta', estrellas=0, tono=((40, 16, 24), (14, 6, 12)), borde='acento', ventana=True, tinte=0.1),
 'limpia':   dict(glow=0.7, rays=False, ring=None, esquinas=0, remate=False, doble=False, cinta='recta', estrellas=0, tono=((30, 12, 20), (12, 5, 10)), borde='acento'),
 'equilibrada': dict(glow=1.0, rays=True, ring='puntos', esquinas=1, remate=True, doble=False, cinta='punta', estrellas=0, tono=((36, 14, 22), (12, 6, 10)), borde='acento'),
 'recargada': dict(glow=1.3, rays=True, ring='doble', esquinas=2, remate=True, doble=True, cinta='punta', estrellas=14, tono=((44, 14, 26), (14, 5, 12)), borde='oro'),
}

def carta(nombre, estilo='equilibrada'):
    o = ESTILOS[estilo]
    acc = ACC[nombre]
    img = fondo(acc, o['glow'], o['rays'], o['tono'], o.get('tinte', 0.0)); d = ImageDraw.Draw(img)
    cx = CW // 2
    if o.get('ventana'):
        ov = Image.new('RGBA', (CW, CH), (0, 0, 0, 0)); od = ImageDraw.Draw(ov)
        od.rounded_rectangle([cx - 19, 13, cx + 19, 55], radius=8, fill=(8, 3, 6, 150), outline=acc + (255,))
        od.rounded_rectangle([cx - 17, 15, cx + 17, 53], radius=6, outline=darken(GOLD, .3) + (255,))
        img.alpha_composite(ov); d = ImageDraw.Draw(img)
    if o['ring']:
        for rad in ((20,) if o['ring'] == 'puntos' else (20, 23)):
            n = 48 if rad == 20 else 64
            for k in range(n):
                a = k / n * 2 * math.pi
                x = int(round(cx + math.cos(a) * rad - .5)); y = int(round(34 + math.sin(a) * rad - .5))
                if (k % 2 == 0 or o['ring'] == 'doble' and rad == 23) and 0 < x < CW - 1 and 0 < y < CH - 1: d.point((x, y), fill=lighten(acc, .25) + (255,))
        for (dx, dy) in ((0, -22), (22, 0), (0, 22), (-22, 0)):
            x, y = cx + dx, 34 + dy
            d.polygon([(x, y - 2), (x + 2, y), (x, y + 2), (x - 2, y)], fill=GOLD + (255,)); d.point((x, y), fill=RED + (255,))
    if o['estrellas']:
        import random
        rnd = random.Random(hash(nombre) % 1000 + 7)
        for _ in range(o['estrellas']):
            x = rnd.randint(6, CW - 7); y = rnd.randint(8, 54)
            if math.hypot(x - cx, y - 34) > 15:
                d.point((x, y), fill=lighten(acc, .5) + (255,))
                if rnd.random() < .4:
                    for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)): d.point((x + ddx, y + ddy), fill=darken(acc, .3) + (255,))
    ic = icono(ICON[nombre]); img.alpha_composite(ic, (cx - ic.width // 2, 34 - ic.height // 2))
    # marco
    d.rectangle([0, 0, CW - 1, CH - 1], outline=(10, 4, 8, 255))
    cb = acc if o['borde'] == 'acento' else GOLD
    d.rectangle([1, 1, CW - 2, CH - 2], outline=cb + (255,))
    d.rectangle([2, 2, CW - 3, CH - 3], outline=darken(cb, .5) + (255,))
    if o['esquinas'] or o['doble']:
        d.rectangle([4, 4, CW - 5, CH - 5], outline=darken(GOLD, .35) + (255,))
    if o['doble']:
        d.rectangle([6, 6, CW - 7, CH - 7], outline=darken(acc, .4) + (255,))
    for (x, y) in ((0, 0), (CW - 1, 0), (0, CH - 1), (CW - 1, CH - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    if o['esquinas']:
        L = 6 if o['esquinas'] == 1 else 9
        for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            ox = 4 if sx > 0 else CW - 5; oy = 4 if sy > 0 else CH - 5
            for k in range(1, L + 1): d.point((ox + sx * k, oy), fill=GOLD + (255,)); d.point((ox, oy + sy * k), fill=GOLD + (255,))
            d.point((ox + sx, oy + sy), fill=GOLD + (255,)); d.point((ox + sx * 2, oy + sy * 2), fill=RED + (255,))
            if o['esquinas'] == 2:
                d.point((ox + sx * 3, oy + sy * 3), fill=lighten(GOLD, .4) + (255,))
                d.point((ox + sx * L, oy + sy), fill=GOLD + (255,)); d.point((ox + sx, oy + sy * L), fill=GOLD + (255,))
    if o['remate']:
        d.polygon([(cx, 1), (cx + 4, 5), (cx, 9), (cx - 4, 5)], fill=(18, 8, 12, 255), outline=GOLD + (255,))
        d.point((cx, 5), fill=RED + (255,)); d.point((cx, 4), fill=lighten(RED, .5) + (255,))
    # cinta del nombre
    y0, y1 = 57, 67
    if o['cinta'] == 'punta':
        rib = [(2, y0 + 5), (5, y0), (CW - 6, y0), (CW - 3, y0 + 5), (CW - 6, y1), (5, y1)]
        d.polygon(rib, fill=(20, 8, 12, 255), outline=GOLD + (255,))
        d.line([(7, y0 + 1), (CW - 8, y0 + 1)], fill=darken(acc, .3) + (255,)); d.line([(7, y1 - 1), (CW - 8, y1 - 1)], fill=darken(acc, .3) + (255,))
        for (x, y) in ((4, y0 + 5), (CW - 5, y0 + 5)): img.putpixel((x, y), RED + (255,))
    else:
        d.rectangle([3, y0, CW - 4, y1], fill=(20, 8, 12, 255), outline=acc + (255,))
        d.line([(3, y0 + 1), (CW - 4, y0 + 1)], fill=darken(acc, .45) + (255,))
    texto(img, nombre, cx + 1, y0 + 3, (0, 0, 0)); texto(img, nombre, cx, y0 + 3, CREAM)
    return img

if __name__ == '__main__':
    from PIL import ImageFont
    out = sys.argv[1]; K = 4; pad = 12; lab = 20
    nombres = list(ACC); estilos = ['oro_rayos', 'oro_rayos_anillo']
    sheet = Image.new('RGBA', (pad + len(nombres) * (CW * K + pad), len(estilos) * (CH * K + pad + lab) + pad), (30, 12, 18, 255))
    d = ImageDraw.Draw(sheet); f = ImageFont.truetype('/home/claude/3y33-pack/fuente/fuentes/PressStart2P.ttf', 10)
    for i, e in enumerate(estilos):
        y0 = pad + i * (CH * K + pad + lab)
        d.text((pad, y0 + 2), '%d  %s' % (i + 1, e.upper()), font=f, fill=GOLD + (255,))
        for j, n in enumerate(nombres):
            c = carta(n, e); c.save(os.path.join(os.path.dirname(out), 'v%d_%s.png' % (i + 1, ICON[n])))
            sheet.alpha_composite(c.resize((CW * K, CH * K), Image.NEAREST), (pad + j * (CW * K + pad), y0 + lab))
    sheet.save(out); print(sheet.size)
