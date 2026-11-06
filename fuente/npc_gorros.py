"""Gorros 3D de los NPC (uno por trabajo): modelos de cajas con textura pintada (atlas de 128x128).
Se ponen en la cabeza del maniqui como un item con item_model "3y33:gorro/<trabajo>".
Medidas en unidades de modelo: la cabeza ocupa de 1,6 a 14,4 en x, y, z; arriba de la cabeza es y=14,4; el frente es el lado norte (z chico).
Uso: python3 npc_gorros.py <carpeta_pack>"""
import sys, os, json, math, random
from PIL import Image

ATLAS = 128


def mezcla(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


class Mat:
    """Material: color base + patron. fn(cara, x, y, w, h, rnd) -> rgb opcional (si devuelve None usa el base)."""
    def __init__(self, base, ruido=0.07, patron=None, borde=None):
        self.base, self.ruido, self.patron, self.borde = base, ruido, patron, borde


def pat_paja(c, x, y, w, h, r):
    k = 1.0 if (x + y) % 4 < 2 else 0.88
    if y % 3 == 0: k *= 0.93
    return mezcla(c, k)


def pat_cuadros(c1, c2):
    def f(c, x, y, w, h, r):
        a = (x // 2 + y // 2) % 2
        return mezcla(c1 if a == 0 else c2, 1.0)
    return f


def pat_rayas(c2, paso=3):
    def f(c, x, y, w, h, r):
        return c2 if y % paso == 0 else None
    return f


def pat_hueso(c, x, y, w, h, r):
    return mezcla(c, 0.82) if (x * 7 + y * 13) % 11 == 0 else None


def pat_madera(c, x, y, w, h, r):
    return mezcla(c, 0.84) if y % 4 == 1 else (mezcla(c, 1.06) if y % 4 == 3 else c)


def pat_metal(c, x, y, w, h, r):
    if x == 0 or y == 0: return mezcla(c, 1.25)
    if x == w - 1 or y == h - 1: return mezcla(c, 0.7)
    return None


def pat_lana(c, x, y, w, h, r):
    return mezcla(c, 1.0 + 0.12 * (1 if (x + 2 * y) % 5 == 0 else 0) - 0.1 * (1 if (3 * x + y) % 7 == 0 else 0))


def pat_pelo(c, x, y, w, h, r):
    return mezcla(c, 0.85 + 0.3 * ((x * 5 + y * 3) % 4) / 3)


def pat_mena(c, x, y, w, h, r, mena=(240, 200, 70)):
    return mena if (x * 3 + y * 5) % 7 == 0 and 0 < x < w - 1 and 0 < y < h - 1 else None


def pat_escamas(c, x, y, w, h, r):
    return mezcla(c, 1.12) if (x + (y % 2) * 2) % 4 == 0 else (mezcla(c, 0.86) if y % 2 == 1 else None)


class Gorro:
    def __init__(self, nombre):
        self.nombre = nombre
        self.cajas = []   # (desde, hasta, material)
        self.atlas = Image.new('RGBA', (ATLAS, ATLAS), (0, 0, 0, 0))
        self.cx = 0; self.cy = 0; self.fila = 0
        self.rnd = random.Random(nombre)
        self.elementos = []

    def reservar(self, w, h):
        w = max(1, int(math.ceil(w))); h = max(1, int(math.ceil(h)))
        if self.cx + w > ATLAS:
            self.cx = 0; self.cy += self.fila + 1; self.fila = 0
        if self.cy + h > ATLAS: raise RuntimeError('atlas lleno')
        x, y = self.cx, self.cy
        self.cx += w + 1; self.fila = max(self.fila, h)
        return x, y, w, h

    def caja(self, d, h, mat, rot=None):
        dx, dy, dz = h[0] - d[0], h[1] - d[1], h[2] - d[2]
        faces = {}
        medidas = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
        sombra = {'up': 1.0, 'north': 0.97, 'south': 0.9, 'east': 0.92, 'west': 0.92, 'down': 0.8}
        for cara, (mw, mh) in medidas.items():
            # un texel por unidad de modelo (minimo 2x2 para que tenga detalle)
            x, y, w, hh = self.reservar(max(2, mw), max(2, mh))
            for j in range(hh):
                for i in range(w):
                    c = mat.base
                    if mat.patron:
                        p = mat.patron(c, i, j, w, hh, self.rnd)
                        if p is not None: c = p
                    if mat.ruido: c = mezcla(c, 1 + (self.rnd.random() - 0.5) * 2 * mat.ruido)
                    if mat.borde and (i == 0 or j == 0 or i == w - 1 or j == hh - 1): c = mat.borde
                    self.atlas.putpixel((x + i, y + j), mezcla(c, sombra[cara] if cara != 'up' else 1.0) + (255,))
            u = 16.0 / ATLAS
            faces[cara] = {'uv': [round(x * u, 4), round(y * u, 4), round((x + w) * u, 4), round((y + hh) * u, 4)], 'texture': '#0'}
        e = {'from': [round(v, 3) for v in d], 'to': [round(v, 3) for v in h], 'faces': faces}
        if rot: e['rotation'] = rot
        self.elementos.append(e)

    def modelo(self, display=None):
        return {'textures': {'0': '3y33:gorro/' + self.nombre, 'particle': '3y33:gorro/' + self.nombre},
                'elements': self.elementos,
                'display': {'head': display or {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}


def minero():
    g = Gorro('minero')
    AM = (244, 196, 36); AMO = (214, 160, 20); GR = (96, 98, 108); NE = (30, 30, 36)
    g.caja([0.6, 12.4, 0.6], [15.4, 15.6, 15.4], Mat(AM, patron=pat_metal))          # casco
    g.caja([1.8, 15.6, 1.8], [14.2, 17.2, 14.2], Mat(AM, patron=pat_metal))          # cupula
    g.caja([6.4, 17.2, 2.4], [9.6, 18.2, 13.6], Mat(AMO))                            # cresta
    g.caja([0.6, 12.4, 0.6], [15.4, 13.2, 15.4], Mat(NE, ruido=0.03))                # franja negra
    g.caja([1.2, 12.4, -2.6], [14.8, 13.4, 0.6], Mat(AMO, patron=pat_metal))         # visera
    g.caja([5.4, 13.6, -0.8], [10.6, 16.6, 0.6], Mat(GR, patron=pat_metal))          # carcasa de la lampara
    g.caja([6.3, 14.2, -1.4], [9.7, 16.0, -0.8], Mat((255, 248, 200), ruido=0.02))   # lente
    g.caja([6.8, 14.7, -1.8], [9.2, 15.5, -1.4], Mat((255, 255, 235), ruido=0.0))    # brillo
    g.caja([10.6, 14.4, -0.2], [12.4, 15.6, 0.6], Mat(NE))                           # cable
    g.caja([2.4, 17.2, 3.4], [5.6, 19.6, 6.6], Mat((70, 72, 80), patron=lambda c, x, y, w, h, r: (240, 200, 70) if (x + y * 2) % 3 == 0 else None))   # mena de oro encima
    g.caja([10.4, 17.2, 7.4], [13.0, 19.2, 10.0], Mat((70, 72, 80), patron=lambda c, x, y, w, h, r: (90, 230, 240) if (x * 2 + y) % 3 == 0 else None))  # diamante
    return g


def lenador():
    g = Gorro('lenador')
    RO = (178, 32, 36); NE = (30, 26, 28); MAD = (126, 84, 46); AC = (190, 196, 206); PEL = (234, 226, 210)
    cu = pat_cuadros(RO, NE)
    g.caja([0.6, 12.0, 0.6], [15.4, 16.2, 15.4], Mat(RO, patron=cu, ruido=0.04))      # gorro a cuadros
    g.caja([1.6, 16.2, 1.6], [14.4, 17.2, 14.4], Mat(RO, patron=cu, ruido=0.04))
    g.caja([0.8, 12.0, -3.0], [15.2, 13.0, 0.6], Mat((70, 20, 22), patron=pat_madera))  # visera
    g.caja([-0.6, 6.0, 2.6], [0.8, 12.4, 13.4], Mat(RO, patron=cu))                  # orejera izq
    g.caja([15.2, 6.0, 2.6], [16.6, 12.4, 13.4], Mat(RO, patron=cu))                 # orejera der
    g.caja([-0.8, 5.0, 2.4], [0.8, 6.2, 13.6], Mat(PEL, patron=pat_pelo))
    g.caja([15.2, 5.0, 2.4], [16.8, 6.2, 13.6], Mat(PEL, patron=pat_pelo))
    # hacha clavada arriba
    g.caja([7.2, 17.2, 7.2], [8.8, 24.0, 8.8], Mat(MAD, patron=pat_madera))          # mango
    g.caja([6.4, 21.0, 4.6], [9.6, 24.6, 7.2], Mat(AC, patron=pat_metal))            # filo
    g.caja([6.4, 20.4, 7.2], [9.6, 24.6, 9.2], Mat((120, 124, 134), patron=pat_metal))  # cabeza del hacha
    return g


def granjero():
    g = Gorro('granjero')
    PA = (236, 206, 100); PAO = (204, 170, 70); RO = (176, 40, 36); MAR = (110, 68, 36); VE = (96, 170, 60)
    g.caja([-4.0, 13.4, -4.0], [20.0, 14.2, 20.0], Mat(PA, patron=pat_paja))         # ala
    g.caja([-3.0, 13.0, -3.0], [19.0, 13.4, 19.0], Mat(PAO, patron=pat_paja))        # sombra inferior
    g.caja([2.4, 14.2, 2.4], [13.6, 15.6, 13.6], Mat(RO, patron=pat_rayas(MAR, 3)))  # cinta
    g.caja([2.8, 15.6, 2.8], [13.2, 19.0, 13.2], Mat(PA, patron=pat_paja))           # copa
    g.caja([3.8, 19.0, 3.8], [12.2, 19.4, 12.2], Mat(PAO, patron=pat_paja))
    # espigas de trigo al costado
    for dx, h in ((0.0, 5.0), (1.4, 6.2), (2.8, 4.6)):
        g.caja([13.0 + dx, 15.0, 5.6], [13.5 + dx, 15.0 + h, 6.1], Mat(VE))
        g.caja([12.5 + dx, 15.0 + h - 2.4, 5.2], [14.0 + dx, 15.0 + h + 1.4, 6.5], Mat((226, 184, 58), patron=lambda c, x, y, w, hh, r: mezcla(c, 0.85) if (x + y) % 2 == 0 else None))
    # manzana en el ala
    g.caja([-1.0, 14.2, 2.0], [2.4, 17.4, 5.4], Mat((200, 36, 40), patron=lambda c, x, y, w, h, r: (255, 150, 150) if (x, y) == (1, 1) else None))
    g.caja([0.2, 17.4, 3.2], [1.0, 18.6, 4.0], Mat(MAR))
    return g


def cazador():
    g = Gorro('cazador')
    VE = (46, 84, 52); VEO = (30, 56, 38); CU = (122, 82, 50); HU = (226, 220, 196); RO = (210, 48, 48)
    g.caja([0.6, 11.6, 0.6], [15.4, 15.8, 15.4], Mat(VE, patron=pat_lana))           # capucha
    g.caja([1.8, 15.8, 1.8], [14.2, 16.8, 14.2], Mat(VE, patron=pat_lana))
    g.caja([0.6, 11.6, 0.6], [15.4, 12.6, 15.4], Mat(CU, patron=pat_madera))         # cinta de cuero
    g.caja([1.0, 12.6, -2.2], [15.0, 13.4, 0.6], Mat(VEO, patron=pat_lana))          # visera
    # astas (cuernos de ciervo)
    for lado in (0, 1):
        x = 2.0 if lado == 0 else 12.8
        s = 1 if lado == 0 else -1
        g.caja([x, 16.8, 6.8], [x + 1.4, 21.4, 8.2], Mat(HU, patron=pat_hueso))              # asta principal
        g.caja([x - 1.8 * s if s > 0 else x + 1.4, 19.2, 6.8], [x if s > 0 else x + 3.2, 20.6, 8.2], Mat(HU, patron=pat_hueso))   # rama hacia afuera
        g.caja([x, 21.4, 6.8], [x + 1.4, 23.6, 8.2], Mat(HU, patron=pat_hueso))              # punta
    # pluma roja en el costado
    g.caja([15.4, 13.6, 5.0], [16.6, 15.2, 11.0], Mat(RO))
    g.caja([16.2, 15.2, 5.8], [17.2, 20.4, 10.2], Mat(RO, patron=lambda c, x, y, w, h, r: mezcla(c, 1.2) if x == w // 2 else None))
    return g


def pescador():
    g = Gorro('pescador')
    AM = (242, 200, 40); AMO = (208, 160, 22); AZ = (30, 62, 108); BL = (236, 236, 240)
    g.caja([-4.0, 12.6, -4.0], [20.0, 13.6, 20.0], Mat(AM, patron=pat_metal, ruido=0.04))       # ala ancha
    g.caja([-4.0, 6.0, 15.0], [20.0, 13.6, 20.0], Mat(AM, ruido=0.04))                          # cubre nuca
    g.caja([-4.0, 12.6, -4.0], [20.0, 13.0, 20.0], Mat(AMO))
    g.caja([2.0, 13.6, 2.0], [14.0, 17.6, 14.0], Mat(AM, ruido=0.04))                           # copa
    g.caja([1.8, 13.6, 1.8], [14.2, 15.0, 14.2], Mat(AZ, patron=pat_rayas((20, 40, 78), 2)))    # cinta azul
    g.caja([6.2, 14.0, 1.2], [9.8, 17.0, 1.8], Mat(BL, patron=pat_metal))                       # parche
    # pececito arriba
    g.caja([5.2, 17.6, 4.4], [10.8, 20.4, 11.6], Mat((70, 160, 220), patron=pat_escamas))
    g.caja([5.4, 18.2, 11.6], [10.6, 20.0, 13.6], Mat((50, 120, 190)))
    g.caja([5.2, 18.2, 3.2], [10.8, 19.8, 4.4], Mat((236, 120, 60)))
    g.caja([4.8, 19.0, 4.8], [5.2, 19.6, 6.0], Mat((20, 20, 24)))
    g.caja([10.8, 19.0, 4.8], [11.2, 19.6, 6.0], Mat((20, 20, 24)))
    return g


GORROS = {'minero': minero, 'lenador': lenador, 'granjero': granjero, 'cazador': cazador, 'pescador': pescador}


def escribir(pack):
    base = os.path.join(pack, 'assets', '3y33')
    for sub in ('textures/gorro', 'models/gorro', 'items/gorro'): os.makedirs(os.path.join(base, sub), exist_ok=True)
    for j, f in GORROS.items():
        g = f()
        g.atlas.save(os.path.join(base, 'textures', 'gorro', j + '.png'))
        json.dump(g.modelo(), open(os.path.join(base, 'models', 'gorro', j + '.json'), 'w'), indent=1)
        json.dump({'model': {'type': 'minecraft:model', 'model': '3y33:gorro/' + j}}, open(os.path.join(base, 'items', 'gorro', j + '.json'), 'w'), indent=1)
    return len(GORROS)


if __name__ == '__main__':
    print(escribir(sys.argv[1]), 'gorros')
