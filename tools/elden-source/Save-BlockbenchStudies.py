"""Keep validated local motion studies outside disposable build output."""
import hashlib
import json
from pathlib import Path
import shutil

WORKSPACE = Path(__file__).resolve().parents[2]
CACHE = WORKSPACE / "build/elden-source"
STUDY = CACHE / "blockbench-skills-20261021-arm70"
DEST = WORKSPACE / "local-research/elden-source-motion-20261021-arm70"
LABELS = {"takers_flames": "掠夺火焰", "wave_of_gold": "黄金波动", "cursed_blood_slice": "咒血斩",
          "waterfowl_dance": "水鸟乱舞", "gold_breaker": "黄金碎击", "starcaller_cry": "唤星",
          "bloodboon_ritual": "授血仪式", "destined_death": "命定之死"}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def main():
    conversion = json.loads((STUDY / "conversion-report.json").read_text(encoding="utf-8"))
    live = json.loads((STUDY / "blockbench-live.json").read_text(encoding="utf-8"))
    assert live["passed"] and live["waistGain"] == .6 and live["speed"] == 1.3
    assert live["armAmplitudeControl"] == .7
    files = {}
    def keep(source, relative):
        target = DEST / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists() and sha(target) != sha(source):
            raise ValueError(f"Existing study differs; preserve the user's edits and choose a new destination: {target}")
        shutil.copyfile(source, target)
        if sha(source) != sha(target):
            raise ValueError("Study copy hash mismatch")
        files[str(relative).replace('\\', '/')] = {"sha256": sha(target), "bytes": target.stat().st_size}
    for project in conversion["projects"]:
        keep(STUDY / "native-roundtrip" / (project["skill"] + ".bbmodel"), Path("projects") / (project["skill"] + ".bbmodel"))
    for path in STUDY.glob("*.motion.json"):
        keep(path, Path("motions") / path.name)
    for path in (CACHE / "decoded-skills").glob("*.npz"):
        keep(path, Path("reference/decoded") / path.name)
    for filename in ["skeleton.json", "decode-report.json"]:
        keep(CACHE / "decoded-skills" / filename, Path("reference/decoded") / filename)
    decoded = json.loads((CACHE / "decoded-skills/decode-report.json").read_text(encoding="utf-8"))
    inputs = {Path(decoded["Skeleton"])}
    for clip in decoded["Clips"]:
        inputs.update([Path(clip["Source"]), Path(clip["Compendium"])])
    for path in sorted(inputs):
        keep(path, Path("reference/hkx") / path.name)
    for filename in ["conversion-report.json", "validation.json", "blockbench-live.json"]:
        keep(STUDY / filename, Path("evidence") / filename)
    keep(CACHE / "restoration-receipt.json", Path("evidence/restoration-receipt.json"))
    for filename in ["wave_of_gold.png", "wave_of_gold-1.3x-arm70.gif"]:
        keep(STUDY / "previews" / filename, Path("previews") / filename)
    table = ["| 战技 | 片段 | 原始秒数 → 1.3倍速秒数 |", "|---|---:|---|"]
    for project in conversion["projects"]:
        skill = project["skill"]
        clips = [r for r in conversion["clips"] if r["skill"] == skill]
        duration = "；".join(f"{r['sourceSeconds']:.3f} → {r['seconds']:.3f}" for r in clips)
        table.append(f"| [{LABELS[skill]}](projects/{skill}.bbmodel) | {len(clips)} | {duration} |")
    readme = "\n".join([
        "# 法环战技 Blockbench 本地动作研究 · 手臂70%修订 · 2026-10-21", "",
        "8 个可独立打开的 .bbmodel，15 段动作，来源共 2,744 帧。已在 Blockbench 5.2.2 原生加载、采样、编译并重开验证。", "",
        "## 当前调整", "",
        "- 播放速度 1.3 倍：所有关键帧时间与时长统一除以 1.3，不删帧、不把原动作改成原时长的 1.3 倍。",
        "- 腰部输入增益 60%，平滑收紧；前后弯和侧倾各不超过 15°，局部扭腰不超过 20°。较大角度经过平滑限幅后会收得更多。",
        "- 整体朝向从骨盆 Y 轴扭转提取，放在人物父组；完整转身保留，不拿腰部硬拧来代替整体转身。",
        "- 左右手臂幅度控制从 0.85 改为 0.70，以未收幅的原始转换姿态为基准。沿用归一化缩放四元数向量部分的连续算法；70% 是控制增益，不是把每个大角度都机械乘 0.7。",
        "- 头部和腿部维持原幅度控制 0.85；腰部、人物整体朝向、全部时间点和原始骨架对照保持上一版设置。",
        "- 使用原生逐骨骼四元数插值。保持源采样时序与独立连段；不会把所有片段头尾直接拼成可结算连击。", "",
        "## 打开", "",
        "在 Blockbench 中打开 projects/ 下任一工程，进入动画模式，选择所需片段并点击时间轴播放。通用模型格式，不依赖 Elden Combat 动作编辑器插件。",
        "像素人物是收幅后的 Minecraft 候选；细线骨架是未收幅的原始关节点对照。原始骨架保留 150 骨骼数据，视图只显示 22 个主要关节点及 21 条连接，避免手指等辅助骨骼遮挡。",
        "参考皮肤与几何已内嵌，工程打开不依赖游戏安装目录。没有加载或修改武器模型及握姿。", "",
        *table, "", "## 数据与验收边界", "",
        "- projects/ 为 Blockbench 原生编译后的编辑工程；motions/ 包含转换前后四元数、加速后的时间与单独保存的 extractedMotion 根运动样本。",
        "- reference/ 保存本轮实际使用的原始 HKX/compendium/骨架及完整解码 NPZ。evidence/ 保存来源哈希、转换设置、离线检查与原生检查。旧元数据中的 build 路径是当时解码位置，保留输入可按文件名与哈希在 reference/ 对照。",
        "- 八工程、十五片段共核对 1,260 个六关节姿态，以及 75 个帧间四元数插值点；另核对父组朝向及原始关节点坐标。黄金波动已重开原生编译文件验证插值。",
        "- 六部件玩家没有独立肘、膝、腕。当前候选没有四肢平移、实体位移、武器、命中窗口、音效或 VFX；不能代表原版动作全保真或战技实战完成。",
        "- 当前游戏加载器不支持这些工程使用的父组朝向轨道和四元数插值；尚未替换游戏动作 JSON，也未更改伤害、蓝量、冷却或服务器位移。接入时须另行确认阶段与事件。",
        "- 保存于单独忽略的本地研究目录，不随 build clean 删除，不纳入游戏 JAR、整合包或 Git 资产；不要将本地研究结果当成已完成发布准入的素材。", "",
        "## 复现工具", "",
        "项目 tools/elden-source/ 下的 Restore-SkillSources.py、Decode-SkillAnimations.py、Convert-SkillsToBlockbench.py、Validate-BlockbenchSkills.py、Validate-BlockbenchSkills.js 与 Save-BlockbenchStudies.py。",
        "复用 [Soulstruct Havok](https://github.com/Grimrukh/soulstruct-havok)、[Nuxe](https://github.com/JKAnderson/Nuxe) 的现成解码/文件字典及项目已有只读档案读取工具。", "",
        "保留研究副本时逐文件校验哈希；已存在且内容不同的编辑工程会拒绝覆盖，避免覆盖你后续修改。", ""
    ])
    readme_path = DEST / "README.md"
    if readme_path.exists() and readme_path.read_text(encoding="utf-8") != readme:
        raise ValueError("Existing README changed; refusing overwrite")
    readme_path.write_text(readme, encoding="utf-8")
    files["README.md"] = {"sha256": sha(readme_path), "bytes": readme_path.stat().st_size}
    manifest = {"localResearchOnly": True, "speed": 1.3, "waistGain": .6, "armAmplitudeControl": .7,
                "waistEulerLimitsDegrees": [15, 20, 15], "files": files}
    (DEST / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Saved {len(files)} verified local files to {DEST}")


if __name__ == "__main__":
    main()
