"""Cartas grandes de los trabajos: 32x48 px (2 casilleros de ancho x 3 de alto), una por trabajo / marco de prestigio / estado.
Se muestran como item "oversized" en el menu: cada pixel de la textura es un pixel de la pantalla del juego (sin estirar).
Uso: python3 cartas32.py hoja <salida.png>  |  python3 cartas32.py <carpeta_pack>"""
import sys, os, math, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from pixel import lighten, darken, contorno, texto
from pixel3 import icono, ACC, ICON, BAYER

W, H = 32, 48
IDS = {'MINERO': 'minero', 'LEÑADOR': 'lenador', 'GRANJERO': 'granjero', 'CAZADOR': 'cazador', 'PESCADOR': 'pescador'}
ORDEN_TRABAJOS = ['minero', 'lenador', 'granjero', 'cazador', 'pescador']
MARCOS = {
    'hierro': dict(base=(120, 126, 138), luz=(178, 186, 198), sombra=(58, 62, 72), n=0),
    'bronce': dict(base=(196, 118, 56), luz=(236, 170, 96), sombra=(104, 56, 28), n=1),
    'plata':  dict(base=(206, 216, 232), luz=(250, 252, 255), sombra=(108, 120, 144), n=2),
    'oro':    dict(base=(240, 192, 72), luz=(255, 236, 150), sombra=(150, 100, 24), n=3),
}
ORDEN = ['hierro', 'bronce', 'plata', 'oro']
RED = (210, 40, 60)
CREAM = (248, 238, 216)


def fondo(acc, fuerte):
    img = Image.new('RGBA', (W, H), (0, 0, 0, 255)); px = img.load()
    cx = W / 2 - 0.5; cy = 20
    for y in range(H):
        t = y / (H - 1)
        for x in range(W):
            top, bot = (30, 10, 18), (10, 4, 8)
            c = [top[i] * (1 - t) + bot[i] * t for i in range(3)]
            c = [c[i] * 0.84 + acc[i] * 0.16 * (1 - t * .6) for i in range(3)]
            r = math.hypot(x - cx, y - cy)
            g = max(0.0, 1 - r / 20.0)
            ang = math.atan2(y - cy, x - cx)
            rayo = 0.2 if (int((ang + math.pi) / (math.pi / 10)) % 2 == 0 and r < 22) else 0
            v = (g * 0.55 * (1.9 if fuerte else 1.5) + rayo * g) * 4
            b = int(v); fr = v - b
            if fr * 16 > BAYER[y % 4][x % 4]: b += 1
            k = min(b, 4) / 4 * (0.62 if fuerte else 0.5)
            c = [int(c[i] * (1 - k) + acc[i] * k) for i in range(3)]
            px[x, y] = tuple(c) + (255,)
    return img


def icono_chico(clave, lado=22):
    """El icono de 32x32 llevado a lado x lado, con los bordes duros y su contorno."""
    g = icono(clave)
    # recorto el vacio para escalar solo el dibujo
    caja = g.getbbox()
    g = g.crop(caja)
    k = lado / max(g.size)
    nuevo = g.resize((max(1, round(g.width * k)), max(1, round(g.height * k))), Image.BOX)
    a = nuevo.split()[3].point(lambda v: 255 if v >= 110 else 0)
    nuevo.putalpha(a)
    # el contorno oscuro del original ya vino mezclado; lo rehago prolijo
    px = nuevo.load()
    for y in range(nuevo.height):
        for x in range(nuevo.width):
            r, gg, b, al = px[x, y]
            if al: px[x, y] = (r, gg, b, 255)
    lienzo = Image.new('RGBA', (nuevo.width + 2, nuevo.height + 2), (0, 0, 0, 0))
    lienzo.alpha_composite(nuevo, (1, 1))
    return contorno(lienzo)


def marco(img, m, activo, acc):
    d = ImageDraw.Draw(img)
    base, luz, sombra = m['base'], m['luz'], m['sombra']
    d.rectangle([0, 0, W - 1, H - 1], outline=(10, 4, 8, 255))
    d.rectangle([1, 1, W - 2, H - 2], outline=base + (255,))
    d.rectangle([2, 2, W - 3, H - 3], outline=sombra + (255,))
    d.line([(2, 1), (W - 3, 1)], fill=luz + (255,))
    d.line([(1, 2), (1, H - 3)], fill=luz + (255,))
    n = m['n']
    if n >= 1:
        for (x, y) in ((4, 4), (W - 5, 4)):
            d.point((x, y), fill=luz + (255,))
    if n >= 2:
        for sx, sy in ((1, 1), (-1, 1)):
            ox = 4 if sx > 0 else W - 5; oy = 4
            for k in range(1, 4): d.point((ox + sx * k, oy), fill=luz + (255,)); d.point((ox, oy + sy * k), fill=luz + (255,))
    if n >= 3:
        for (x, y) in ((7, 9), (W - 8, 9)):
            d.point((x, y), fill=(255, 250, 200, 255))
    for (x, y) in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    if activo:
        d.rectangle([1, 1, W - 2, H - 2], outline=lighten(acc, .35) + (255,))
        for (x, y) in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    if n >= 3 or activo:
        cx = W // 2
        gema = (60, 200, 90) if activo else RED
        d.polygon([(cx, 0), (cx + 3, 3), (cx, 6), (cx - 3, 3)], fill=(18, 8, 12, 255), outline=(base if n >= 3 else lighten(acc, .35)) + (255,))
        d.point((cx, 3), fill=gema + (255,)); d.point((cx, 2), fill=lighten(gema, .5) + (255,))
    return img


import pixel
pixel.FONT.setdefault('T', ['XXX', '.X.', '.X.', '.X.', '.X.'])
ANCHA = {'M': ['X...X', 'XX.XX', 'X.X.X', 'X...X', 'X...X']}
ETIQUETA = {'MINERO': 'MINA', 'LEÑADOR': 'TALA', 'GRANJERO': 'GRANJA', 'CAZADOR': 'CAZA', 'PESCADOR': 'PESCA'}


def cinta(img, acc, activo, nombre):
    """Cinta de abajo con la actividad del trabajo (el nombre completo va en el cartel del cursor)."""
    d = ImageDraw.Draw(img)
    y0, y1 = 37, 45
    d.rectangle([3, y0, W - 4, y1], fill=(20, 8, 12, 255), outline=acc + (255,))
    d.line([(4, y0 + 1), (W - 5, y0 + 1)], fill=darken(acc, .45) + (255,))
    t = ETIQUETA[nombre]
    letras = [ANCHA.get(ch, pixel.FONT[ch]) for ch in t]
    ancho = sum(len(g[0]) for g in letras) + len(letras) - 1
    for col, dx in (((0, 0, 0), 1), (CREAM, 0)):
        x = (W - ancho) // 2 + dx
        for g in letras:
            for r, fila in enumerate(g):
                for c, v in enumerate(fila):
                    if v == 'X': img.putpixel((x + c, y0 + 2 + r + dx), col + (255,))
            x += len(g[0]) + 1
    return img


def carta(nombre, marco_id, activo):
    acc = ACC[nombre]
    img = fondo(acc, activo)
    ic = icono_chico(ICON[nombre])
    img.alpha_composite(ic, ((W - ic.width) // 2, 5 + (30 - ic.height) // 2))
    img = cinta(img, acc, activo, nombre)
    return marco(img, MARCOS[marco_id], activo, acc)


# ---------- ubicacion del item de cada carta en el menu ----------
# Menu de 5 filas: las cartas ocupan las filas 1 a 3 (49 px de alto). Cada carta cuelga de un casillero de la fila 2.
# Los casilleros miden 18 px y el primero empieza en x=7. La carta i empieza en x = 7 + 32*i.
# Dentro del casillero (16 unidades de modelo = 16 px) el modelo va de x0 a x0+32 y de y -16 a 32 (el maximo que acepta el juego).
SLOT_X = {0: 7, 2: 43, 4: 79, 6: 115, 8: 151}
COLUMNA = {'minero': 0, 'lenador': 2, 'granjero': 4, 'cazador': 6, 'pescador': 8}
IZQ = {'minero': 7, 'lenador': 39, 'granjero': 71, 'cazador': 103, 'pescador': 135}


def modelo(tid):
    x0 = IZQ[tid] - SLOT_X[COLUMNA[tid]]
    return x0


def escribir(pack):
    tex = os.path.join(pack, 'assets', '3y33', 'textures', 'item', 'trabajos')
    mod = os.path.join(pack, 'assets', '3y33', 'models', 'item', 'trabajos')
    its = os.path.join(pack, 'assets', '3y33', 'items', 'trabajos')
    for p in (tex, mod, its): os.makedirs(p, exist_ok=True)
    n = 0
    for nombre, tid in IDS.items():
        x0 = modelo(tid)
        for mid in ORDEN:
            for estado in ('normal', 'activo'):
                clave = 'carta_%s_%s_%s' % (tid, mid, estado)
                carta(nombre, mid, estado == 'activo').save(os.path.join(tex, clave + '.png'))
                json.dump({
                    'gui_light': 'front',
                    'textures': {'layer0': '3y33:item/trabajos/' + clave},
                    'elements': [{'from': [x0, -16, 7.5], 'to': [x0 + 32, 32, 8.5], 'faces': {
                        'south': {'uv': [0, 0, 16, 16], 'texture': '#layer0'},
                        'north': {'uv': [16, 0, 0, 16], 'texture': '#layer0'}}}],
                }, open(os.path.join(mod, clave + '.json'), 'w'), indent=1)
                json.dump({'oversized_in_gui': True, 'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/' + clave}},
                          open(os.path.join(its, clave + '.json'), 'w'), indent=1)
                # la misma carta, centrada en la ventana de detalle (columna 4: el casillero empieza en x=79 y la carta en x=72)
                json.dump({
                    'gui_light': 'front',
                    'textures': {'layer0': '3y33:item/trabajos/' + clave},
                    'elements': [{'from': [-7, -16, 7.5], 'to': [25, 32, 8.5], 'faces': {
                        'south': {'uv': [0, 0, 16, 16], 'texture': '#layer0'},
                        'north': {'uv': [16, 0, 0, 16], 'texture': '#layer0'}}}],
                }, open(os.path.join(mod, 'detalle_' + clave + '.json'), 'w'), indent=1)
                json.dump({'oversized_in_gui': True, 'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/detalle_' + clave}},
                          open(os.path.join(its, 'detalle_' + clave + '.json'), 'w'), indent=1)
                n += 1
    # casillero transparente para que el cursor muestre el nombre en toda la carta
    Image.new('RGBA', (16, 16), (0, 0, 0, 0)).save(os.path.join(tex, 'vacio.png'))
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': '3y33:item/trabajos/vacio'}}, open(os.path.join(mod, 'vacio.json'), 'w'), indent=1)
    json.dump({'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/vacio'}}, open(os.path.join(its, 'vacio.json'), 'w'), indent=1)
    return n


def hoja(salida, K=8):
    pad = 8
    sh = Image.new('RGBA', (pad + 8 * (W * K + pad), pad + 5 * (H * K + pad)), (40, 40, 44, 255))
    for r, (nombre, tid) in enumerate(IDS.items()):
        c = 0
        for mid in ORDEN:
            for estado in ('normal', 'activo'):
                im = carta(nombre, mid, estado == 'activo').resize((W * K, H * K), Image.NEAREST)
                sh.alpha_composite(im, (pad + c * (W * K + pad), pad + r * (H * K + pad))); c += 1
    sh.save(salida)


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == 'hoja':
        hoja(sys.argv[2], 4); print('ok')
    else:
        print(escribir(sys.argv[1]), 'cartas')
