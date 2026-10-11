"""Focused offline update test: retained data, conflicts, rollback, checksum rejection."""
import hashlib
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--base', required=True, help='Extracted prior full deployment directory')
parser.add_argument('--package', default=str(ROOT / 'release/MY-MC-SERVER-update-v0.5.0.zip'))
args = parser.parse_args()
BASE = Path(args.base)
work = Path(tempfile.mkdtemp(prefix='updater-test-', dir=ROOT / 'downloads'))
server = work / 'installation'
payload = work / 'payload'
server.mkdir()
with zipfile.ZipFile(args.package) as z:
    z.extractall(payload)
index = json.loads((payload / 'update-index.json').read_text(encoding='utf-8-sig'))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def text_digest(path):
    return hashlib.sha256(path.read_text(encoding='utf-8-sig').replace('\r\n', '\n').encode()).hexdigest()

def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')

for folder in ('scripts', 'docs', 'skills', 'config', 'manifests'):
    shutil.copytree(BASE / folder, server / folder)
for name in ('README.md', 'AGENTS.md', 'GLOSSARY.md', 'CHANGELOG.md', 'server-settings.json'):
    shutil.copy2(BASE / name, server / name)
shutil.copytree(server / 'config/survival', server / 'servers/survival')
shutil.copy2(ROOT / 'scripts/update-release.ps1', server / 'scripts/update-release.ps1')
jar = b'offline component fixture'
jar_hash = hashlib.sha256(jar).hexdigest()
for name in ('plugins', 'extensions'):
    lock_path = payload / f'manifests/{name}.lock.json'
    lock = json.loads(lock_path.read_text(encoding='utf-8-sig'))
    for item in lock:
        item['sha256'] = jar_hash
        path = item.get('path', 'plugins/' + item['name'] + '.jar')
        target = server / 'servers/survival' / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(jar)
    write(lock_path, json.dumps(lock))
    shutil.copy2(lock_path, server / f'manifests/{name}.lock.json')
paper_path = payload / 'manifests/paper.lock.json'
paper = json.loads(paper_path.read_text(encoding='utf-8-sig'))
paper['downloads']['server:default']['checksums']['sha256'] = jar_hash
write(paper_path, json.dumps(paper))
shutil.copy2(paper_path, server / 'manifests/paper.lock.json')
(server / 'servers/survival/paper.jar').write_bytes(jar)
sentinels = ('world/level.dat', 'world/playerdata/player.dat', 'plugins/AuthMe/authme.db', 'plugins/floodgate/key.pem', 'plugins/floodgate/linked-players.db', 'plugins/GriefPreventionData/ClaimData/1', 'plugins/Essentials/userdata/player.yml', 'plugins/Multiverse-Inventories/worlds/flat/player.json')
for path in sentinels:
    write(server / 'servers/survival' / path, 'KEEP ' + path)
before = {p: digest(server / 'servers/survival' / p) for p in sentinels}
write(server / 'server-settings.json', (server / 'server-settings.json').read_text(encoding='utf-8-sig').replace('6G', '4G'))
settings_before = digest(server / 'server-settings.json')
home = 'plugins/DeluxeMenus/gui_menus/homes.yml'
modified = 'plugins/DeluxeMenus/gui_menus/survival.yml'
write(server / 'servers/survival' / modified, (server / 'servers/survival' / modified).read_text(encoding='utf-8-sig') + '\n# cloud custom menu\n')
modified_before = digest(server / 'servers/survival' / modified)
write(server / 'servers/survival/server.properties', (server / 'servers/survival/server.properties').read_text(encoding='utf-8-sig') + '\n# cloud port settings\n')
properties_before = digest(server / 'servers/survival/server.properties')
for path in (home, modified):
    target = payload / 'config/survival' / path
    write(target, target.read_text(encoding='utf-8-sig') + '\n# new official menu\n')
for file in index['files']:
    target = payload / file['path']
    old = server / file['path']
    file['sha256'] = digest(target)
    file['contentSha256'] = text_digest(target)
    file['baseSha256'] = text_digest(old) if old.exists() else None

def make_package(name):
    write(payload / 'update-index.json', json.dumps(index))
    path = work / name
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        for file in payload.rglob('*'):
            if file.is_file():
                z.write(file, file.relative_to(payload).as_posix())
    return path

env = dict(os.environ)
env.pop('PSModulePath', None)
def run(package, checksum=None):
    return subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(server / 'scripts/update-release.ps1'), '-Package', str(package), '-ExpectedSha256', checksum or digest(package)], capture_output=True, env=env)

package = make_package('normal.zip')
result = run(package)
if result.returncode:
    raise RuntimeError(result.stdout.decode(errors='replace') + result.stderr.decode(errors='replace'))
assert digest(server / 'servers/survival' / home) == digest(payload / 'config/survival' / home)
assert digest(server / 'servers/survival' / modified) == modified_before
assert digest(server / 'servers/survival/server.properties') == properties_before
assert digest(server / 'server-settings.json') == settings_before
assert all(digest(server / 'servers/survival' / p) == h for p, h in before.items())
state = json.loads((server / '.update-state.json').read_text(encoding='utf-8-sig'))
assert (Path(state['pendingDirectory']) / ('servers/survival/' + modified + '.new')).exists()
assert (Path(state['backup']) / 'survival/world/level.dat').exists()
print('PASS: PS5.1 update, unchanged menu replaced, modified menu/ports/data retained, full backup')

before_state = digest(server / '.update-state.json')
assert run(package, '0' * 64).returncode != 0
assert digest(server / '.update-state.json') == before_state
print('PASS: checksum rejection before applying files')

index['version'] = '0.5.1'
readme_before = digest(server / 'README.md')
write(payload / 'README.md', (payload / 'README.md').read_text(encoding='utf-8-sig') + '\nrollback test\n')
for file in index['files']:
    if file['path'] == 'README.md':
        file['sha256'] = digest(payload / 'README.md')
        file['contentSha256'] = text_digest(payload / 'README.md')
for path in ('scripts/created-for-test.ps1', 'scripts/collision/new.ps1'):
    write(payload / path, '# fixture\n')
    index['files'].append({'path': path, 'sha256': digest(payload / path), 'contentSha256': text_digest(payload / path), 'baseSha256': None})
write(server / 'scripts/collision', 'existing file blocks directory creation')
result = run(make_package('rollback.zip'))
assert result.returncode != 0
assert digest(server / 'README.md') == readme_before
assert not (server / 'scripts/created-for-test.ps1').exists()
assert digest(server / '.update-state.json') == before_state
assert all(digest(server / 'servers/survival' / p) == h for p, h in before.items())
print('PASS: forced copy failure rolls back replaced/new files and keeps version/data')
print('Fixture retained:', work)
