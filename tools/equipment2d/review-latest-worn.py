"""Capture arm-mounted working copies through Blockbench's native viewport MCP."""
import base64
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "art/equipment-2d/blockbench-holds-20261021"
# Keep the HTTP client local to this command: no external script import side effects.
import urllib.request
headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
sequence = 0


def call(method, params):
    global sequence
    sequence += 1
    request = urllib.request.Request("http://127.0.0.1:3000/bb-mcp",
        json.dumps(dict(jsonrpc="2.0", id=sequence, method=method, params=params)).encode(), headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        session = response.headers.get("Mcp-Session-Id")
        data = response.read().decode()
    if session:
        headers["Mcp-Session-Id"] = session
    if data.startswith(("event:", "data:")):
        data = next(line[6:] for line in data.splitlines() if line.startswith("data: "))
    value = json.loads(data)
    assert "error" not in value, value.get("error")
    return value.get("result", {})


def tool(name, arguments, image_path=None):
    value = call("tools/call", dict(name=name, arguments=arguments))
    assert not value.get("isError"), value
    for content in value.get("content", []):
        if content["type"] == "text":
            assert not content["text"].startswith("Error executing"), content["text"]
        if content["type"] == "image" and image_path:
            image_path.write_bytes(base64.b64decode(content["data"]))
    if image_path:
        image = Image.open(image_path).convert("RGB")
        assert len(image.getcolors(image.width * image.height)) > 32, "Empty native viewport"
    return value


def main():
    call("initialize", dict(protocolVersion="2024-11-05", capabilities={},
        clientInfo=dict(name="codex-native-worn-review", version="1")))
    items = []
    for name in ("claw", "fist"):
        path = str(WORK / "projects" / (name + ".bbmodel")).replace("\\", "/")
        tool("risky_eval", dict(code="""(async()=>{
            await new Promise(resolve=>Blockbench.read([PATH],{readtype:'text'},files=>{
                Codecs.project.load(JSON.parse(files[0].content),{path:files[0].path,no_file:true});resolve();}));
            Modes.options.edit.select();Canvas.updateAll();return Format.id;
        })()""".replace("PATH", json.dumps(path))))
        captures = []
        for raised in (False, True):
            tool("risky_eval", dict(code="""(()=>{
                for(const name of ['arm_right','arm_right_l','ref_arm_right','ref_arm_left'])
                    Group.all.find(g=>g.name===name).rotation[0]=ANGLE;
                Canvas.updateAll();return true;
            })()""".replace("ANGLE", "80" if raised else "0")))
            capture = WORK / "qa" / (name + ("_worn_raised.png" if raised else "_worn_rest.png"))
            tool("set_camera_angle", dict(view="active", position=[36,25,-56], target=[0,17,0],
                projection="perspective", fov=45, max_size=1200, format="png"), capture)
            captures.append(str(capture.relative_to(ROOT)).replace("\\", "/"))
        tool("risky_eval", dict(code="(()=>{for(const g of Group.all)g.rotation[0]=0;Canvas.updateAll();Project.saved=true;return true;})()"))
        items.append(dict(id=name, captures=captures, sourceProjectChanged=False))
    receipt = dict(items=items, tool="Blockbench native viewport MCP", firstPerson="runtime review pending",
        gameStarted=False, runtimeAccepted=False, humanAccepted=False)
    (WORK / "native-worn-review.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print("NATIVE_WORN_REVIEW: 2 items / 4 actual viewport captures; first-person game review pending")


if __name__ == "__main__":
    main()
