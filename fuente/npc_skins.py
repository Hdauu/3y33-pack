"""Skins (64x64, brazos anchos) de los NPC de misiones: uno por trabajo.
Uso: python3 npc_skins.py <carpeta_pack>   (escribe assets/3y33/textures/npc/<trabajo>.png)
     python3 npc_skins.py hoja <salida.png>  (vista de frente y de espalda para revisar)"""
import sys, os
from PIL import Image

# (u, v, ancho, alto, profundidad) de cada parte: base y capa de arriba
PARTES = {
    'cabeza':   {'base': (0, 0, 8, 8, 8),    'capa': (32, 0, 8, 8, 8)},
    'cuerpo':   {'base': (16, 16, 8, 12, 4), 'capa': (16, 32, 8, 12, 4)},
    'brazo_d':  {'base': (40, 16, 4, 12, 4), 'capa': (40, 32, 4, 12, 4)},
    'brazo_i':  {'base': (32, 48, 4, 12, 4), 'capa': (48, 48, 4, 12, 4)},
    'pierna_d': {'base': (0, 16, 4, 12, 4),  'capa': (0, 32, 4, 12, 4)},
    'pierna_i': {'base': (16, 48, 4, 12, 4), 'capa': (0, 48, 4, 12, 4)},
}


def caras(u, v, w, h, d):
    """Rectangulos de cada cara dentro de la imagen: nombre -> (x, y, ancho, alto)."""
    return {
        'arriba': (u + d, v, w, d), 'abajo': (u + d + w, v, w, d),
        'derecha': (u, v + d, d, h), 'frente': (u + d, v + d, w, h),
        'izquierda': (u + d + w, v + d, d, h), 'espalda': (u + 2 * d + w, v + d, w, h),
    }


def sombra(c, k):
    return tuple(max(0, min(255, int(x * k))) for x in c[:3]) + ((c[3],) if len(c) > 3 else ())


class Skin:
    def __init__(self):
        self.im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def pintar(self, parte, capa, fn):
        """fn(cara, x, y, ancho, alto) -> color o None (transparente)."""
        u, v, w, h, d = PARTES[parte]['capa' if capa else 'base']
        for cara, (x0, y0, cw, ch) in caras(u, v, w, h, d).items():
            for y in range(ch):
                for x in range(cw):
                    c = fn(cara, x, y, cw, ch)
                    if c is not None: self.im.putpixel((x0 + x, y0 + y), tuple(c) + ((255,) if len(c) == 3 else ()))

    def todo(self, capa, fn):
        for p in PARTES: self.pintar(p, capa, lambda cara, x, y, w, h, p=p: fn(p, cara, x, y, w, h))


def lado(cara, c):
    """Oscurece un poco las caras de los costados, arriba y abajo para dar volumen."""
    return {'frente': c, 'espalda': sombra(c, .86), 'derecha': sombra(c, .92), 'izquierda': sombra(c, .92), 'arriba': sombra(c, 1.08), 'abajo': sombra(c, .7)}[cara]


PIEL = (221, 168, 128)
PIEL_O = (190, 138, 100)
OJO = (255, 255, 255)
PUPILA = (40, 44, 70)
BOCA = (150, 80, 70)


def cara_base(s, piel=PIEL, pelo=None, barba=None, mejillas=True):
    """Cabeza con ojos y boca. pelo/barba: color o None."""
    def f(cara, x, y, w, h):
        if cara != 'frente':
            c = lado(cara, piel)
            if pelo is not None and (cara in ('arriba',) or (cara in ('derecha', 'izquierda', 'espalda') and y <= 2)): c = lado(cara, pelo)
            if barba is not None and cara in ('derecha', 'izquierda') and y >= 5 and x >= 3: c = lado(cara, barba)
            return c
        c = piel
        if pelo is not None and y <= 1: c = pelo
        if pelo is not None and y == 2 and x in (0, 7): c = pelo
        if y == 4 and x in (1, 2, 5, 6): c = OJO
        if y == 4 and x in (2, 5): c = PUPILA
        if mejillas and y == 5 and x in (1, 6): c = sombra((240, 150, 140), 1.0)
        if barba is not None and ((y == 5 and x in (0, 1, 6, 7)) or y >= 6) and not (y == 6 and x in (3, 4) and False): c = barba
        if y == 6 and x in (3, 4) and barba is None: c = BOCA
        if y == 6 and x in (3, 4) and barba is not None: c = sombra(barba, .6)
        return c
    s.pintar('cabeza', False, f)


def minero():
    s = Skin()
    AMAR = (240, 190, 40); NARA = (232, 140, 28); GRIS = (62, 64, 74); NEGRO = (28, 26, 30)
    cara_base(s, pelo=(70, 46, 28))
    s.todo(False, lambda p, cara, x, y, w, h: None) if False else None
    # cuerpo y brazos: camisa gris oscura; piernas: pantalon gris; botas negras
    def base(p, cara, x, y, w, h):
        if p == 'cabeza': return None
        if p.startswith('pierna'): return lado(cara, NEGRO if y >= 9 else GRIS)
        if p.startswith('brazo'): return lado(cara, NEGRO if y >= 9 else NARA)
        return lado(cara, GRIS)
    for part in ('cuerpo', 'brazo_d', 'brazo_i', 'pierna_d', 'pierna_i'):
        s.pintar(part, False, lambda cara, x, y, w, h, part=part: base(part, cara, x, y, w, h))
    # capa: casco amarillo con lampara, mameluco naranja con franjas reflectantes
    def casco(cara, x, y, w, h):
        if cara == 'arriba': return lado(cara, AMAR)
        if cara == 'abajo': return None
        if cara == 'frente':
            if y <= 1: return AMAR
            if y == 2 and 0 < x < 7: return sombra(AMAR, .85)
            if y == 1 and x in (3, 4): return (210, 214, 220)
            return None
        if y <= 2: return lado(cara, AMAR)
        return None
    # (el casco ahora es un gorro 3D del pack)
    # la lampara: un cuadradito gris claro con brillo arriba del casco
    s.im.putpixel((8 + 3 + 32 - 0, 8), (200, 206, 214, 255)) if False else None
    def mameluco(cara, x, y, w, h):
        c = NARA
        if y in (7, 8) or y in (10, 11): c = (30, 30, 34) if y in (8, 11) else (236, 236, 150)
        if cara == 'abajo' or cara == 'arriba': return None
        return lado(cara, c)
    s.pintar('cuerpo', True, mameluco)
    s.pintar('brazo_d', True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y < 9 else lado(cara, NEGRO))
    s.pintar('brazo_i', True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y < 9 else lado(cara, NEGRO))
    for part in ('pierna_d', 'pierna_i'):
        s.pintar(part, True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y > 8 else lado(cara, NARA) if y not in (5, 6) else lado(cara, (236, 236, 150)))
    return s


def lenador():
    s = Skin()
    ROJO = (178, 38, 38); NEG = (36, 30, 32); JEAN = (62, 84, 134); BOTA = (92, 58, 34)
    cara_base(s, pelo=(98, 62, 36), barba=(110, 70, 40))
    def camisa(cara, x, y, w, h):
        c = ROJO if (x // 2 + y // 2) % 2 == 0 else NEG
        return lado(cara, c)
    for part in ('cuerpo', 'brazo_d', 'brazo_i'):
        s.pintar(part, False, lambda cara, x, y, w, h: lado(cara, (236, 224, 200)) if (y >= 10 and cara not in ('arriba', 'abajo')) and False else camisa(cara, x, y, w, h))
    for part in ('pierna_d', 'pierna_i'):
        s.pintar(part, False, lambda cara, x, y, w, h: lado(cara, BOTA if y >= 9 else JEAN))
    # manos con guantes y gorro rojo
    for part in ('brazo_d', 'brazo_i'):
        s.pintar(part, True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y < 10 else lado(cara, (90, 60, 36)))
    def gorro(cara, x, y, w, h):
        if cara == 'abajo': return None
        if cara == 'frente': return ROJO if y <= 1 else None
        if cara == 'arriba': return lado(cara, ROJO)
        return lado(cara, ROJO) if y <= 1 else None
    # cinturon con hebilla y pañuelo al cuello
    s.pintar('cuerpo', True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y not in (0, 9) else lado(cara, (236, 236, 236) if y == 0 else (50, 34, 22)))
    return s


def granjero():
    s = Skin()
    CAMISA = (240, 232, 210); OVER = (58, 100, 170); BOTA = (98, 64, 36); PAJA = (232, 202, 96)
    cara_base(s, piel=(232, 180, 140), pelo=(120, 78, 40))
    for part in ('cuerpo', 'brazo_d', 'brazo_i'):
        s.pintar(part, False, lambda cara, x, y, w, h: lado(cara, CAMISA))
    for part in ('pierna_d', 'pierna_i'):
        s.pintar(part, False, lambda cara, x, y, w, h: lado(cara, BOTA if y >= 9 else OVER))
    # overol azul con tiradores y bolsillo
    def over(cara, x, y, w, h):
        if cara in ('arriba', 'abajo'): return None
        if y >= 5: return lado(cara, OVER)
        if cara in ('frente', 'espalda') and x in (1, 6): return lado(cara, OVER)
        return None
    s.pintar('cuerpo', True, over)
    s.im.putpixel((16 + 4 + 3, 20 + 32 - 16 + 8), (240, 220, 120, 255)) if False else None
    for part in ('pierna_d', 'pierna_i'):
        s.pintar(part, True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y > 8 else lado(cara, OVER))
    # sombrero de paja con cinta marron
    def paja(cara, x, y, w, h):
        if cara == 'abajo': return None
        if cara == 'arriba': return lado(cara, PAJA)
        if y <= 1: return lado(cara, PAJA)
        if y == 2 and (cara != 'frente' or True): return lado(cara, (130, 70, 40))
        if cara == 'frente' and y == 3 and x in (0, 7): return lado(cara, PAJA)
        return None
    return s


def cazador():
    s = Skin()
    VERDE = (52, 78, 52); VERDE_O = (36, 56, 38); CUERO = (122, 82, 50); CINTO = (62, 42, 26)
    cara_base(s, piel=(214, 160, 120), mejillas=False)
    for part in ('cuerpo', 'brazo_d', 'brazo_i'):
        s.pintar(part, False, lambda cara, x, y, w, h: lado(cara, CUERO))
    for part in ('pierna_d', 'pierna_i'):
        s.pintar(part, False, lambda cara, x, y, w, h: lado(cara, CINTO if y >= 9 else VERDE_O))
    # capucha verde que deja ver los ojos + mascara oscura abajo
    def capucha(cara, x, y, w, h):
        if cara == 'abajo': return None
        if cara == 'frente':
            if y <= 2 or x in (0, 7): return lado(cara, VERDE)
            if y >= 5: return (46, 48, 54)
            return None
        return lado(cara, VERDE)
    s.pintar('cabeza', True, capucha)
    # capa verde sobre los hombros y correa cruzada
    def capa(cara, x, y, w, h):
        if cara in ('arriba', 'abajo'): return None
        if y <= 3: return lado(cara, VERDE)
        if cara in ('frente', 'espalda') and abs((x - 1) - (y - 4)) <= 0: return lado(cara, CINTO)
        if y in (9,): return lado(cara, CINTO)
        return None
    s.pintar('cuerpo', True, capa)
    for part in ('brazo_d', 'brazo_i'):
        s.pintar(part, True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y > 3 and y < 10 else (lado(cara, VERDE) if y <= 3 else lado(cara, CINTO)))
    return s


def pescador():
    s = Skin()
    AMAR = (238, 196, 38); AZUL = (34, 64, 112); GRIS = (214, 214, 220)
    cara_base(s, piel=(224, 172, 132), barba=GRIS)
    for part in ('cuerpo', 'brazo_d', 'brazo_i'):
        s.pintar(part, False, lambda cara, x, y, w, h: lado(cara, (40, 76, 120)))
    for part in ('pierna_d', 'pierna_i'):
        s.pintar(part, False, lambda cara, x, y, w, h: lado(cara, (30, 36, 44) if y >= 8 else AZUL))
    # impermeable amarillo con botones y capucha tipo sueste con ala
    def coat(cara, x, y, w, h):
        if cara in ('arriba', 'abajo'): return None
        c = AMAR
        if cara == 'frente' and x in (3, 4) and y in (2, 5, 8): c = (60, 50, 20)
        return lado(cara, c)
    s.pintar('cuerpo', True, coat)
    for part in ('brazo_d', 'brazo_i'):
        s.pintar(part, True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y > 8 else lado(cara, AMAR))
    s.pintar('pierna_d', True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y < 9 else lado(cara, (22, 26, 34)))
    s.pintar('pierna_i', True, lambda cara, x, y, w, h: None if cara in ('arriba', 'abajo') or y < 9 else lado(cara, (22, 26, 34)))
    def sueste(cara, x, y, w, h):
        if cara == 'abajo': return None
        if cara == 'arriba': return lado(cara, AMAR)
        if cara == 'frente': return lado(cara, AMAR) if y <= 1 else (sombra(AMAR, .8) if y == 2 else None)
        return lado(cara, AMAR) if y <= 2 else (lado(cara, AMAR) if cara == 'espalda' and y <= 4 else None)
    return s


SKINS = {'minero': minero, 'lenador': lenador, 'granjero': granjero, 'cazador': cazador, 'pescador': pescador}


def frente(im):
    """Vista de frente y de espalda del skin (para revisar)."""
    def parte(rect, flip=False):
        c = im.crop(rect)
        return c.transpose(Image.FLIP_LEFT_RIGHT) if flip else c
    out = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    def poner(base, capa, x, y):
        for r in (base, capa):
            out.alpha_composite(im.crop(r), (x, y))
    # frente
    poner((8, 8, 16, 16), (40, 8, 48, 16), 4, 0)
    poner((20, 20, 28, 32), (20, 36, 28, 48), 4, 8)
    poner((44, 20, 48, 32), (44, 36, 48, 48), 0, 8)
    poner((36, 52, 40, 64), (52, 52, 56, 64), 12, 8)
    poner((4, 20, 8, 32), (4, 36, 8, 48), 4, 20)
    poner((20, 52, 24, 64), (4, 52, 8, 64), 8, 20)
    return out


def hoja(salida, K=6):
    sh = Image.new('RGBA', (5 * (16 * K + 12) + 12, 32 * K + 24), (70, 74, 86, 255))
    for i, (n, f) in enumerate(SKINS.items()):
        im = frente(f().im).resize((16 * K, 32 * K), Image.NEAREST)
        sh.alpha_composite(im, (12 + i * (16 * K + 12), 12))
    sh.save(salida)


def escribir(pack):
    d = os.path.join(pack, 'assets', '3y33', 'textures', 'npc')
    os.makedirs(d, exist_ok=True)
    for n, f in SKINS.items(): f().im.save(os.path.join(d, n + '.png'))
    return len(SKINS)


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == 'hoja': hoja(sys.argv[2]); print('ok')
    else: print(escribir(sys.argv[1]), 'skins')
