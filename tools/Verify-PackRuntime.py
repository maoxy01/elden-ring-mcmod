"""Read-only launcher/runtime file audit. Never starts Minecraft."""
from pathlib import Path
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('D:/PCL/.minecraft')
SOURCE = BASE / 'versions/Elden Combat 0.2.0 - Vanilla Combat - 副本'
PROFILE = 'Elden Combat 0.2.0 - Vanilla Combat'
folder = Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE
name = sys.argv[2] if len(sys.argv) > 2 else PROFILE
version_path = folder / (name + '.json')
doc = json.loads(version_path.read_text(encoding='utf-8-sig'))
failures = []
checked = []


def check(path, expected=None):
    if not path.is_file():
        failures.append({'path': str(path), 'error': 'missing'})
        return
    raw = path.read_bytes()
    sha1 = hashlib.sha1(raw).hexdigest()
    if expected and sha1 != expected:
        failures.append({'path': str(path), 'error': 'sha1 mismatch'})
    checked.append({'path': str(path), 'bytes': len(raw), 'sha1': sha1})


def windows(rules):
    allowed = not rules
    for rule in rules:
        osrule = rule.get('os', {})
        if osrule.get('name', 'windows') != 'windows':
            continue
        if 'arch' in osrule and not re.fullmatch(osrule['arch'], 'amd64'):
            continue
        if rule.get('features'):
            continue
        allowed = rule['action'] == 'allow'
    return allowed


check(folder / (name + '.jar'), doc['downloads']['client']['sha1'])
for lib in doc['libraries']:
    if not windows(lib.get('rules', [])):
        continue
    artifact = lib.get('downloads', {}).get('artifact')
    if artifact:
        check(BASE / 'libraries' / artifact['path'], artifact.get('sha1'))
    else:
        group, artifact_name, version, *classifier = lib['name'].split(':')
        filename = f'{artifact_name}-{version}' + (f'-{classifier[0]}' if classifier else '') + '.jar'
        check(BASE / 'libraries' / group.replace('.', '/') / artifact_name / version / filename)
    native = lib.get('natives', {}).get('windows')
    if native:
        entry = lib['downloads']['classifiers'][native.replace('${arch}', '64')]
        check(BASE / 'libraries' / entry['path'], entry.get('sha1'))
index_path = BASE / 'assets/indexes' / (doc['assetIndex']['id'] + '.json')
check(index_path, doc['assetIndex']['sha1'])
if index_path.is_file():
    index = json.loads(index_path.read_text(encoding='utf-8'))
    for sha1 in sorted({obj['hash'] for obj in index['objects'].values()}):
        check(BASE / 'assets/objects' / sha1[:2] / sha1, sha1)
if 'logging' in doc:
    log = doc['logging']['client']['file']
    check(BASE / 'assets/log_configs' / log['id'], log.get('sha1'))
report = dict(instance=str(folder), profile=str(version_path), id=doc['id'],
              profileSha256=hashlib.sha256(version_path.read_bytes()).hexdigest(),
              windowsFiles=len(checked), failures=failures, filesComplete=not failures,
              checked=checked, gameStarted=False)
output = ROOT / 'dist/playable-pack-20261023' / ('INSTALLED-RUNTIME-AUDIT.json' if len(sys.argv) > 1 else 'RUNTIME-AUDIT.json')
output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: report[k] for k in ('instance', 'id', 'windowsFiles', 'filesComplete', 'failures')}, ensure_ascii=False))
sys.exit(bool(failures))
