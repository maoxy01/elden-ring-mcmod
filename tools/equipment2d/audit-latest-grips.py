"""Audit grip semantics before more display fitting; only diagnostic files are written."""
import importlib.util
import base64
import io
import json
from pathlib import Path
from PIL import Image
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "art/equipment-2d/blockbench-holds-20261021"
OUT = WORK / "grip-audit"
CANDIDATES = {
    "blasphemous_blade": [10.5,53.5], "colossal_sword": [5.5,26.5],
    "curved_greatsword": [5.5,25.5], "curved_sword": [5.5,25.5],
    "great_sword": [5.5,26.5], "hand_of_malenia": [9.5,49.5],
    "heavy_thrusting_sword": [6.5,25.5], "malikeths_black_blade": [9.5,54.5],
    "morgotts_cursed_sword": [9.5,46.5], "sacred_relic_sword": [8.5,55.5],
    "starscourge_greatsword": [12.5,47.5], "twinblade": [16,16],
}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    OUT.mkdir(exist_ok=True)
    assets = read(ROOT / "art/equipment-2d/blockbench-all-items-20261018/manifest.json")["assets"]
    routes = {e["assetId"]: e for e in read(ROOT / "src/main/resources/assets/eldencombat/routes/2d.json")["equipment"] if e.get("route") == "own_sprite"}
    spec = importlib.util.spec_from_file_location("display_math", Path(__file__).with_name("build-display-candidates.py"))
    maths = importlib.util.module_from_spec(spec);spec.loader.exec_module(maths)
    contacts = []
    rows = []
    for a in assets:
        name = a["id"]
        if name in ("claw", "fist"):
            finding = "arm_mount_not_item_anchor"
        elif name in ("ballista", "test_flask"):
            finding = "declared_anchor_outside_vanilla_hand_volume"
        elif name in ("great_shield", "shield", "small_shield"):
            finding = "anchor_at_hand_volume_corner_back_grip_unverified"
        elif name == "great_bow":
            finding = "anchor_on_grip_wrap_left_firstperson_facing_reversed"
        elif name in CANDIDATES:
            finding = "anchor_inside_hand_semantic_handle_center_candidate_not_a_verified_fix"
        else:
            finding = "point_on_grip_or_shaft_contact_check_pending"
        rows.append(dict(id=name, declaredAnchorPixels=a["anchorPixels"],
            candidateAnchorPixels=CANDIDATES.get(name), finding=finding,
            adopted=False, sourceModelSha256=a["modelSha256"]))
        if name in ("claw", "fist"):
            continue
        entry = routes[name]
        for left in (False,True):
            sign = -1 if left else 1
            context = "thirdperson_"+("lefthand" if left else "righthand")
            frame = maths.raw_pose(entry["display"][context],left)
            base = maths.rotation_xyz(-90,180,0) @ maths.translation([sign/16,2/16,-10/16])
            grip = maths.anchor_block(a["anchorPixels"], a["variants"][0]["size"])
            point = (base @ frame @ np.r_[grip,1])[:3]*16
            lower = np.array([-1,6,-2] if left else [-3,6,-2])
            upper = np.array([3,10,2] if left else [1,10,2])
            outside = np.maximum(lower-point,0)+np.maximum(point-upper,0)
            contacts.append(dict(id=name,hand="left" if left else "right",armLocalUnits=point.tolist(),
                handBounds=[lower.tolist(),upper.tolist()],insideHandVolume=bool(np.max(outside)<.0001),
                outsideDistanceUnits=float(np.linalg.norm(outside))))

    spec = importlib.util.spec_from_file_location("bb_client", Path(__file__).with_name("review-latest-worn.py"))
    bb = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bb)
    bb.call("initialize", dict(protocolVersion="2024-11-05", capabilities={},
        clientInfo=dict(name="codex-grip-contact-audit", version="1")))
    captures = []
    for name in ("great_sword", "malikeths_black_blade", "heavy_thrusting_sword", "twinblade", "great_bow", "ballista", "test_flask", "shield"):
        a = next(a for a in assets if a["id"] == name)
        w, h = a["variants"][0]["size"]
        points = [(a["anchorPixels"], 0xff4444)]
        if name in CANDIDATES:
            points.append((CANDIDATES[name], 0x44ff88))
        markers = [dict(position=[16*p[0]/w,16*(1-p[1]/h),8], color=color) for p,color in points]
        # Original wide-arm hand is x[-3,1], y[6,10], z[-2,2] relative to its arm.
        # In ItemInHandLayer's item-parent coordinates its centre is (0,-2,2)/16.
        frame = maths.raw_pose(routes[name]["display"]["thirdperson_righthand"],False)
        physical_palm = (np.linalg.inv(frame) @ np.array([0,-2/16,2/16,1]))[:3]*16+8
        markers.append(dict(position=physical_palm.tolist(),color=0xff44ff))
        for marker in markers:
            im = Image.new("RGBA", (2,2), ((marker["color"]>>16)&255,(marker["color"]>>8)&255,marker["color"]&255,255))
            buffer = io.BytesIO();im.save(buffer,format="PNG")
            marker["texture"] = "data:image/png;base64,"+base64.b64encode(buffer.getvalue()).decode()
        code = """(async()=>{
            await LatestHold.load(NAME);
            const markers=MARKERS;
            for(const info of markers){
                const texture=new Texture({name:'Grip audit '+info.color,uv_width:2,uv_height:2}).fromDataURL(info.texture).add(false);
                const faces={};for(const face of ['north','south','east','west','up','down'])faces[face]={uv:[0,0,2,2],texture:texture.uuid};
                const cube=new Cube({name:'Grip audit '+info.color,from:info.position.map(v=>v-.25),to:info.position.map(v=>v+.25),faces,box_uv:false,export:true}).init();
                cube.mesh.renderOrder=9999;
                const material=cube.mesh.material;
                for(const m of Array.isArray(material)?material:[material])m.depthTest=false;
            }
            Modes.options.display.select();DisplayMode.load('thirdperson_righthand');Canvas.updateAll();
            await new Promise(r=>setTimeout(r,200));return {id:NAME,markerCubes:Cube.all.filter(c=>c.name.startsWith('Grip audit')).length};
        })()""".replace("NAME", json.dumps(name)).replace("MARKERS", json.dumps(markers))
        bb.tool("risky_eval", dict(code=code))
        path = OUT / (name + "_native_contact.png")
        bb.tool("capture_screenshot", dict(view="active", max_size=1200, format="png"), path)
        captures.append(str(path.relative_to(ROOT)).replace("\\", "/"))

    # Vanilla model and display are the comparison, not a guessed 'universal' palm point.
    for name in ("sword", "bow"):
        source = str(ROOT / "art/equipment-2d/official-holds-20261020/blockbench" / (name+".bbmodel")).replace("\\", "/")
        code = """(async()=>{
            await new Promise(resolve=>Blockbench.read([PATH],{readtype:'text'},files=>{
                Codecs.project.load(JSON.parse(files[0].content),{path:files[0].path,no_file:true});resolve();}));
            Modes.options.display.select();DisplayMode.load('thirdperson_righthand');Canvas.updateAll();
            await new Promise(r=>setTimeout(r,100));return true;
        })()""".replace("PATH", json.dumps(source))
        bb.tool("risky_eval", dict(code=code))
        path = OUT / ("vanilla_"+name+"_native_contact.png")
        bb.tool("capture_screenshot", dict(view="active", max_size=1200, format="png"), path)
        captures.append(str(path.relative_to(ROOT)).replace("\\", "/"))

    bow_facing=[]
    for left in (False,True):
        context="firstperson_"+("lefthand" if left else "righthand")
        direction=maths.raw_pose(routes["great_bow"]["display"][context],left)[:3,:3] @ np.array([1.,-1.,0])
        direction/=np.linalg.norm(direction)
        bow_facing.append(dict(context=context,fromStringTowardBowBack=direction.tolist(),
            pointsTowardPlayer=bool(direction[2]>0),scope="idle first person; draw animation still needs verification"))
    receipt = dict(items=rows, captures=captures, thirdPersonContacts=contacts, bowFacing=bow_facing,
        markerLegend=dict(red="declared model anchor", green="handle center candidate; not adopted",
            magenta="vanilla wide-arm hand-volume centre, inverse-transformed into this item"),
        policy="No pose tuning, art changes, runtime synchronization, packaging or game launch in this audit",
        contactLimit="Point containment is necessary, not sufficient. Finger/handle fit, blade direction and aim pose are separate checks.",
        sourceModelsChanged=False, runtimeAccepted=False, humanAccepted=False)
    (OUT / "grip-audit.json").write_text(json.dumps(receipt, indent=2)+"\n", encoding="utf-8")
    print(f"GRIP_AUDIT: 28 items / {len(contacts)} hand-volume checks / {len(captures)} native captures; no candidates adopted")


if __name__ == "__main__":
    main()
