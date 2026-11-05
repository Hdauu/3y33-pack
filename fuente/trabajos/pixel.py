import sys, os
from PIL import Image, ImageDraw, ImageChops

CW, CH = 44, 64                      # carta en pixeles del inventario (aprox. 2,4 x 3,5 casilleros)
ACC = {'MINERO': (96, 156, 255), 'LEÑADOR': (214, 140, 56), 'GRANJERO': (130, 200, 70), 'CAZADOR': (232, 74, 74), 'PESCADOR': (52, 190, 226)}
ICON = {'MINERO': 'pico', 'LEÑADOR': 'hacha', 'GRANJERO': 'espiga', 'CAZADOR': 'espada', 'PESCADOR': 'pez'}

FONT = {
 'A': ['.X.', 'X.X', 'XXX', 'X.X', 'X.X'], 'C': ['XXX', 'X..', 'X..', 'X..', 'XXX'], 'D': ['XX.', 'X.X', 'X.X', 'X.X', 'XX.'],
 'E': ['XXX', 'X..', 'XX.', 'X..', 'XXX'], 'G': ['XXX', 'X..', 'X.X', 'X.X', 'XXX'], 'I': ['XXX', '.X.', '.X.', '.X.', 'XXX'],
 'J': ['..X', '..X', '..X', 'X.X', '.X.'], 'L': ['X..', 'X..', 'X..', 'X..', 'XXX'], 'M': ['X.X', 'XXX', 'XXX', 'X.X', 'X.X'],
 'N': ['XX.', 'X.X', 'X.X', 'X.X', 'X.X'], 'O': ['.X.', 'X.X', 'X.X', 'X.X', '.X.'], 'P': ['XX.', 'X.X', 'XX.', 'X..', 'X..'],
 'R': ['XX.', 'X.X', 'XX.', 'X.X', 'X.X'], 'S': ['.XX', 'X..', '.X.', '..X', 'XX.'], 'Z': ['XXX', '..X', '.X.', 'X..', 'XXX'],
}

def mask(w, h, fn):
    m = Image.new('L', (w, h), 0); fn(ImageDraw.Draw(m)); return m

def shift(m, dx, dy):
    return ImageChops.offset(m, dx, dy)

def lighten(c, k): return tuple(min(255, int(v + (255 - v) * k)) for v in c)
def darken(c, k): return tuple(int(v * (1 - k)) for v in c)

def pintar(img, m, col):
    """Pinta una pieza con 3 tonos: luz arriba a la izquierda, sombra abajo a la derecha."""
    b = m.point(lambda v: 255 if v > 0 else 0)
    luz = ImageChops.subtract(b, shift(b, 1, 1))     # borde con vacio arriba-izquierda
    luz = ImageChops.multiply(luz, b)
    sombra = ImageChops.subtract(b, shift(b, -1, -1))
    img.paste(col + (255,), mask=b)
    img.paste(lighten(col, .38) + (255,), mask=luz)
    img.paste(darken(col, .35) + (255,), mask=ImageChops.subtract(sombra, luz))

def contorno(img, col=(14, 8, 12)):
    a = img.split()[3].point(lambda v: 255 if v > 0 else 0)
    grande = ImageChops.lighter(ImageChops.lighter(shift(a, 1, 0), shift(a, -1, 0)), ImageChops.lighter(shift(a, 0, 1), shift(a, 0, -1)))
    borde = ImageChops.subtract(grande, a)
    out = Image.new('RGBA', img.size, (0, 0, 0, 0)); out.paste(col + (255,), mask=borde)
    out.alpha_composite(img); return out

STEEL = (156, 170, 186); STEEL_D = (112, 124, 140); WOOD = (150, 98, 52); WOOD_D = (100, 62, 32); GOLD = (232, 188, 72); CREAM = (246, 236, 214)

def icono(nombre):
    s = 24
    im = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    if nombre == 'pico':
        pintar(im, mask(s, s, lambda d: d.line([(5, 19), (13, 9)], fill=255, width=2)), WOOD)
        pintar(im, mask(s, s, lambda d: d.polygon([(1, 9), (3, 5), (8, 2), (14, 2), (19, 5), (20, 9), (17, 7), (13, 6), (9, 6), (5, 8), (3, 11)], fill=255)), STEEL)
        pintar(im, mask(s, s, lambda d: d.polygon([(9, 3), (14, 3), (17, 5), (13, 5)], fill=255)), (200, 214, 230))
    elif nombre == 'hacha':
        pintar(im, mask(s, s, lambda d: d.line([(5, 20), (13, 5)], fill=255, width=2)), WOOD)
        pintar(im, mask(s, s, lambda d: d.polygon([(11, 2), (18, 3), (20, 8), (17, 13), (12, 12), (10, 8)], fill=255)), STEEL)
        pintar(im, mask(s, s, lambda d: d.polygon([(13, 4), (17, 5), (18, 8), (15, 6)], fill=255)), (206, 218, 232))
    elif nombre == 'espiga':
        pintar(im, mask(s, s, lambda d: d.line([(11, 20), (11, 5)], fill=255, width=1)), (118, 150, 52))
        for y in (6, 9, 12, 15):
            for dx in (-3, 2):
                pintar(im, mask(s, s, lambda d, y=y, dx=dx: d.polygon([(11 + dx, y), (11 + dx + 1, y - 2), (11 + dx + 2, y), (11 + dx + 1, y + 2)], fill=255)), GOLD)
        pintar(im, mask(s, s, lambda d: d.polygon([(10, 4), (11, 1), (12, 4), (11, 6)], fill=255)), GOLD)
    elif nombre == 'espada':
        pintar(im, mask(s, s, lambda d: d.polygon([(11, 0), (14, 3), (14, 15), (8, 15), (8, 3)], fill=255)), (206, 216, 228))
        pintar(im, mask(s, s, lambda d: d.polygon([(11, 2), (12, 4), (12, 13), (10, 13), (10, 4)], fill=255)), (236, 242, 250))
        pintar(im, mask(s, s, lambda d: d.rectangle([4, 15, 17, 17], fill=255)), GOLD)
        pintar(im, mask(s, s, lambda d: d.rectangle([9, 18, 12, 21], fill=255)), WOOD)
        pintar(im, mask(s, s, lambda d: d.ellipse([8, 21, 13, 23], fill=255)), GOLD)
    elif nombre == 'pez':
        pintar(im, mask(s, s, lambda d: d.polygon([(15, 10), (20, 5), (20, 16)], fill=255)), (40, 150, 190))
        pintar(im, mask(s, s, lambda d: d.ellipse([2, 6, 17, 15], fill=255)), (92, 202, 232))
        pintar(im, mask(s, s, lambda d: d.polygon([(7, 6), (10, 3), (13, 6)], fill=255)), (40, 150, 190))
        pintar(im, mask(s, s, lambda d: d.ellipse([4, 9, 5, 10], fill=255)), (14, 8, 12))
        pintar(im, mask(s, s, lambda d: d.ellipse([9, 8, 11, 12], fill=255)), (200, 238, 250))
        pintar(im, mask(s, s, lambda d: d.line([(5, 1), (5, 6)], fill=255, width=1)), CREAM)
    return contorno(im)

def texto(img, t, cx, y, col):
    ancho = len(t) * 4 - 1
    x0 = cx - ancho // 2
    for i, ch in enumerate(t):
        if ch == 'Ñ':
            g = FONT['N']; img.putpixel((x0 + i * 4 + 1, y - 1), col + (255,))
        else:
            g = FONT[ch]
        for r, fila in enumerate(g):
            for c, v in enumerate(fila):
                if v == 'X': img.putpixel((x0 + i * 4 + c, y + r), col + (255,))

def carta(nombre, claro=False):
    acc = ACC[nombre]
    img = Image.new('RGBA', (CW, CH), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    fondo_a, fondo_b = ((236, 222, 192), (210, 190, 154)) if claro else ((28, 14, 20), (14, 7, 11))
    tinta = (40, 20, 24) if claro else CREAM
    for y in range(CH):
        t = y / (CH - 1)
        c = tuple(int(fondo_a[i] * (1 - t) + fondo_b[i] * t) for i in range(3))
        d.line([(0, y), (CW, y)], fill=c + (255,))
    # esquinas redondeadas (pixel art)
    for (x, y) in ((0, 0), (CW - 1, 0), (0, CH - 1), (CW - 1, CH - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    # borde: color del trabajo + borde interno oscuro/claro
    d.rectangle([0, 0, CW - 1, CH - 1], outline=acc + (255,))
    for (x, y) in ((0, 0), (CW - 1, 0), (0, CH - 1), (CW - 1, CH - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    d.rectangle([2, 2, CW - 3, CH - 3], outline=darken(acc, .45) + (255,))
    # esquinas ornamentales y gemas rojas 3y33
    rojo = (206, 40, 58)
    for (x, y) in ((4, 4), (CW - 5, 4), (4, CH - 5), (CW - 5, CH - 5)):
        d.point((x, y), fill=rojo + (255,)); d.point((x + (1 if x < CW // 2 else -1), y), fill=lighten(rojo, .4) + (255,))
    # ventana del simbolo: arco oscuro con halo del color
    cx = CW // 2
    arc = (9, 7, CW - 10, 40)
    halo = Image.new('RGBA', (CW, CH), (0, 0, 0, 0)); hd = ImageDraw.Draw(halo)
    hd.rounded_rectangle([arc[0] - 1, arc[1] - 1, arc[2] + 1, arc[3] + 1], radius=8, outline=acc + (255,))
    hd.rounded_rectangle([arc[0], arc[1], arc[2], arc[3]], radius=7, fill=((250, 242, 222) if claro else (10, 5, 8)) + (255,))
    img.alpha_composite(halo)
    # destellos de fondo
    for (x, y) in ((14, 12), (29, 16), (14, 33), (28, 11), (30, 34)):
        img.putpixel((x, y), lighten(acc, .1) + (255,) if not claro else darken(acc, .2) + (255,))
    ic = icono(ICON[nombre]); img.alpha_composite(ic, (cx - ic.width // 2, 12))
    # placa del nombre
    py0, py1 = 46, 56
    d.rectangle([4, py0, CW - 5, py1], fill=(16, 8, 12, 255) if not claro else (58, 30, 34, 255), outline=acc + (255,))
    for (x, y) in ((4, py0), (CW - 5, py0), (4, py1), (CW - 5, py1)): d.point((x, y), fill=rojo + (255,))
    texto(img, nombre, cx, py0 + 3, CREAM)
    # cenefa de abajo
    for x in range(10, CW - 10, 3): d.point((x, CH - 5), fill=darken(acc, .2) + (255,))
    d.point((cx, CH - 5), fill=rojo + (255,))
    return img

if __name__ == '__main__':
    out = sys.argv[1]
    K = 6; pad = 12
    nombres = list(ACC)
    sheet = Image.new('RGBA', (pad + len(nombres) * (CW * K + pad), 2 * (CH * K + pad) + pad), (40, 16, 22, 255))
    for r, claro in enumerate((False, True)):
        for j, n in enumerate(nombres):
            c = carta(n, claro); c.save(os.path.join(os.path.dirname(out), 'pix_%s_%s.png' % ('claro' if claro else 'oscuro', ICON[n])))
            sheet.alpha_composite(c.resize((CW * K, CH * K), Image.NEAREST), (pad + j * (CW * K + pad), pad + r * (CH * K + pad)))
    sheet.save(out); print(sheet.size)
