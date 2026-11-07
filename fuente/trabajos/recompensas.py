"""Arte de las recompensas por nivel. Los iconos usan la textura de Minecraft como base (el modelo del pack la apunta, no se copia) y le ponen
encima una capa propia de 16x16 con flechas "de mejora" del color de la rareza:
- boost_xp_<rareza>: botella de XP + 1..4 flechas hacia arriba (comun 1, rara 2, epica 3, legendaria 4)
- boost_plata_<rareza>: lingote de oro + 1..4 flechas
- boost_xpdirecta_<rareza>: botella de XP con 1..4 destellos (la XP instantanea no dura: no lleva flechas)
- boost_mega: estrella del Nether + 4 flechas rojas
- recompensa_<tipo>_<rareza>[_sel]: carta (la misma de las misiones, 34x54) con el rotulo del tipo y la gema de la rareza
Uso: python3 recompensas.py hoja <salida.png>  |  python3 recompensas.py <carpeta_pack>"""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import misiones_cartas as MC
from pixel import lighten, darken

MC.FONT['X'] = ['X.X', 'X.X', '.X.', 'X.X', 'X.X']
MC.FONT['V'] = ['X.X', 'X.X', 'X.X', 'X.X', '.X.']
MC.FONT['Y'] = ['X.X', 'X.X', '.X.', '.X.', '.X.']

RAREZAS = ['comun', 'rara', 'epica', 'legendaria']
COLOR = {'comun': (205, 205, 212), 'rara': (85, 168, 255), 'epica': (180, 85, 255), 'legendaria': (255, 170, 0), 'mega': (255, 77, 77)}
CONTORNO = (22, 10, 16)
BASE = {'xp': 'experience_bottle', 'xpdirecta': 'experience_bottle', 'plata': 'gold_ingot', 'mega': 'nether_star'}


def _capa(forma, color):
    """forma: {(x, y): 'c' (color) | 'l' (luz)}. Devuelve una capa 16x16 con contorno oscuro."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for (x, y) in forma:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in forma and 0 <= p[0] < 16 and 0 <= p[1] < 16: im.putpixel(p, CONTORNO + (255,))
    for (x, y), t in forma.items():
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), (lighten(color, .55) if t == 'l' else color if t == 'c' else darken(color, .3)) + (255,))
    return im


def flechas(color, n, cx=12, y0=2):
    """n flechas (chevrones finos) hacia arriba apiladas, sobre el costado derecho del item."""
    forma = {}
    for k in range(n):
        y = y0 + 3 * k
        forma[(cx, y)] = 'l'
        for dx, dy, t in ((-1, 1, 'c'), (1, 1, 'c'), (-2, 2, 'c'), (2, 2, 'o')):
            forma[(cx + dx, y + dy)] = t
        forma[(cx, y + 1)] = 'c'
    return _capa(forma, color)


def destellos(color, n):
    sitios = [(12, 3), (13, 8), (9, 1), (14, 13)][:n]
    forma = {}
    for (x, y) in sitios:
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)): forma[(x + dx, y + dy)] = 'c'
        forma[(x, y)] = 'l'
    return _capa(forma, color)


def regalo(base, osc, luz, cinta, cinta_osc):
    """Regalo (caja con cinta y mono) de 16x16, dibujado a mano: el icono de "elegi tu recompensa"."""
    forma = {}
    def r(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): forma[(x, y)] = c
    r(2, 8, 13, 14, base); r(13, 8, 13, 14, osc); r(2, 14, 13, 14, osc)          # cuerpo
    r(1, 5, 14, 7, base); r(1, 5, 14, 5, luz); r(1, 7, 14, 7, osc)                # tapa
    r(7, 5, 8, 14, cinta); r(8, 5, 8, 14, cinta_osc)                              # cinta vertical

    for x in range(1, 15): forma[(x, 6)] = cinta if x not in (8,) else cinta_osc
    for (x, y) in ((4, 3), (5, 2), (6, 3), (5, 4), (4, 4), (6, 4), (11, 3), (10, 2), (9, 3), (10, 4), (9, 4), (11, 4)): forma[(x, y)] = cinta
    forma[(5, 3)] = cinta_osc; forma[(10, 3)] = cinta_osc
    r(7, 3, 8, 4, cinta_osc); forma[(7, 3)] = luz
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for (x, y) in forma:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in forma and 0 <= p[0] < 16 and 0 <= p[1] < 16: im.putpixel(p, CONTORNO + (255,))
    for (x, y), c in forma.items(): im.putpixel((x, y), tuple(c) + (255,))
    return im


ROTULOS = {'xp': 'XP', 'plata': 'PLATA', 'xpdirecta': 'AHORA', 'mega': 'MEGA'}


def variantes():
    """Todo lo que se escribe: nombre -> (imagen, es_carta, textura base de Minecraft o None)."""
    out = {}
    for tipo in ('xp', 'plata', 'xpdirecta'):
        for i, r in enumerate(RAREZAS):
            capa = destellos(COLOR[r], i + 1) if tipo == 'xpdirecta' else flechas(COLOR[r], i + 1)
            out['boost_%s_%s' % (tipo, r)] = (capa, False, BASE[tipo])
            out['recompensa_%s_%s' % (tipo, r)] = (MC.carta(ROTULOS[tipo], COLOR[r]), True, None)
            out['recompensa_%s_%s_sel' % (tipo, r)] = (MC.carta(ROTULOS[tipo], COLOR[r], 'lista'), True, None)
    out['boost_mega'] = (flechas(COLOR['mega'], 4), False, BASE['mega'])
    out['recompensa_mega'] = (MC.carta(ROTULOS['mega'], COLOR['mega']), True, None)
    out['reclamar'] = (regalo((196, 40, 52), (128, 22, 34), (236, 96, 96), (255, 204, 64), (200, 140, 30)), False, None)
    out['reclamar_bloqueado'] = (regalo((128, 128, 138), (86, 86, 98), (170, 170, 180), (168, 168, 178), (118, 118, 130)), False, None)
    out['recompensa_mega_sel'] = (MC.carta(ROTULOS['mega'], COLOR['mega'], 'lista'), True, None)
    return out


def escribir(pack):
    tex = os.path.join(pack, 'assets', '3y33', 'textures', 'item', 'trabajos')
    mod = os.path.join(pack, 'assets', '3y33', 'models', 'item', 'trabajos')
    its = os.path.join(pack, 'assets', '3y33', 'items', 'trabajos')
    for p in (tex, mod, its): os.makedirs(p, exist_ok=True)
    n = 0
    for nombre, (img, carta, base) in variantes().items():
        img.save(os.path.join(tex, nombre + '.png'))
        if carta:
            m = MC.modelo(0, 0); m['textures']['layer0'] = '3y33:item/trabajos/' + nombre
            json.dump(m, open(os.path.join(mod, nombre + '.json'), 'w'), indent=1)
            json.dump({'oversized_in_gui': True, 'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/' + nombre}},
                      open(os.path.join(its, nombre + '.json'), 'w'), indent=1)
        else:
            tx = {'layer0': 'minecraft:item/' + base, 'layer1': '3y33:item/trabajos/' + nombre} if base else {'layer0': '3y33:item/trabajos/' + nombre}
            json.dump({'parent': 'minecraft:item/generated', 'textures': tx},
                      open(os.path.join(mod, nombre + '.json'), 'w'), indent=1)
            json.dump({'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/' + nombre}}, open(os.path.join(its, nombre + '.json'), 'w'), indent=1)
        n += 1
    return n


def hoja(salida, K=12):
    v = variantes()
    iconos = [k for k, x in v.items() if not x[1]]
    cartas = [k for k, x in v.items() if x[1] and not k.endswith('_sel')]
    pad = 10
    cols = 7
    filas = (len(iconos) + cols - 1) // cols
    ancho = max(pad + cols * (16 * K + pad), pad + len(cartas) * (MC.CW * 4 + pad))
    sh = Image.new('RGBA', (ancho, pad * 3 + filas * (16 * K + pad) + MC.CH * 4 + pad), (92, 92, 98, 255))
    for i, k in enumerate(iconos):
        im = v[k][0].resize((16 * K, 16 * K), Image.NEAREST)
        sh.alpha_composite(im, (pad + (i % cols) * (16 * K + pad), pad + (i // cols) * (16 * K + pad)))
    y = pad * 2 + filas * (16 * K + pad)
    for i, k in enumerate(cartas):
        sh.alpha_composite(v[k][0].resize((MC.CW * 4, MC.CH * 4), Image.NEAREST), (pad + i * (MC.CW * 4 + pad), y))
    sh.save(salida)
    print(len(iconos), 'capas', len(cartas), 'cartas')


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == 'hoja': hoja(sys.argv[2])
    else: print(escribir(sys.argv[1]), 'recompensas')
