"""Check the relationships that protect creative inventory isolation, without running Minecraft."""
import hashlib, json, pathlib, zipfile
import yaml

root = pathlib.Path(__file__).resolve().parent.parent
config = root / 'config/survival'
def read(rel):
    return yaml.safe_load((config / rel).read_text(encoding='utf-8-sig'))

floodgate = read('plugins/floodgate/config.yml')['player-link']
assert floodgate['enabled'] and floodgate['enable-own-linking']
assert floodgate['type'] == 'sqlite' and not floodgate['enable-global-linking']
geyser = read('plugins/Geyser-Spigot/config.yml')
assert geyser['bedrock']['port'] == 8585
assert geyser['advanced']['bedrock']['broadcast-port'] == 20013
assert geyser['java']['auth-type'] == 'floodgate'
aura = read('plugins/AuraSkills/config.yml')
assert set(aura['disabled_worlds']) == {'lobby', 'flat', 'architect'}
assert aura['disable_in_creative_mode'] and aura['jobs']['enabled']
assert not aura['jobs']['selection']['require_selection']
fake = read('plugins/FakePlayer-CE/config.yml')
assert fake['player-limit'] == 5 and fake['server-limit'] == 20
assert not fake['http-admin']['enabled'] and not fake['restore-on-start']
assert fake['follow-quiting-force'] and fake['after-spawn-commands']
assert 'survivalbotrestore %p' in fake['after-spawn-commands']
dummy = read('permissions.yml')['survival.dummy']
assert not dummy['default'] and not dummy['children']['survival.reward']
assert not dummy['children']['auraskills.skill.mining']
files = list(config.rglob('*.yml'))
for file in files:
    yaml.safe_load(file.read_text(encoding='utf-8-sig'))
worlds = read('plugins/Multiverse-Core/worlds.yml')
legacy = {w['read-only']['legacy-world-name'] for w in worlds.values()}
groups = read('plugins/Multiverse-Inventories/groups.yml')['groups']
assert set(groups['survival']['worlds']) == {'lobby', 'world', 'world_nether', 'world_the_end', 'mining', 'mining_nether', 'mining_the_end'}
assert set(groups['creative']['worlds']) == {'flat'}
assert set(groups['architecture']['worlds']) == {'architect'}
seen = set()
for group in groups.values():
    members = set(group['worlds'])
    assert members <= legacy | {'mining', 'mining_nether', 'mining_the_end', 'architect'}, 'Inventory groups must use legacy Bukkit names; managed worlds are created at startup'
    assert not (seen & members), 'Conflicting inventory groups'
    assert group['shares'] == ['all']
    seen |= members
assert seen == legacy | {'mining', 'mining_nether', 'mining_the_end', 'architect'}
assert worlds['minecraft:flat']['gamemode'] == 'creative'
flat_settings = json.loads(worlds['minecraft:flat']['read-only']['generator-settings'])
assert sum(layer['height'] for layer in flat_settings['layers']) == 128
assert flat_settings['structure_overrides'] == [] and not flat_settings['features']
assert worlds['minecraft:flat']['spawn-location']['y'] == 64
assert worlds['minecraft:lobby']['gamemode'] == 'adventure'
assert worlds['minecraft:overworld']['gamemode'] == 'survival'
core = read('plugins/Multiverse-Core/config.yml')
assert core['world']['enforce-gamemode']
assert core['teleport']['passenger-mode'] == 'dismount_all'
shares = read('plugins/Multiverse-Inventories/config.yml')['share-handling']
assert shares['enable-gamemode-share-handling']
assert not shares['enable-bypass-permissions']
assert not shares['default-ungrouped-worlds']
assert not shares['active-optional-shares'], 'Currency is global; never enable economy sharing'
perms = read('permissions.yml')['survival.player']['children']
for dest in ['minecraft:overworld', 'minecraft:lobby', 'minecraft:flat']:
    assert perms['multiverse.teleport.self.w.' + dest] is True
for node, enabled in perms.items():
    if enabled:
        assert 'bypass' not in node.lower() and node != '*', 'Player bypass permission granted'
menus = read('plugins/DeluxeMenus/config.yml')['gui_menus']
for entry in menus.values():
    menu = read('plugins/DeluxeMenus/gui_menus/' + entry['file'])
    occupied = set()
    for item in menu['items'].values():
        assert 0 <= item['slot'] < menu['size']
        assert item['slot'] not in occupied, 'Overlapping inventory menu slots'
        occupied.add(item['slot'])
        assert item.get('left_click_commands', []) == item.get('right_click_commands', []), 'Mobile/right click behavior missing'
        for action in item.get('left_click_commands', []):
            if action.startswith('[openguimenu] '):
                assert action.split(' ', 1)[1] in menus, 'Menu links must reach a registered panel'
guide = read('plugins/DeluxeMenus/gui_menus/survival.yml')
assert '[openguimenu] worlds' in guide['items']['flat']['left_click_commands']
assert 'lobby' not in guide['items'] and 'survival' not in guide['items'], 'World destinations belong inside exploration'
assert guide['items']['community']['slot'] == 11 and guide['items']['report']['slot'] == 15
assert guide['items']['titles']['slot'] == 4 and '[player] titles' in guide['items']['titles']['left_click_commands']
title_catalog = read('plugins/MiaoServer/titles.yml')
assert title_catalog['schema'] == 1 and title_catalog['custom-price'] == 100000 and len(title_catalog['titles']) >= 70
for title in title_catalog['titles'].values():
    assert title['kind'] in {'milestone', 'shop', 'admin'}
    if title['kind'] == 'shop':
        assert title['price'] > 0
notes = read('plugins/MiaoServer/release-notes.yml')
release_version = json.loads((root / 'release-version.json').read_text(encoding='utf-8-sig'))['version']
assert notes['version'] == release_version == yaml.safe_load((root / 'tools/miao-server/plugin.yml').read_text(encoding='utf-8'))['version']
community = read('plugins/MiaoServer/community.yml')
assert community['schema'] == 1 and len(community['quests']) >= 6 and len(community['system-orders']) >= 6
assert community['relics']['armored_wings']['price'] == 1000000000
assert community['relics']['light_blocks']['price'] == 500000 and community['relics']['light_blocks']['quantity'] == 64
for relic in community['relics'].values():
    tier = relic['material'].split('_')[0]
    if tier in ('IRON', 'DIAMOND', 'NETHERITE'):
        assert relic['price'] == {'IRON': 1000000, 'DIAMOND': 10000000, 'NETHERITE': 100000000}[tier]
assert read('plugins/MiaoServer/config.yml')['resource-world']['entry-fee'] == 200
assert read('plugins/MiaoServer/config.yml')['bedrock']['send-view-distance'] == 8
resource = read('plugins/DeluxeMenus/gui_menus/resource.yml')
assert '[player] miao mining nether' in resource['items']['nether']['left_click_commands']
assert '[player] miao mining end' in resource['items']['end']['left_click_commands']
assert notes['lines'] and notes['summary'], 'Player update notice must be maintained with every release'
assert [guide['items'][key]['slot'] for key in ('guide', 'online_map', 'account', 'close')] == [37, 39, 41, 43]
assert read('plugins/DeluxeMenus/gui_menus/worlds.yml')['items']['back']['slot'] == 22
lobby = read('plugins/WorldGuard/worlds/lobby/regions.yml')['regions']['__global__']['flags']
assert lobby['block-break'] == lobby['block-place'] == 'deny'
blocked = read('plugins/WorldGuard/worlds/flat/regions.yml')['regions']['__global__']['flags']['blocked-cmds']
assert {'shop', 'sell', 'pay', 'reward'} <= set(blocked)
lock = json.loads((root / 'manifests/world-template.lock.json').read_text(encoding='utf-8-sig'))
asset = root / lock['file']
assert hashlib.sha256(asset.read_bytes()).hexdigest() == lock['sha256']
with zipfile.ZipFile(asset) as archive:
    names = archive.namelist()
    assert 'world/level.dat' in names
    assert any(n.startswith('world/dimensions/minecraft/lobby/region/') for n in names)
    for name in names:
        assert name.startswith('world/') and '..' not in name.split('/')
        assert not any(part in {'players','playerdata','stats','advancements','entities','poi'} for part in name.split('/'))
        if '/region/' in name:
            assert name.startswith('world/dimensions/minecraft/lobby/region/')
print(f'{len(files)} YAML templates parsed; inventory groups, menus, world protection and clean map asset validated')
