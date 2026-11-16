import json, numpy as np
SRC='prototype_the_corrupted.bbmodel'
TEX=512            # real texture size; uv in the file is in 64-space
UVS=TEX/64.0       # 8 px per uv unit
SHIFT_Z=0.4        # centre line of the model in blockbench z
SHIFT_X=-0.17      # torso centre in blockbench x
BONE_OF={'Left_Leg':'L_LEG','Right_Leg':'R_LEG','Torso':'TORSO','Left_Arm':'L_ARM','Right_arm':'R_ARM','Head':'HEAD'}
# pivots in blockbench space (x,y,z)
PIVOT_BB={'TORSO':(-0.17,3.7,0.4),'HEAD':(-0.3,7.9,0.4),'R_ARM':(-0.07,7.2,-2.0),'L_ARM':(-0.07,7.2,2.8),
          'R_LEG':(0.0,3.7,-0.35),'L_LEG':(0.0,3.7,1.15)}
def bb2m(p):
    p=np.asarray(p,float)
    return np.array([p[...,2]-SHIFT_Z, 24.0-p[...,1], p[...,0]-SHIFT_X]).T if p.ndim==1 else np.stack([p[:,2]-SHIFT_Z,24.0-p[:,1],p[:,0]-SHIFT_X],1)
def nbb2m(n):
    n=np.asarray(n,float); return np.array([n[2],-n[1],n[0]])
def rot(r):
    rx,ry,rz=[np.radians(a) for a in r]
    cx,sx,cy,sy,cz,sz=np.cos(rx),np.sin(rx),np.cos(ry),np.sin(ry),np.cos(rz),np.sin(rz)
    Rx=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]]);Ry=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]);Rz=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
    return Rz@Ry@Rx
def face_corners(f,t,fn):
    fx,fy,fz=f; tx,ty,tz=t
    return {'north':[(tx,ty,fz),(fx,ty,fz),(fx,fy,fz),(tx,fy,fz)],
            'south':[(fx,ty,tz),(tx,ty,tz),(tx,fy,tz),(fx,fy,tz)],
            'east':[(tx,ty,tz),(tx,ty,fz),(tx,fy,fz),(tx,fy,tz)],
            'west':[(fx,ty,fz),(fx,ty,tz),(fx,fy,tz),(fx,fy,fz)],
            'up':[(fx,ty,fz),(tx,ty,fz),(tx,ty,tz),(fx,ty,tz)],
            'down':[(fx,fy,tz),(tx,fy,tz),(tx,fy,fz),(fx,fy,fz)]}[fn]
NORM={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
def bake():
    bb=json.load(open(SRC))
    els={e['uuid']:e for e in bb['elements']}; groups={g['uuid']:g for g in bb['groups']}
    quads=[]
    for o in bb['outliner']:
        bone=BONE_OF[groups[o['uuid']]['name']]
        for ci,cu in enumerate(o['children']):
            e=els[cu]; M=rot(e['rotation']); org=np.array(e['origin'])
            for fn,fd in e['faces'].items():
                u1,v1,u2,v2=[c*UVS for c in fd['uv']]
                cs=np.array([M@(np.array(c)-org)+org for c in face_corners(e['from'],e['to'],fn)])  # bb space
                n=M@np.array(NORM[fn])
                quads.append(dict(bone=bone,cube=cu,ci=ci,face=fn,uv=[u1,v1,u2,v2],bb=cs,nbb=n,
                                  size=(np.array(e['to'])-np.array(e['from'])),cb=(cs.mean(0))))
    return quads
