"""Fondos de las ventanas de tiendas (resource pack, fuente 3y33:tienda). Un glifo por ventana, de 176 x alto (el juego no dibuja glifos de mas de 256 px).
Uso: python3 tienda_gui.py <carpeta de salida>"""
import sys, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import numpy as np
S = 8                      # supersampling
OSCURO = dict(bg1=(46, 24, 29), bg2=(24, 13, 16), rojo=(128, 26, 36), panel=(34, 18, 22), panel_borde=(70, 36, 42), rayado=(38, 20, 25),
              grupo=(52, 28, 34), grupo_borde=(150, 96, 52), hueco=(15, 8, 10), hueco_borde=(96, 50, 58), hueco_sombra=(6, 3, 4),
              cartel1=(62, 30, 36), cartel2=(30, 15, 19), friso=(92, 22, 32))
T = dict(OSCURO)
TEMAS = {
 'oscuro': OSCURO,
 'madera': dict(bg1=(222, 168, 104), bg2=(180, 120, 66), rojo=(158, 30, 42), panel=(204, 148, 88), panel_borde=(122, 72, 36), rayado=(196, 140, 82),
                grupo=(176, 116, 62), grupo_borde=(112, 66, 30), hueco=(126, 80, 44), hueco_borde=(88, 52, 28), hueco_sombra=(66, 38, 20),
                cartel1=(146, 44, 52), cartel2=(104, 28, 38), friso=(150, 34, 46)),
 'crema': dict(bg1=(250, 236, 208), bg2=(226, 200, 160), rojo=(172, 34, 46), panel=(240, 222, 188), panel_borde=(176, 126, 74), rayado=(234, 214, 178),
               grupo=(222, 196, 154), grupo_borde=(168, 118, 66), hueco=(204, 176, 138), hueco_borde=(150, 108, 66), hueco_sombra=(120, 84, 52),
               cartel1=(170, 40, 50), cartel2=(120, 26, 36), friso=(168, 34, 46)),
 'rojo': dict(bg1=(176, 40, 52), bg2=(118, 22, 34), rojo=(70, 10, 18), panel=(142, 32, 44), panel_borde=(214, 164, 65), rayado=(136, 28, 40),
              grupo=(116, 24, 36), grupo_borde=(214, 164, 65), hueco=(78, 14, 24), hueco_borde=(214, 164, 65), hueco_sombra=(50, 8, 14),
              cartel1=(96, 18, 28), cartel2=(60, 10, 18), friso=(84, 14, 24)),
}
def tema(nombre):
    T.clear(); T.update(TEMAS[nombre])
W = 176
FONT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fuentes', 'PressStart2P.ttf')

def px(v): return int(round(v * S))
def box(x0, y0, x1, y1): return [px(x0), px(y0), px(x1) - 1, px(y1) - 1]

def vgrad(w, h, top, bot):
    t = np.linspace(0, 1, h)[:, None, None]
    a = (np.array(top)[None, None, :] * (1 - t) + np.array(bot)[None, None, :] * t)
    return Image.fromarray(np.repeat(a, w, axis=1).astype('uint8'), 'RGB').convert('RGBA')

def generar(filas, texto, botones, salida, grupos=(), toldo=True):
    """filas: filas de la ventana (3 o 4). botones: set de (fila, columna) que llevan marco. Las filas se cuentan desde 0 (la 0 es el banner)."""
    H = 114 + 18 * filas
    img = Image.new('RGBA', (W * S, H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # fondo y bordes
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box(0, 0, W, H), radius=px(4), fill=255)
    img.paste(vgrad(W * S, H * S, T['bg1'], T['bg2']), (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(box(0, 0, W, H), radius=px(4), outline=(10, 5, 7, 255), width=px(1))
    d.rounded_rectangle(box(1, 1, W - 1, H - 1), radius=px(3), outline=T['rojo'] + (255,), width=px(1))
    d.rounded_rectangle(box(2, 2, W - 2, H - 2), radius=px(2.5), outline=(214, 164, 65, 255), width=px(1))

    if toldo:
        # toldo
        ax0, ax1, ay0, ay1, n = 7, 169, 4, 13, 18
        sw = (ax1 - ax0) / n
        awn = Image.new('RGBA', img.size, (0, 0, 0, 0)); ad = ImageDraw.Draw(awn)
        for i in range(n):
            x0 = ax0 + i * sw
            col = (196, 40, 52, 255) if i % 2 == 0 else (246, 236, 216, 255)
            ad.rectangle(box(x0, ay0, x0 + sw, ay1), fill=col)
            ad.pieslice(box(x0, ay1 - sw / 2, x0 + sw, ay1 + sw / 2), 0, 180, fill=col)
        a = np.array(awn).astype(float)
        ymap = np.linspace(0, 1, H * S)[:, None]
        k = 1.0 - 0.38 * np.clip((ymap - ay0 / H) / ((ay1 + 3) / H - ay0 / H), 0, 1)
        for c in range(3): a[:, :, c] = a[:, :, c] * k
        awn = Image.fromarray(a.astype('uint8'), 'RGBA')
        sh = Image.new('RGBA', img.size, (0, 0, 0, 0))
        sh.putalpha(awn.split()[3].filter(ImageFilter.GaussianBlur(px(1.2))).point(lambda v: int(v * 0.55)))
        img.alpha_composite(sh, (0, px(1.2))); img.alpha_composite(awn)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle(box(6, 3, 170, 5), radius=px(1), fill=(92, 52, 30, 255), outline=(40, 22, 14, 255), width=px(1))
        d.line([px(7), px(3.7), px(169), px(3.7)], fill=(150, 98, 58, 255), width=px(1))



    # panel de contenido liso, con marcos solo detras de los botones
    top, bot = (35 if toldo else 21), 17 + 18 * filas + 0.5   # sin toldo: la linea del panel sube para no tocar los marcos
    d.rounded_rectangle(box(6, top, 170, bot), radius=px(2), fill=T['panel'] + (255,), outline=T['panel_borde'] + (255,), width=px(1))
    for y in range(int(top) + 3, int(bot) - 1, 6):          # rayado muy suave
        d.line([px(7), px(y), px(169), px(y)], fill=T['rayado'] + (255,), width=px(1))
    # recuadros de grupo: (fila0, col0, fila1, col1) inclusive
    for (r0, c0, r1, c1) in grupos:
        d.rounded_rectangle(box(7 + 18 * c0 - 1.5, 17 + 18 * r0 - 1.5, 7 + 18 * (c1 + 1) + 1.5, 17 + 18 * (r1 + 1) + 1.5), radius=px(3),
                            fill=T['grupo'] + (255,), outline=T['grupo_borde'] + (255,), width=px(1))
    def marco(x, y):
        d.rounded_rectangle(box(x, y, x + 18, y + 18), radius=px(1.4), fill=T['hueco'] + (255,), outline=T['hueco_borde'] + (255,), width=px(1))
        d.line([px(x + 1), px(y + 1.5), px(x + 17), px(y + 1.5)], fill=T['hueco_sombra'] + (255,), width=px(1))
    for (r, c) in botones:
        marco(7 + 18 * c, 17 + 18 * r)

    # cartel con el nombre de la ventana (si no hay texto, solo un ornamento dorado: la ventana es obvia sin titulo)
    if texto:
        font = ImageFont.truetype(FONT, px(16))
        tw = d.textlength(texto, font=font) / S
        ancho = max(100, tw + 20)
        cy0, cy1 = (16, 36) if toldo else (6, 26)
        cx0, cx1 = (W - ancho) / 2, (W + ancho) / 2
        d.rounded_rectangle(box(cx0 + 0.6, cy0 + 1, cx1 + 0.6, cy1 + 1), radius=px(3), fill=(0, 0, 0, 110))
        pl = vgrad(px(cx1 - cx0), px(cy1 - cy0), T['cartel1'], T['cartel2'])
        pm = Image.new('L', pl.size, 0)
        ImageDraw.Draw(pm).rounded_rectangle([0, 0, pl.size[0] - 1, pl.size[1] - 1], radius=px(3), fill=255)
        img.paste(pl, (px(cx0), px(cy0)), pm)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle(box(cx0, cy0, cx1, cy1), radius=px(3), outline=(214, 164, 65, 255), width=px(1))
        d.rounded_rectangle(box(cx0 + 1.6, cy0 + 1.6, cx1 - 1.6, cy1 - 1.6), radius=px(2), outline=(120, 80, 30, 255), width=px(1))
        for rx, ry in ((cx0 + 3.2, cy0 + 3.2), (cx1 - 3.2, cy0 + 3.2), (cx0 + 3.2, cy1 - 3.2), (cx1 - 3.2, cy1 - 3.2)):
            d.ellipse(box(rx - 0.9, ry - 0.9, rx + 0.9, ry + 0.9), fill=(238, 190, 80, 255), outline=(120, 80, 30, 255), width=px(1))
        tx = (W * S - tw * S) / 2; ty = px(cy0 + 2.2)
        d.text((tx + px(0.9), ty + px(0.9)), texto, font=font, fill=(0, 0, 0, 200))
        tm = Image.new('L', img.size, 0); ImageDraw.Draw(tm).text((tx, ty), texto, font=font, fill=255)
        img.paste(vgrad(img.size[0], img.size[1], (255, 232, 140), (226, 150, 42)), (0, 0), tm)
        d = ImageDraw.Draw(img)
        def moneda(cx, cy, r):
            d.ellipse(box(cx - r - 0.5, cy - r + 0.2, cx + r + 0.5, cy + r + 0.9), fill=(0, 0, 0, 90))
            d.ellipse(box(cx - r, cy - r, cx + r, cy + r), fill=(176, 121, 34, 255), outline=(92, 58, 14, 255), width=px(1))
            d.ellipse(box(cx - r + 0.7, cy - r + 0.7, cx + r - 0.7, cy + r - 0.7), fill=(246, 196, 76, 255))
            d.ellipse(box(cx - r + 2.0, cy - r + 2.0, cx + r - 2.0, cy + r - 2.0), outline=(190, 134, 40, 255), width=px(1))
            d.line([px(cx - r * 0.45), px(cy + r * 0.25), px(cx + r * 0.1), px(cy - r * 0.5)], fill=(255, 238, 170, 255), width=px(1))
        if cx0 > 32:
            moneda(19, 28.5, 6.2); moneda(11, 25.5, 4.2); moneda(157, 28.5, 6.2); moneda(165, 25.5, 4.2)
    else:
        oro, oro_o, rojo = (238, 190, 80, 255), (120, 80, 30, 255), (210, 40, 60, 255)
        cy = 11
        for lado in (-1, 1):
            x0, x1 = 88 + lado * 8, 88 + lado * 46
            d.line([px(x0), px(cy), px(x1), px(cy)], fill=oro, width=px(1))
            d.line([px(x0 + lado * 2), px(cy + 1.4), px(x1 - lado * 6), px(cy + 1.4)], fill=oro_o, width=px(0.8))
            for k, r in ((46, 1.5), (34, 1.0), (22, 0.8)):
                d.polygon([(px(88 + lado * k), px(cy - r - .4)), (px(88 + lado * k + r + .4), px(cy)), (px(88 + lado * k), px(cy + r + .4)), (px(88 + lado * k - r - .4), px(cy))], fill=oro)
        d.polygon([(px(88), px(cy - 5)), (px(88 + 5), px(cy)), (px(88), px(cy + 5)), (px(88 - 5), px(cy))], fill=(40, 12, 18, 255), outline=oro, width=px(1))
        d.polygon([(px(88), px(cy - 2.6)), (px(88 + 2.6), px(cy)), (px(88), px(cy + 2.6)), (px(88 - 2.6), px(cy))], fill=rojo)
        for (sx, sy) in ((60, 6), (116, 6)):
            d.line([px(sx), px(sy - 1.6), px(sx), px(sy + 1.6)], fill=(255, 236, 170, 255), width=px(0.6))
            d.line([px(sx - 1.6), px(sy), px(sx + 1.6), px(sy)], fill=(255, 236, 170, 255), width=px(0.6))

    # separador y etiqueta clara para el "Inventory" que escribe el juego
    sep = 17 + 18 * filas + 2
    d.line([px(8), px(sep), px(168), px(sep)], fill=T['rojo'] + (255,), width=px(1))
    d.line([px(8), px(sep + 1), px(168), px(sep + 1)], fill=(214, 164, 65, 120), width=px(1))
    d.rounded_rectangle(box(5.5, sep - 0.6, 62, sep + 10.6), radius=px(2.2), fill=(222, 208, 182, 255), outline=(120, 80, 30, 255), width=px(1))
    # inventario del jugador (y = 85 + 18*(filas-3) para el primer renglon, hotbar 58 mas abajo)
    base = 84 + 18 * (filas - 3)
    for r in range(3):
        for c in range(9): marco(7 + 18 * c, base + 18 * r)
    for c in range(9): marco(7 + 18 * c, base + 58)

    out = img.resize((W, H), Image.LANCZOS)
    out.save(salida)
    prev = Image.new('RGBA', out.size, (120, 120, 120, 255)); prev.alpha_composite(out)
    prev.resize((W * 4, H * 4), Image.NEAREST).save(salida.replace('.png', '_vista.png'))
    return out.size

if __name__ == '__main__':
    carpeta = sys.argv[1] if len(sys.argv) > 1 else '.'
    # botones de cada ventana (fila, columna): fila 0 = banner
    tienda = {(1, c) for c in range(1, 8)} | {(2, 0), (2, 4), (2, 6), (2, 8)}
    ajustes = ({(1, 1), (1, 2), (2, 1), (2, 2)}                                   # precios y bloqueos (arriba compra, abajo venta)
               | {(1, 4), (2, 4), (3, 4), (4, 4)}                                  # alertas y holograma
               | {(1, 6), (1, 7), (2, 6), (2, 7), (3, 6), (3, 7)}                  # gestion y copiar/pegar
               | {(4, 0)})                                                         # volver
    grupos = [(1, 1, 2, 2), (1, 4, 4, 4), (1, 6, 2, 7), (3, 6, 3, 7)]
    print(generar(3, 'TIENDA', tienda, os.path.join(carpeta, 'tienda_gui.png')))
    print(generar(5, 'AJUSTES', ajustes, os.path.join(carpeta, 'ajustes_gui.png'), grupos))
    admins = {(1, c) for c in range(1, 8)} | {(2, c) for c in range(1, 8)} | {(3, 0), (3, 4)}
    print(generar(4, 'ADMINS', admins, os.path.join(carpeta, 'admins_gui.png'), [(1, 1, 2, 7)]))
    historial = {(1, c) for c in range(1, 8)} | {(2, c) for c in range(1, 8)} | {(3, c) for c in range(1, 8)} | {(4, 0)}
    # trabajos: principal (cartas en las filas 1 a 3, pie con info y reloj) y detalle (carta al centro)
    tema('crema')
    trabajos = {(5, 0), (5, 3), (5, 5), (5, 8)}
    print(generar(6, '', trabajos, os.path.join(carpeta, 'trabajos_gui.png'), [(0.6278, 0, 3.8333, 8)], toldo=False))
    detalle = {(1, 1), (2, 1), (3, 1), (1, 7), (5, 0), (5, 8)}
    print(generar(6, '', detalle, os.path.join(carpeta, 'trabajo_gui.png'), [(0.6278, 3, 3.8333, 5)], toldo=False))
    # acciones (lista de 28 casilleros + pie con volver, reloj y flechas) y confirmacion (3 filas, tres botones)
    acciones = {(r, c) for r in range(1, 5) for c in range(1, 8)} | {(5, 0), (5, 3), (5, 4), (5, 5)}
    print(generar(6, '', acciones, os.path.join(carpeta, 'acciones_gui.png'), [], toldo=False))
    print(generar(3, '', {(1, 2), (1, 4), (1, 6)}, os.path.join(carpeta, 'confirmar_gui.png'), [], toldo=False))
    tema('oscuro')
    print(generar(5, 'HISTORIAL', historial, os.path.join(carpeta, 'historial_gui.png'), [(1, 1, 3, 7)]))
