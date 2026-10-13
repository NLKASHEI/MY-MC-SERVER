from pathlib import Path
import yaml
base=Path(__file__).resolve().parents[1]/'config/survival/plugins/EconomyShopGUI'
shop=yaml.safe_load((base/'shops/nlk_technical.yml').read_text(encoding='utf-8'))
items={v['material']:v for v in shop['pages']['page1']['items'].values()}
assert len(items)==20
for v in items.values():
 assert v['buy']>0 and v['sell']==-1
# Conservative recipe material costs at the current system shop buy prices.
minimum={'PISTON':34,'OBSERVER':28,'HOPPER':96,'REPEATER':18,'COMPARATOR':32,'DROPPER':18,'CRAFTER':140,'REDSTONE_TORCH':6,'LEVER':4,'CHEST':16,'RAIL':6.125,'POWERED_RAIL':25,'DETECTOR_RAIL':18,'ACTIVATOR_RAIL':18,'MINECART':80,'HOPPER_MINECART':250}
for material,cost in minimum.items(): assert items[material]['buy']>cost,(material,cost)
assert items['STICKY_PISTON']['buy']>items['PISTON']['buy']
assert items['DISPENSER']['buy']>items['DROPPER']['buy']
section=yaml.safe_load((base/'sections/nlk_technical.yml').read_text(encoding='utf-8'))
assert section['enable'] is True and section['display-item'] is False
print('TECHNICAL_SHOP_POLICY_PASS: 20 buy-only items, recipe price floors, valid trade category')

assert items["VILLAGER_SPAWN_EGG"]["buy"]==5000
assert items["PILLAGER_SPAWN_EGG"]["buy"]==10000
