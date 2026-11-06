"""Vista previa en 3D (proyeccion isometrica) de un maniqui con su skin y su gorro, para revisar el diseno sin entrar al juego.
Uso desde Python: vista(skin_png, gorro_modelo_json_o_None, gorro_textura, salida, yaw=30)"""
import math, json
from PIL import Image, ImageDraw

SH = {'up': 1.0, 'south': .86, 'north': .78, 'east': .7, 'west': .7, 'down': .5}


def _rot(p, yaw, pit):
    x, y, z = p
    c, s = math.cos(yaw), math.sin(yaw)
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(pit), math.sin(pit)
    y, z = y * c - z * s, y * s + z * c
    return x, y, z


class Escena:
    def __init__(self):
        self.caras = []  # (profundidad, polygon3D[4], textura, rect uv (x,y,w,h), flipU, flipV, cara)

    def caja(self, a, b, tex, uvs, mirror_u=None):
        """a, b: esquinas (x, y, z) en pixeles (y arriba). uvs: cara -> (x, y, w, h) dentro de tex. Frente = sur (+z)."""
        x0, y0, z0 = [min(p, q) for p, q in zip(a, b)]
        x1, y1, z1 = [max(p, q) for p, q in zip(a, b)]
        Q = {  # cada cara: 4 esquinas en orden (arriba-izq, arriba-der, abajo-der, abajo-izq) vistas desde afuera
            'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        }
        for cara, rect in uvs.items():
            if rect is None: continue
            self.caras.append((Q[cara], tex, rect, cara))

    def dibujar(self, salida, yaw=30, pit=20, K=10, tam=(520, 620), fondo=(70, 74, 86, 255), cyr=0.93):
        yaw, pit = math.radians(yaw), math.radians(-pit)
        im = Image.new('RGBA', tam, fondo)
        cx, cy = tam[0] / 2, tam[1] * cyr
        lista = []
        for q, tex, rect, cara in self.caras:
            r = [_rot(p, yaw, pit) for p in q]
            prof = sum(p[2] for p in r) / 4
            # descartar caras de espaldas
            ax, ay = r[1][0] - r[0][0], r[1][1] - r[0][1]
            bx, by = r[3][0] - r[0][0], r[3][1] - r[0][1]
            if ax * by - ay * bx > 0: continue
            lista.append((prof, r, tex, rect, cara))
        lista.sort(key=lambda t: t[0])  # mas lejos primero (z menor = mas lejos tras la rotacion)
        d = ImageDraw.Draw(im)
        for prof, r, tex, (rx, ry, rw, rh), cara in lista:
            for j in range(rh):
                for i in range(rw):
                    px = tex.getpixel((rx + i, ry + j))
                    if px[3] < 128: continue
                    u0, u1, v0, v1 = i / rw, (i + 1) / rw, j / rh, (j + 1) / rh

                    def pt(u, v):
                        top = (r[0][0] + (r[1][0] - r[0][0]) * u, r[0][1] + (r[1][1] - r[0][1]) * u)
                        bot = (r[3][0] + (r[2][0] - r[3][0]) * u, r[3][1] + (r[2][1] - r[3][1]) * u)
                        return (cx + (top[0] + (bot[0] - top[0]) * v) * K, cy - (top[1] + (bot[1] - top[1]) * v) * K)
                    poly = [pt(u0, v0), pt(u1, v0), pt(u1, v1), pt(u0, v1)]
                    k = SH[cara]
                    d.polygon(poly, fill=tuple(int(c * k) for c in px[:3]) + (255,))
        im.save(salida)


PARTES = {  # nombre: (u, v, ancho, alto, prof), caja (a, b) en pixeles, con y=0 en los pies, y centrada en x/z
    'cabeza': ((0, 0, 8, 8, 8), (-4, 24, -4), (4, 32, 4)),
    'cuerpo': ((16, 16, 8, 12, 4), (-4, 12, -2), (4, 24, 2)),
    'brazo_d': ((40, 16, 4, 12, 4), (-8, 12, -2), (-4, 24, 2)),
    'brazo_i': ((32, 48, 4, 12, 4), (4, 12, -2), (8, 24, 2)),
    'pierna_d': ((0, 16, 4, 12, 4), (-4, 0, -2), (0, 12, 2)),
    'pierna_i': ((16, 48, 4, 12, 4), (0, 0, -2), (4, 12, 2)),
}
CAPAS = {'cabeza': (32, 0), 'cuerpo': (16, 32), 'brazo_d': (40, 32), 'brazo_i': (48, 48), 'pierna_d': (0, 32), 'pierna_i': (0, 48)}


def _uvs(u, v, w, h, d):
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'west': (u, v + d, d, h), 'south': (u + d, v + d, w, h),
            'east': (u + d + w, v + d, d, h), 'north': (u + 2 * d + w, v + d, w, h)}


def maniqui(escena, skin, gorro=None, tex_gorro=None, margen=0.5):
    for n, ((u, v, w, h, d), a, b) in PARTES.items():
        escena.caja(a, b, skin, _uvs(u, v, w, h, d))
        cu, cv = CAPAS[n]
        a2 = (a[0] - margen, a[1] - margen, a[2] - margen); b2 = (b[0] + margen, b[1] + margen, b[2] + margen)
        escena.caja(a2, b2, skin, _uvs(cu, cv, w, h, d))
    if gorro:
        m = json.load(open(gorro)) if isinstance(gorro, str) else gorro
        esc = 0.625
        disp = m.get('display', {}).get('head', {})
        sc = disp.get('scale', [1, 1, 1]); tr = disp.get('translation', [0, 0, 0])
        for e in m['elements']:
            def conv(p):  # unidades de modelo -> pixeles del maniqui (gira 180 grados: el norte del gorro es el frente)
                x, y, z = [(p[i] - 8) * sc[i] + tr[i] for i in range(3)]
                return (-x * esc, 28 + y * esc, -z * esc)
            a, b = conv(e['from']), conv(e['to'])
            uvs = {}
            f = e['faces']
            # el gorro gira 180: norte -> sur, sur -> norte, este -> oeste, oeste -> este
            giro = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'up', 'down': 'down'}
            for cara, fc in f.items():
                uv = fc.get('uv', [0, 0, 16, 16])
                k = tex_gorro.size[0] / 16.0
                r = (int(round(min(uv[0], uv[2]) * k)), int(round(min(uv[1], uv[3]) * k)), max(1, int(round(abs(uv[2] - uv[0]) * k))), max(1, int(round(abs(uv[3] - uv[1]) * k))))
                uvs[giro[cara]] = r
            escena.caja(a, b, tex_gorro, uvs)


def vista(skin_png, modelo, tex_gorro_png, salida, yaw=30, pit=18, K=9):
    sk = Image.open(skin_png).convert('RGBA')
    tg = Image.open(tex_gorro_png).convert('RGBA') if tex_gorro_png else None
    e = Escena()
    maniqui(e, sk, modelo, tg)
    e.dibujar(salida, yaw=yaw, pit=pit, K=K)


def hoja(items, salida, ang=(25, 155, 205, 335), K=8):
    """items: lista de (skin_png, modelo, tex). Una fila por item, una columna por angulo."""
    W, H = 330, 470
    sh = Image.new('RGBA', (W * len(ang), H * len(items)), (70, 74, 86, 255))
    for r, (s, m, t) in enumerate(items):
        sk = Image.open(s).convert('RGBA'); tg = Image.open(t).convert('RGBA') if t else None
        for c, a in enumerate(ang):
            e = Escena(); maniqui(e, sk, m, tg)
            e.dibujar('/tmp/_v.png', yaw=a, K=K, tam=(W, H))
            sh.alpha_composite(Image.open('/tmp/_v.png'), (c * W, r * H))
    sh.save(salida)
