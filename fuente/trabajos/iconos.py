"""Marcos con imagen para los trabajos: 5 trabajos x 4 marcos (prestigio 0..3) x normal/activo = 40 texturas 48x48.
Uso: python3 iconos.py <carpeta_pack>   (escribe items/, models/ y textures/ de trabajos dentro de assets/3y33)"""
import sys, os, math, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from pixel import lighten, darken
from pixel3 import icono, ACC, ICON, BAYER

S = 48
IDS = {'MINERO': 'minero', 'LEÑADOR': 'lenador', 'GRANJERO': 'granjero', 'CAZADOR': 'cazador', 'PESCADOR': 'pescador'}
# color del marco segun el prestigio
MARCOS = {
    'hierro': dict(base=(120, 126, 138), luz=(178, 186, 198), sombra=(58, 62, 72), adornos=0),
    'bronce': dict(base=(196, 118, 56), luz=(236, 170, 96), sombra=(104, 56, 28), adornos=1),
    'plata':  dict(base=(206, 216, 232), luz=(250, 252, 255), sombra=(108, 120, 144), adornos=2),
    'oro':    dict(base=(240, 192, 72), luz=(255, 236, 150), sombra=(150, 100, 24), adornos=3),
}
ORDEN = ['hierro', 'bronce', 'plata', 'oro']
RED = (210, 40, 60)


def fondo(acc, fuerte):
    img = Image.new('RGBA', (S, S), (0, 0, 0, 255)); px = img.load()
    cx = cy = S / 2 - 0.5
    for y in range(S):
        t = y / (S - 1)
        for x in range(S):
            top, bot = (30, 10, 18), (10, 4, 8)
            c = [top[i] * (1 - t) + bot[i] * t for i in range(3)]
            c = [c[i] * 0.84 + acc[i] * 0.16 * (1 - t * .6) for i in range(3)]
            r = math.hypot(x - cx, y - cy)
            g = max(0.0, 1 - r / 26.0)
            ang = math.atan2(y - cy, x - cx)
            rayo = 0.2 if (int((ang + math.pi) / (math.pi / 12)) % 2 == 0 and r < 28) else 0
            v = (g * 0.55 * (1.9 if fuerte else 1.5) + rayo * g) * 4
            b = int(v); fr = v - b
            if fr * 16 > BAYER[y % 4][x % 4]: b += 1
            k = min(b, 4) / 4 * (0.62 if fuerte else 0.5)
            c = [int(c[i] * (1 - k) + acc[i] * k) for i in range(3)]
            px[x, y] = tuple(c) + (255,)
    return img


def marco(img, m, activo, acc):
    d = ImageDraw.Draw(img)
    base, luz, sombra = m['base'], m['luz'], m['sombra']
    d.rectangle([0, 0, S - 1, S - 1], outline=(10, 4, 8, 255))
    d.rectangle([1, 1, S - 2, S - 2], outline=base + (255,))
    d.rectangle([2, 2, S - 3, S - 3], outline=base + (255,))
    d.rectangle([3, 3, S - 4, S - 4], outline=sombra + (255,))
    # brillo en el borde de arriba y a la izquierda
    d.line([(2, 1), (S - 3, 1)], fill=luz + (255,))
    d.line([(1, 2), (1, S - 3)], fill=luz + (255,))
    ad = m['adornos']
    if ad >= 1:  # remaches en las esquinas
        for (x, y) in ((5, 5), (S - 6, 5), (5, S - 6), (S - 6, S - 6)):
            d.point((x, y), fill=luz + (255,)); d.point((x + 1, y), fill=base + (255,)); d.point((x, y + 1), fill=base + (255,))
    if ad >= 2:  # linea interior y esquinas en L
        d.rectangle([5, 5, S - 6, S - 6], outline=darken(base, .55) + (255,))
        for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            ox = 5 if sx > 0 else S - 6; oy = 5 if sy > 0 else S - 6
            for k in range(1, 6): d.point((ox + sx * k, oy), fill=luz + (255,)); d.point((ox, oy + sy * k), fill=luz + (255,))
    if ad >= 3:  # remate con gema roja y chispas doradas
        cx = S // 2
        d.polygon([(cx, 0), (cx + 4, 4), (cx, 8), (cx - 4, 4)], fill=(18, 8, 12, 255), outline=base + (255,))
        d.point((cx, 4), fill=RED + (255,)); d.point((cx, 3), fill=lighten(RED, .5) + (255,))
        for (x, y) in ((9, 9), (S - 10, 9), (9, S - 10), (S - 10, S - 10)):
            d.point((x, y), fill=(255, 250, 200, 255))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)): d.point((x + dx, y + dy), fill=darken(luz, .25) + (255,))
    for (x, y) in ((0, 0), (S - 1, 0), (0, S - 1), (S - 1, S - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    if activo:
        # aro de color del trabajo por fuera + gema verde abajo
        d.rectangle([1, 1, S - 2, S - 2], outline=lighten(acc, .35) + (255,))
        gx, gy = S - 8, S - 8
        d.polygon([(gx, gy - 4), (gx + 4, gy), (gx, gy + 4), (gx - 4, gy)], fill=(60, 200, 90, 255), outline=(255, 255, 255, 255))
        d.point((gx, gy - 1), fill=(210, 255, 220, 255)); d.point((gx, gy), fill=(120, 240, 150, 255))
        for (x, y) in ((0, 0), (S - 1, 0), (0, S - 1), (S - 1, S - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    return img


def hacer(nombre, marco_id, activo):
    acc = ACC[nombre]
    img = fondo(acc, activo)
    ic = icono(ICON[nombre])
    # el icono es de 32x32: lo centro con un poquito de aire arriba (el remate del marco de oro)
    img.alpha_composite(ic, ((S - ic.width) // 2, (S - ic.height) // 2 + 1))
    return marco(img, MARCOS[marco_id], activo, acc)


def escribir(pack):
    tex = os.path.join(pack, 'assets', '3y33', 'textures', 'item', 'trabajos')
    mod = os.path.join(pack, 'assets', '3y33', 'models', 'item', 'trabajos')
    its = os.path.join(pack, 'assets', '3y33', 'items', 'trabajos')
    for p in (tex, mod, its): os.makedirs(p, exist_ok=True)
    n = 0
    for nombre, tid in IDS.items():
        for mid in ORDEN:
            for estado in ('normal', 'activo'):
                clave = '%s_%s_%s' % (tid, mid, estado)
                hacer(nombre, mid, estado == 'activo').save(os.path.join(tex, clave + '.png'))
                json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': '3y33:item/trabajos/' + clave}}, open(os.path.join(mod, clave + '.json'), 'w'), indent=1)
                json.dump({'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/' + clave}}, open(os.path.join(its, clave + '.json'), 'w'), indent=1)
                n += 1
    return n


def hoja(salida, K=4):
    pad = 8
    W = pad + 8 * (S * K + pad); H = pad + 5 * (S * K + pad)
    sh = Image.new('RGBA', (W, H), (40, 40, 44, 255))
    for r, (nombre, tid) in enumerate(IDS.items()):
        c = 0
        for mid in ORDEN:
            for estado in ('normal', 'activo'):
                im = hacer(nombre, mid, estado == 'activo').resize((S * K, S * K), Image.NEAREST)
                sh.alpha_composite(im, (pad + c * (S * K + pad), pad + r * (S * K + pad))); c += 1
    sh.save(salida)


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == 'hoja':
        hoja(sys.argv[2]); print('hoja ok')
    else:
        print(escribir(sys.argv[1]), 'texturas')
