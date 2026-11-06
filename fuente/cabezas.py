import sys,json,os; sys.path.insert(0,'/home/claude/3y33-pack/fuente')
import vista3d, npc_gorros as g
from PIL import Image
S='/tmp/claude-0/-home-claude/e0984caa-d1b0-588b-b4d0-ef103e7d4264/scratchpad/pv'
def run(angs=(20,160,-60)):
    W=360
    sh=Image.new('RGBA',(W*len(angs),W*len(g.GORROS)))
    for r,j in enumerate(g.GORROS):
        G=g.GORROS[j](); G.atlas.save(f'{S}/g_{j}.png'); sk=Image.open(f'{S}/{j}.png')
        for c,a in enumerate(angs):
            e=vista3d.Escena(); vista3d.maniqui(e,sk,G.modelo(),G.atlas); e.dibujar('/tmp/_z.png',yaw=a,pit=15,K=20,tam=(W,W),cyr=(180+31*20)/W)
            sh.alpha_composite(Image.open('/tmp/_z.png'),(c*W,r*W))
    sh.save(S+'/cabezas.png')
if __name__=='__main__': run()
