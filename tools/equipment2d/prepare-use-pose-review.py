"""Prepare native Blockbench-only use-pose fixtures from current admitted geometry."""
import copy
import hashlib
import json
import uuid
from pathlib import Path
import base64

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "art/equipment-2d/blockbench-holds-20261021"
OUT = WORK / "grip-audit/use-poses"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    (OUT / "projects").mkdir(parents=True, exist_ok=True)
    routes = {e["assetId"]: e for e in read(ROOT / "src/main/resources/assets/eldencombat/routes/2d.json")["equipment"] if e.get("route") == "own_sprite"}
    jobs = []
    for name, states in (("great_bow", [("standby",0),("pulling_0",.3),("pulling_1",.65),("pulling_2",1)]),
                         ("ballista", [("standby",0),("loading_0",.3),("loading_1",.65),("loading_2",1),("loaded",1)])):
        base = read(WORK / "grip-audit/candidates/exports" / (name+".json"))["display"]
        for state, progress in states:
            stem = name if state == "standby" else name+"__"+state
            model = read(WORK / "projects" / (stem+".bbmodel"))
            model["display"] = copy.deepcopy(base)
            target = OUT / "projects" / (stem+".bbmodel")
            target.write_text(json.dumps(model), encoding="utf-8")
            for hand in ("right","left"):
                for person in ("first","third"):
                    jobs.append(dict(id=stem, kind="bow" if name == "great_bow" else "crossbow", state=state,
                        progress=progress, context=person+"person_"+hand+"hand", path=str(target).replace("\\","/")))
    thickness = read(ROOT / "art/item-thickness/manifest.json")["models"]
    for name in ("shield","small_shield","great_shield"):
        source = next(m for m in thickness if m["id"] == "eldencombat:"+name and m["state"] == "front")
        geometry = read(ROOT / "art/item-thickness" / source["file"])
        textures = []
        for face in ("front","back"):
            path = ROOT / "art/equipment-2d/full/sprites" / (name+"_"+face+".png")
            textures.append(dict(name=path.name, id=str(len(textures)), uuid=str(uuid.uuid4()),
                width=32, height=32, uv_width=32, uv_height=32,
                source="data:image/png;base64,"+base64.b64encode(path.read_bytes()).decode()))
        cubes = []
        for index, cube in enumerate(geometry["elements"]):
            faces = {f: dict(uv=[0,0,0,0], texture=None) for f in ("north","south","east","west","up","down")}
            faces.update({f: dict(uv=[v*2 for v in p["uv"]], texture=0 if p["texture"] == "#front" else 1)
                     for f,p in cube["faces"].items()})
            cubes.append(dict(name="contour_"+str(index), uuid=str(uuid.uuid4()), type="cube", box_uv=False,
                autouv=0, origin=[0,0,0], **{"from":cube["from"],"to":cube["to"],"faces":faces}))
        for blocking in (False,True):
            stem=name+("_blocking" if blocking else "_rest")
            display=routes[name]["displayBlocking"] if blocking else routes[name]["display"]
            candidate=WORK/"grip-audit/candidates/exports"/(name+"_blocking.json")
            if blocking and candidate.exists():
                display=read(candidate)["display"]
            model=dict(meta=dict(format_version="5.0",model_format="java_block",box_uv=False),name=stem,
                resolution=dict(width=32,height=32),elements=cubes,outliner=[c["uuid"] for c in cubes],textures=textures,
                display=display,unhandled_root_fields=dict(reviewOnly=True,dualMaterial=True,runtimeAdopted=False))
            target=OUT/"projects"/(stem+".bbmodel");target.write_text(json.dumps(model),encoding="utf-8")
            for hand in ("right","left"):
                for person in ("first","third"):
                    for side in (("front","back") if person == "third" else ("view",)):
                        jobs.append(dict(id=stem,kind="shield",state="blocking" if blocking else "rest",
                            context=person+"person_"+hand+"hand",side=side,path=str(target).replace("\\","/")))
    source=ROOT/"build/ItemInHandRenderer-holds-source.java"
    (OUT/"jobs.json").write_text(json.dumps(dict(jobs=jobs,vanillaSourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        scope="Native Blockbench replay of vanilla use transforms; not a Minecraft run",runtimeAccepted=False),indent=2),encoding="utf-8")
    print("Native use-pose jobs:",len(jobs))


if __name__ == "__main__":
    main()
