"""Cartas de las misiones: 34x54 px (2 casilleros de ancho x 3 de alto), como las de Masivo pero en nuestra paleta roja y dorada.
Cada carta es un item "oversized" (escala 3 sobre un modelo de 34/3 x 18 unidades) que cuelga del casillero de la fila 1 de su columna;
el item de verdad (lo que piden entregar) va en el casillero de la fila 2 y se dibuja encima, dentro de la ventanita central de la carta.
Uso: python3 misiones_cartas.py hoja <salida.png>  |  python3 misiones_cartas.py <carpeta_pack>"""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from pixel import FONT as _F, lighten, darken, ACC

FONT = dict(_F)
FONT['B'] = ['XX.', 'X.X', 'XX.', 'X.X', 'XX.']
FONT['H'] = ['X.X', 'X.X', 'XXX', 'X.X', 'X.X']
FONT['U'] = ['X.X', 'X.X', 'X.X', 'X.X', 'XXX']
FONT['T'] = ['XXX', '.X.', '.X.', '.X.', '.X.']

CW, CH = 34, 54
GOLD = (236, 190, 80)
RED = (210, 40, 60)
CREAM = (248, 238, 216)
VERDE = (110, 205, 100)
GRIS = (120, 120, 128)
FONDO = (22, 9, 13)

# color de la gema segun la rareza (los mismos del menu) y segun el trabajo (para la global)
GEMAS = {
    'comun': (200, 200, 205), 'rara': (85, 168, 255), 'epica': (180, 85, 255), 'legendaria': (255, 170, 0),
    'minero': ACC['MINERO'], 'lenador': ACC['LEÑADOR'], 'granjero': ACC['GRANJERO'], 'cazador': ACC['CAZADOR'], 'pescador': ACC['PESCADOR'],
    'fuego': (255, 120, 40), 'neutro': GOLD,
}
RAREZAS = ['comun', 'rara', 'epica', 'legendaria']
TRABAJOS = ['minero', 'lenador', 'granjero', 'cazador', 'pescador']

# columna de cada carta en la primera / segunda pagina (el centro de la carta queda sobre el centro de ese casillero)
COLUMNA = {'diaria': 1, 'elegida': 4, 'semanal': 7, 'global': 2, 'racha': 5}
COLUMNAS_ELEGIR = [1, 3, 5, 7]


def texto(img, t, cx, y, col):
    ancho = len(t) * 4 - 1
    x0 = cx - ancho // 2
    for i, ch in enumerate(t):
        g = FONT[ch]
        for r, fila in enumerate(g):
            for c, v in enumerate(fila):
                if v == 'X': img.putpixel((x0 + i * 4 + c, y + r), col + (255,))


def gris(c):
    g = int(0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    return (g, g, g)


def carta(rotulo, gema, estado='normal'):
    """rotulo: texto de la cinta. gema: color. estado: normal | completa | perdida | lista."""
    marco = GOLD
    if estado == 'completa': marco, gema = VERDE, VERDE
    elif estado == 'perdida': marco, gema = GRIS, (90, 90, 96)
    elif estado == 'lista': marco = (255, 226, 120)
    fondo = FONDO if estado != 'perdida' else (20, 20, 23)
    img = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # fondo con un degradado suave de arriba hacia abajo
    for y in range(CH):
        k = y / (CH - 1)
        c = tuple(int(fondo[i] * (1.25 - 0.5 * k)) for i in range(3))
        d.line([(0, y), (CW - 1, y)], fill=c + (255,))
    # marco: negro, dorado, dorado oscuro
    d.rectangle([0, 0, CW - 1, CH - 1], outline=(10, 4, 8, 255))
    d.rectangle([1, 1, CW - 2, CH - 2], outline=marco + (255,))
    d.rectangle([2, 2, CW - 3, CH - 3], outline=darken(marco, .5) + (255,))
    for (x, y) in ((0, 0), (CW - 1, 0), (0, CH - 1), (CW - 1, CH - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    # esquinas con adorno
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        ox = 4 if sx > 0 else CW - 5; oy = 4 if sy > 0 else CH - 5
        for k in range(1, 4): d.point((ox + sx * k, oy), fill=marco + (255,)); d.point((ox, oy + sy * k), fill=marco + (255,))
        d.point((ox + sx, oy + sy), fill=(RED if estado != 'perdida' else GRIS) + (255,))
    cx = CW // 2
    # remate de arriba con la gema
    d.polygon([(cx - 1, 1), (cx, 1), (cx + 4, 5), (cx, 9), (cx - 1, 9), (cx - 5, 5)], fill=(18, 8, 12, 255), outline=marco + (255,))
    d.polygon([(cx - 1, 3), (cx, 3), (cx + 2, 5), (cx, 7), (cx - 1, 7), (cx - 3, 5)], fill=gema + (255,))
    d.point((cx - 1, 4), fill=lighten(gema, .6) + (255,))
    # rayitas a los lados del remate
    for k in range(5, 12): d.point((cx - 5 - k + 3 - 0, 5), fill=darken(marco, .45) + (255,)) if cx - 5 - k + 3 > 3 else None
    for k in range(5, 12): d.point((cx + 4 + k - 3, 5), fill=darken(marco, .45) + (255,)) if cx + 4 + k - 3 < CW - 4 else None
    # ventana del medio: ahi se dibuja el item de verdad (16x16, de (9,19) a (24,34))
    d.rectangle([7, 17, 26, 36], fill=(10, 4, 8, 255), outline=darken(marco, .35) + (255,))
    d.rectangle([8, 18, 25, 35], outline=marco + (255,))
    for (x, y) in ((8, 18), (25, 18), (8, 35), (25, 35)): img.putpixel((x, y), (RED if estado != 'perdida' else GRIS) + (255,))
    # estrellitas
    for (x, y) in ((6, 13), (28, 12), (5, 40), (29, 41)): img.putpixel((x, y), lighten(gema, .4) + (255,))
    # cinta con el rotulo
    y0, y1 = 39, 49
    rib = [(3, y0 + 5), (5, y0), (CW - 6, y0), (CW - 4, y0 + 5), (CW - 6, y1), (5, y1)]
    d.polygon(rib, fill=(20, 8, 12, 255), outline=marco + (255,))
    texto(img, rotulo, cx + 1, y0 + 3, (0, 0, 0))
    texto(img, rotulo, cx, y0 + 3, CREAM if estado != 'perdida' else (170, 170, 175))
    # gema de abajo
    d.polygon([(cx - 1, 50), (cx, 50), (cx + 2, 51), (cx, 52), (cx - 1, 52), (cx - 3, 51)], fill=gema + (255,))
    img.putpixel((cx - 1, 51), lighten(gema, .6) + (255,))
    return img


def variantes():
    """Todas las cartas: clave -> imagen."""
    out = {}
    for tipo, rot in (('diaria', 'DIARIA'), ('elegida', 'DIARIA'), ('semanal', 'SEMANA')):
        for g in RAREZAS: out['mision_%s_%s_normal' % (tipo, g)] = carta(rot, GEMAS[g])
        out['mision_%s_x_completa' % tipo] = carta('HECHA', VERDE, 'completa')
        out['mision_%s_x_perdida' % tipo] = carta('CAIDA', GRIS, 'perdida')
    out['mision_elegir_x_normal'] = carta('ELEGIR', GEMAS['neutro'])
    for g in RAREZAS: out['elegir_%s' % g] = carta('ELEGIR', GEMAS[g])
    for t in TRABAJOS:
        out['mision_global_%s_normal' % t] = carta('GLOBAL', GEMAS[t])
        out['mision_global_%s_lista' % t] = carta('COBRAR', GEMAS[t], 'lista')
    out['mision_global_x_completa'] = carta('HECHA', VERDE, 'completa')
    out['mision_racha_x_normal'] = carta('RACHA', GEMAS['fuego'])
    return out


def modelo(izq_slot, columna):
    """Modelo oversized: la carta cuelga del casillero (fila 1, columna) y queda de y=35 a y=89."""
    sx = 7 + 18 * columna
    b = (-8 + 15) / 3.0                     # borde izquierdo de la carta = origen del casillero - 8
    ancho = CW / 3.0
    yt = 8 + (44 - 35) / 3.0                # arriba: y=35 (el item se dibuja con su centro en origen + 9)
    yb = yt - CH / 3.0
    return {
        'gui_light': 'front',
        'textures': {'layer0': ''},
        'display': {'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [3, 3, 3]}},
        'elements': [{'from': [b, yb, 7.5], 'to': [b + ancho, yt, 8.5], 'faces': {
            'south': {'uv': [0, 0, 16, 16], 'texture': '#layer0'},
            'north': {'uv': [16, 0, 0, 16], 'texture': '#layer0'}}}],
    }


def escribir(pack):
    tex = os.path.join(pack, 'assets', '3y33', 'textures', 'item', 'trabajos')
    mod = os.path.join(pack, 'assets', '3y33', 'models', 'item', 'trabajos')
    its = os.path.join(pack, 'assets', '3y33', 'items', 'trabajos')
    for p in (tex, mod, its): os.makedirs(p, exist_ok=True)
    n = 0

    def poner(clave, img, sufijo=''):
        nonlocal n
        nombre = clave + sufijo
        m = modelo(0, 0); m['textures']['layer0'] = '3y33:item/trabajos/' + clave
        json.dump(m, open(os.path.join(mod, nombre + '.json'), 'w'), indent=1)
        json.dump({'oversized_in_gui': True, 'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/' + nombre}},
                  open(os.path.join(its, nombre + '.json'), 'w'), indent=1)
        n += 1

    for clave, img in variantes().items():
        img.save(os.path.join(tex, clave + '.png'))
        poner(clave, img)
    return n


def hoja(salida, K=4):
    v = variantes()
    claves = list(v)
    cols = 8
    filas = (len(claves) + cols - 1) // cols
    pad = 8
    sh = Image.new('RGBA', (pad + cols * (CW * K + pad), pad + filas * (CH * K + pad)), (60, 60, 64, 255))
    for i, k in enumerate(claves):
        im = v[k].resize((CW * K, CH * K), Image.NEAREST)
        sh.alpha_composite(im, (pad + (i % cols) * (CW * K + pad), pad + (i // cols) * (CH * K + pad)))
    sh.save(salida)
    print(len(claves), 'cartas')


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == 'hoja': hoja(sys.argv[2])
    else: print(escribir(sys.argv[1]), 'cartas')
