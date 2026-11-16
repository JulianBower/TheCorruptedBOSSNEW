import json, numpy as np, os
from PIL import Image
from common import *
from paint import hexc, hash3
os.makedirs('out',exist_ok=True)
# ---------- gauntlet (16x16) from the block-model json
gj=json.load(open('gauntlet_of_hell.json'))
tex=np.zeros((16,16,4),np.uint8); gl=np.zeros((16,16,4),np.uint8)
roles=['grip','main','plate','base']
S=0.55
GQ=[]
def gpt(p):   # block px -> blockbench space relative to hand origin
    gx,gy,gz=(p[0]-8)*S,(p[1]-1.3)*S,(p[2]-8)*S
    return np.array([gz-0.07,gy+3.55,-gx-1.9])
for i,e in enumerate(gj['elements']):
    f,t=e['from'],e['to']
    for fn,fd in e['faces'].items():
        u1,v1,u2,v2=fd['uv']
        x0,x1=int(round(min(u1,u2))),int(round(max(u1,u2))); y0,y1=int(round(min(v1,v2))),int(round(max(v1,v2)))
        x1=max(x1,x0+1); y1=max(y1,y0+1)
        for y in range(y0,min(y1,16)):
            for x in range(x0,min(x1,16)):
                r=roles[i]; n=(hash3(x,y,i,3)-0.5)*14
                base={'grip':hexc('2A2C32'),'main':hexc('5A606A'),'plate':hexc('2E3036'),'base':hexc('43484F')}[r]
                c=base+n
                if x in (x0,x1-1) or y in (y0,y1-1): c=c*0.78
                if r=='main' and fn in('north','south','east','west') and y==y0+1: c=hexc('828994'); 
                tex[y,x]=(*np.clip(c,0,255).astype(np.uint8),255)
                if (r=='base' and (x+y)%4==0) or (r=='main' and fn!='up' and fn!='down' and y==y1-1) or (r=='plate' and fn in('up','down') and (x+y)%3==0):
                    tex[y,x]=(217,30,24,255); gl[y,x]=(255,59,48,255)
        cs=np.array([gpt(c) for c in face_corners(f,t,fn)])
        GQ.append(dict(bb=cs,uv=[u1/16,v1/16,u2/16,v2/16],nbb=np.array(NORM[fn],float)))
pass  # gauntlet.png is the user's hand-painted 32x32 texture (tools/gauntlet.png) - not regenerated
# ---------- effect icon 18x18
ic=np.zeros((18,18,4),np.uint8)
for y in range(18):
    for x in range(18):
        d=((x-8.5)**2+(y-8.5)**2)**.5
        if d<8.2: ic[y,x]=(24,6,8,255)
        if 7.0<d<8.2: ic[y,x]=(217,30,24,255)
bolt=[(10,2),(7,6),(10,8),(6,12),(8,12),(6,16)]
for (a,b),(c,d) in zip(bolt,bolt[1:]):
    for t in np.linspace(0,1,12):
        x=int(round(a+(c-a)*t)); y=int(round(b+(d-b)*t)); ic[y,x]=(255,59,48,255)
Image.fromarray(ic,'RGBA').save('out/corruption.png')
Image.new('RGBA',(16,16),(0,0,0,255)).save('out/void_sphere.png')
Image.new('RGBA',(16,16),(255,255,255,255)).save('out/corruption_bolt.png')
# ---------- java mesh
quads=bake()
def fmt(v): 
    s=('%.4f'%v).rstrip('0').rstrip('.'); return (s if s not in('','-0') else '0')+'F'
def qline(P,uvs,n,piv):
    pm=bb2m(np.array(P))-np.array(piv); nm=nbb2m(n); nm=nm/ (np.linalg.norm(nm) or 1)
    out=[]
    for i in range(4): out+= [pm[i][0],pm[i][1],pm[i][2],uvs[i][0],uvs[i][1]]
    out+=list(nm); return ','.join(fmt(v) for v in out)
piv={b:bb2m(np.array(p)) for b,p in PIVOT_BB.items()}
def uvcorners(u1,v1,u2,v2): return [(u1,v1),(u2,v1),(u2,v2),(u1,v2)]
J=['package com.hellgauntlet.boss.client;','',"/** Generated from prototype_the_corrupted.bbmodel: baked quads per bone. Each quad = 4 x (x,y,z,u,v) + normal. Positions are model pixels relative to the bone pivot. */",'final class CorruptedMesh {','    private CorruptedMesh() {}','']
for bone in ['TORSO','HEAD','R_ARM','L_ARM','R_LEG','L_LEG']:
    qs=[q for q in quads if q['bone']==bone]
    J.append(f'    static final class {bone} {{ static final float[] D = {{')
    for q in qs:
        u1,v1,u2,v2=[c/TEX for c in q['uv']]
        J.append('        '+qline(q['bb'],uvcorners(u1,v1,u2,v2),q['nbb'],piv[bone])+',')
    J.append('    } ; }\n')
J.append('    static final class GAUNTLET { static final float[] D = {')
for q in GQ:
    u1,v1,u2,v2=q['uv']
    J.append('        '+qline(q['bb'],uvcorners(u1,v1,u2,v2),q['nbb'],piv['R_ARM'])+',')
J.append('    } ; }\n')
def pv(b): return ', '.join(fmt(v) for v in piv[b])
J.append('    /** Absolute bone pivots in model pixels (x, y, z). */')
for b in piv: J.append(f'    static final float[] {b}_PIVOT = {{ {pv(b)} }};')
J.append('}')
os.makedirs('out/java',exist_ok=True); open('out/java/CorruptedMesh.java','w').write('\n'.join(J)+'\n')
print({b:len([q for q in quads if q['bone']==b]) for b in piv}, [round(v,2) for v in piv['R_ARM']])
