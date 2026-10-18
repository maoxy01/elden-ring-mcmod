import importlib.util,json,itertools
from pathlib import Path
import numpy as np
root=Path('D:/codexout/mcmod'); work=root/'build/firstperson-framing-20261018'
spec=importlib.util.spec_from_file_location('cal',root/'tools/equipment2d/calibrate-first-person.py'); m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
routes=json.loads((root/'src/main/resources/assets/eldencombat/routes/2d.json').read_text(encoding='utf8'))
profiles={e['id']:e for e in json.loads((root/'art/weapon-proportions/profiles.json').read_text(encoding='utf8'))['entries']}
grips={}
for filename in ('sword-grip-regions.json','utility-grip-regions.json'):
 for g in json.loads((root/'art/weapon-proportions'/filename).read_text(encoding='utf8'))['entries']:
  grips[g['id']]=np.array(g.get('handleCenterModelUnits',g.get('gripCenterModelUnits')))/16
checks=[]
for e in routes['equipment']:
 if e['route']!='provider_model':continue
 p=profiles[e['id']]; bow=e['kind']=='bow'; b=p['sourceBoundsBlocks']; pts=np.array(list(itertools.product(*zip(b['min'],b['max']))));grip=grips.get(e['id'],np.array(p['mainGripModelUnits'])/16)
 length=min(1.10,p['ratioToPlayer']*1.8); scale=length/(b['max'][1]-b['min'][1])
 for left in (False,True):
  rotation=m.maths.rotation_xyz(0,25 if left else -25,35 if left else 55)[:3,:3] @ m.maths.rot_z(np.radians(-45))[:3,:3]
  local=(pts-grip)*scale;local[:,2]*=.68/scale
  cam=(rotation@local.T).T+np.array([-.44 if left else .44,-.04 if bow else -.28,-1.17])
  projected=m.project(cam,4/3); box=[float(projected[:,0].min()),float(projected[:,1].min()),float(projected[:,0].max()),float(projected[:,1].max())]
  checks.append({'id':e['id'],'left':left,'bounds4to3':box,'withinScreen':bool((np.abs(projected)<=1).all() and (cam[:,2]<-.1).all())})
print(json.dumps(checks,separators=(',',':')))
(work/'legacy-projection-checks.json').write_text(json.dumps({'runtimeVerified':False,'method':'Resting-pose source bounding boxes, production first-person branch constants','checks':checks},indent=2)+'\n',encoding='utf8')
assert all(c['withinScreen'] for c in checks),'Legacy projection outside screen'
