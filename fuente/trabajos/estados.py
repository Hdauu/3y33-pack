import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pixel3
from pixel3 import carta, ACC, ICON, CW, CH, ESTILOS, lighten, darken, GOLD, RED
from PIL import Image, ImageDraw, ImageFont, ImageChops, ImageEnhance

FONT = ''+os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','fuentes','PressStart2P.ttf')+''

def activa(img, acc):
    img = img.copy(); d = ImageDraw.Draw(img)
    d.rectangle([1, 1, CW - 2, CH - 2], outline=lighten(acc, .45) + (255,))
    d.rectangle([2, 2, CW - 3, CH - 3], outline=acc + (255,))
    for (x, y) in ((3, 3), (CW - 4, 3), (3, CH - 4), (CW - 4, CH - 4)): d.point((x, y), fill=(255, 255, 255, 255))
    # sellito "activo": gema luminosa arriba
    cx = CW // 2
    d.polygon([(cx, 1), (cx + 4, 5), (cx, 9), (cx - 4, 5)], fill=(60, 200, 90, 255), outline=(255, 255, 255, 255))
    d.point((cx, 4), fill=(210, 255, 220, 255)); d.point((cx, 5), fill=(120, 240, 150, 255))
    for (x, y) in ((0, 0), (CW - 1, 0), (0, CH - 1), (CW - 1, CH - 1)): img.putpixel((x, y), (0, 0, 0, 0))
    return img

def bloqueada(img):
    a = img.split()[3]
    g = ImageEnhance.Color(img.convert('RGB')).enhance(0.08)
    g = ImageEnhance.Brightness(g).enhance(0.62).convert('RGBA'); g.putalpha(a)
    d = ImageDraw.Draw(g); cx = CW // 2; cy = 34
    # candado
    d.rectangle([cx - 5, cy - 1, cx + 5, cy + 9], fill=(150, 150, 158, 255), outline=(20, 20, 24, 255))
    d.rectangle([cx - 4, cy, cx + 4, cy + 1], fill=(190, 190, 198, 255))
    d.line([(cx - 3, cy - 1), (cx - 3, cy - 5)], fill=(20, 20, 24, 255)); d.line([(cx + 3, cy - 1), (cx + 3, cy - 5)], fill=(20, 20, 24, 255))
    d.line([(cx - 2, cy - 7), (cx + 2, cy - 7)], fill=(20, 20, 24, 255))
    d.line([(cx - 2, cy - 6), (cx - 2, cy - 1)], fill=(150, 150, 158, 255)); d.line([(cx + 2, cy - 6), (cx + 2, cy - 1)], fill=(150, 150, 158, 255))
    d.line([(cx - 1, cy - 6), (cx + 1, cy - 6)], fill=(150, 150, 158, 255))
    d.rectangle([cx - 1, cy + 3, cx + 1, cy + 6], fill=(30, 30, 36, 255))
    return g

def hoja(estilo, salida, K=3):
    pad = 10; lab = 16
    cols = list(ACC); filas = ['NORMAL', 'ACTIVO', 'BLOQUEADO']
    W = pad + len(cols) * (CW * K + pad); H = pad + len(filas) * (CH * K + pad)
    sheet = Image.new('RGBA', (W + 0, H), (30, 12, 18, 255))
    for r, f in enumerate(filas):
        for j, n in enumerate(cols):
            c = carta(n, estilo)
            if f == 'ACTIVO': c = activa(c, ACC[n])
            elif f == 'BLOQUEADO': c = bloqueada(c)
            c.save(os.path.join(os.path.dirname(salida), '%s_%s_%s.png' % (estilo, ICON[n], f.lower())))
            sheet.alpha_composite(c.resize((CW * K, CH * K), Image.NEAREST), (pad + j * (CW * K + pad), pad + r * (CH * K + pad)))
    sheet.save(salida)

if __name__ == '__main__':
    base = os.path.dirname(os.path.abspath(__file__))
    hoja('oro_rayos', os.path.join(base, 'estados_sin_anillo.png'))
    hoja('oro_rayos_anillo', os.path.join(base, 'estados_con_anillo.png'))
    print('ok')
