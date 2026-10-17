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
parser.add_argument('--rescue', action='store_true', help='Verify repair of an old installed updater after success')
parser.add_argument('--latest', action='store_true', help='Exercise latest release discovery through an offline HTTP fixture')
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
engine = server / ('scripts/update-release-fixed.ps1' if args.rescue else 'scripts/update-release.ps1')
shutil.copy2(ROOT / 'scripts/update-release.ps1', engine)
write(server / 'scripts/backup.ps1', "throw 'Automatic update backup must never run'\n")
if not args.rescue:
    shutil.copy2(ROOT / 'scripts/update-release.ps1', payload / 'scripts/update-release.ps1')
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
    write(payload / 'update-index.json', json.dumps(index, ensure_ascii=False))
    path = work / name
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        for file in payload.rglob('*'):
            if file.is_file():
                z.write(file, file.relative_to(payload).as_posix())
    return path

env = dict(os.environ)
env.pop('PSModulePath', None)
def run(package, checksum=None):
    return subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(engine), '-Package', str(package), '-ExpectedSha256', checksum or digest(package)], capture_output=True, env=env)

package = make_package('normal.zip')
def run_latest(package):
    release_file = work / 'latest.json'
    checksum_file = work / 'latest.sha256.txt'
    write(checksum_file, digest(package) + '  fixture.zip\n')
    asset = f'MY-MC-SERVER-update-v{index["version"]}.zip'
    write(release_file, json.dumps({'tag_name': 'v' + index['version'], 'html_url': 'https://github.com/NLKASHEI/MY-MC-SERVER/releases/latest', 'assets': [
        {'name': asset, 'browser_download_url': 'https://github.com/fixture/' + asset, 'digest': 'sha256:' + digest(package)},
        {'name': asset + '.sha256.txt', 'browser_download_url': 'https://github.com/fixture/' + asset + '.sha256.txt'}]}))
    bootstrap = work / 'latest-fixture.ps1'
    write(bootstrap, '''param($FixtureEngine,$FixtureArchive,$FixtureChecksum,$FixtureRelease)
function Invoke-RestMethod {
 param($Uri,$Headers,$TimeoutSec)
 if($Uri -ne 'https://gh-proxy.org/https://api.github.com/repos/NLKASHEI/MY-MC-SERVER/releases/latest'){throw "Unexpected release route: $Uri"}
 Get-Content -LiteralPath $FixtureRelease -Raw -Encoding UTF8 | ConvertFrom-Json
}
function Invoke-WebRequest {
 param($Uri,$Headers,$TimeoutSec,$OutFile,$UseBasicParsing)
 if($Uri -notlike 'https://gh-proxy.org/https://github.com/fixture/*'){throw "Unexpected asset route: $Uri"}
 if($Uri.EndsWith('.sha256.txt')){Copy-Item -LiteralPath $FixtureChecksum -Destination $OutFile}else{Copy-Item -LiteralPath $FixtureArchive -Destination $OutFile}
}
& $FixtureEngine -GitHubProxy https://gh-proxy.org/
''')
    return subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bootstrap), str(engine), str(package), str(checksum_file), str(release_file)], capture_output=True, env=env)

result = run_latest(package) if args.latest else run(package)
if result.returncode:
    raise RuntimeError(result.stdout.decode(errors='replace') + result.stderr.decode(errors='replace'))
assert digest(server / 'servers/survival' / home) == digest(payload / 'config/survival' / home)
assert digest(server / 'servers/survival' / modified) == modified_before
assert digest(server / 'servers/survival/server.properties') == properties_before
assert digest(server / 'server-settings.json') == settings_before
assert all(digest(server / 'servers/survival' / p) == h for p, h in before.items())
state = json.loads((server / '.update-state.json').read_text(encoding='utf-8-sig'))
assert (Path(state['pendingDirectory']) / ('servers/survival/' + modified + '.new')).exists()
assert state['backup'] is None
assert not (server / 'backups').exists()
assert Path(state['updateDirectory']).is_dir()
assert not any(p.is_file() for p in (server / 'downloads').glob('release-update-*/rollback/**/*'))
assert (server / '一键更新.bat').read_bytes() == (payload / '一键更新.bat').read_bytes()
if args.rescue:
    assert digest(server / 'scripts/update-release.ps1') == digest(ROOT / 'scripts/update-release.ps1')
    assert (server / 'scripts/update-release.ps1.before-fast-update').exists()
    print('PASS: rescue entry repairs the normal updater after applying old release payload')
print('PASS: UTF-8 manifest with literal Chinese BAT names under PS5.1')
print('PASS: PS5.1 update without full backup; world/data/config preserved; rollback copies removed after success')
if args.latest:
    permanent = (server / 'update-fast.bat').read_text(encoding='utf-8-sig')
    assert '-ReleaseTag' not in permanent and '-PinnedPackageSha256' not in permanent
    assert '-GitHubProxy "https://gh-proxy.org/"' in permanent
    first_state = digest(server / '.update-state.json')
    second = run_latest(package)
    assert second.returncode == 0
    assert digest(server / '.update-state.json') == first_state
    assert '已是最新版本'.encode('utf-8') in second.stdout or '已是最新版本'.encode('gbk') in second.stdout
    print('PASS: permanent accelerated entry discovers latest, survives update, and skips already installed release')

before_state = digest(server / '.update-state.json')
assert run(package, '0' * 64).returncode != 0
assert digest(server / '.update-state.json') == before_state
print('PASS: checksum rejection before applying files')

parts = index['version'].split('.')
index['version'] = '.'.join(parts[:2] + [str(int(parts[2]) + 1)])
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
assert '更新失败，已回退'.encode('utf-8') in result.stderr or '更新失败，已回退'.encode('gbk') in result.stderr
assert digest(server / 'README.md') == readme_before
assert not (server / 'scripts/created-for-test.ps1').exists()
assert digest(server / '.update-state.json') == before_state
assert all(digest(server / 'servers/survival' / p) == h for p, h in before.items())
print('PASS: forced copy failure rolls back replaced/new files and keeps version/data')

# The separate candidate updater must also leave scheduled backups in charge.
candidate_stage = work / 'candidates'
candidate_lock = json.loads((server / 'manifests/plugins.lock.json').read_text(encoding='utf-8-sig'))
candidate = dict(candidate_lock[0])
candidate['file'] = candidate['name'] + '.jar'
candidate['version'] = 'fixture-updated'
write(candidate_stage / candidate['file'], 'new candidate binary fixture')
candidate['sha256'] = digest(candidate_stage / candidate['file'])
write(candidate_stage / 'candidates.json', json.dumps([candidate]))
write(server / 'scripts/backup.ps1', "throw 'Automatic update backup must never run'\n")
shutil.copy2(ROOT / 'scripts/apply-update.ps1', server / 'scripts/apply-update.ps1')
result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(server / 'scripts/apply-update.ps1'), '-Stage', str(candidate_stage)], capture_output=True, env=env)
assert result.returncode == 0, result.stderr.decode(errors='replace')
assert digest(server / 'servers/survival/plugins' / candidate['file']) == candidate['sha256']
assert not (server / 'backups').exists()
assert all(digest(server / 'servers/survival' / p) == h for p, h in before.items())
print('PASS: candidate updater replaces selected component without invoking backups or touching data')
print('Fixture retained:', work)
