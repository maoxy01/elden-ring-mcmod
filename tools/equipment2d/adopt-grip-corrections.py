"""Promote the saved native grip corrections into source inputs, never into a running game."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "art/equipment-2d/blockbench-holds-20261021"
CANDIDATES = WORK / "grip-audit/candidates"
OVERRIDES = ROOT / "art/equipment-2d/full/display-overrides.json"
DEFAULTS = {"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")


def frame(value):
    return {key:value.get(key, default) for key,default in DEFAULTS.items()}


def main():
    plan = read(CANDIDATES / "frame-changes.json")
    native = {r["id"]:r for r in read(CANDIDATES / "native-review.json")["items"]}
    use = read(WORK / "grip-audit/use-poses/native-review.json")
    assert len(use["items"]) == 72 and all(r["nonempty"] and r["savedDisplayUnchanged"] for r in use["items"])
    current = read(ROOT / "src/main/resources/assets/eldencombat/routes/2d.json")
    entries = {e["assetId"]:e for e in current["equipment"] if e.get("route") == "own_sprite"}
    for row in plan["changes"]:
        stem = row.get("project",row["id"])
        assert stem in native and len(native[stem]["captures"]) == 4
        saved = frame(read(CANDIDATES / "exports" / (stem+".json"))["display"][row["context"]])
        for field in DEFAULTS:
            assert max(abs((a-b+180)%360-180) if field == "rotation" else abs(a-b)
                for a,b in zip(saved[field],row["after"][field])) < .0001, (stem,row["context"],field)
        source = entries[row["id"]]["displayBlocking" if row.get("target") == "blocking" else "display"][row["context"]]
        for field in DEFAULTS:
            assert max(abs(a-b) for a,b in zip(source[field],row["before"][field])) < .0001, (row["id"],row["context"])

    backup = WORK / "grip-audit/adoption-backup"
    backup.mkdir(exist_ok=True)
    for path in (OVERRIDES, ROOT/"src/main/resources/assets/eldencombat/routes/2d.json"):
        target=backup/path.name
        if not target.exists():shutil.copy2(path,target)
    overrides = read(OVERRIDES)
    review = read(WORK / "native-review.json")
    captured = {r["id"]:r for r in review["items"]}
    for name in sorted({r["id"] for r in plan["changes"] if r.get("target") != "blocking"}):
        for folder,suffix in (("projects",".bbmodel"),("exports",".json")):
            source=CANDIDATES/folder/(name+suffix);target=WORK/folder/(name+suffix)
            old=backup/(folder+"_"+name+suffix)
            if not old.exists():shutil.copy2(target,old)
            shutil.copy2(source,target)
        captured.setdefault(name,dict(id=name,source=read(WORK/"projects"/(name+".bbmodel"))["unhandled_root_fields"]["hold_source"]))
        captured[name]["captures"]=native[name]["captures"]
        captured[name]["display"]=read(WORK/"exports"/(name+".json"))["display"]
        captured[name]["undoEntries"]=native[name]["undoEntries"]
    review["items"]=list(captured.values());write(WORK/"native-review.json",review)

    for row in plan["changes"]:
        if row.get("target") != "blocking":continue
        stem=row["project"];item=overrides["overrides"][row["id"]]
        item.setdefault("blocking",{})[row["context"]]=frame(read(CANDIDATES/"exports"/(stem+".json"))["display"][row["context"]])
        project=CANDIDATES/"projects"/(stem+".bbmodel")
        item["nativeBlockingCalibration"]=dict(project=project.relative_to(ROOT).as_posix(),
            sha256=hashlib.sha256(project.read_bytes()).hexdigest(),source="Blockbench native guard review; game acceptance pending")
    for name in ("claw","fist"):
        project=WORK/"fallbacks"/(name+".bbmodel");export=read(WORK/"exports"/(name+".json"))
        item=overrides["overrides"][name]
        item["contexts"].update({ctx:frame(export["display"][ctx]) for ctx in
            ("firstperson_righthand","firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand")})
        item["nativeCalibration"]=dict(project=project.relative_to(ROOT).as_posix(),
            sha256=hashlib.sha256(project.read_bytes()).hexdigest(),source="Native incomplete-resource fallback; primary is arm-mounted worn mesh")
    write(OVERRIDES,overrides)

    dynamic=[]
    for path in (WORK/"projects").glob("*__*.bbmodel"):
        model=read(path);base=model["unhandled_root_fields"]["hold_source"]["id"]
        model["display"]=read(WORK/"exports"/(base+".json"))["display"]
        write(path,model);dynamic.append(path.stem)
    write(WORK/"grip-audit/source-adoption.json",dict(changedContexts=len(plan["changes"]),
        sourceAdopted=True,installed=False,runtimeAccepted=False,humanAccepted=False,dynamicProjectsToRecapture=dynamic))
    print("Native corrections staged:",len(plan["changes"]),"contexts; recapture all primary projects and",len(dynamic),"dynamic projects before synchronization")


if __name__ == "__main__":
    main()
