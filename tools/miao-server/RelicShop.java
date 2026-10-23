package cn.nankexi;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Vanilla items with server-authoritative attributes, usable without a client mod. */
final class RelicShop {
    private final CommunityLife life;
    RelicShop(CommunityLife life){this.life=life;}
    private List<String> ids(){var section=life.config.getConfigurationSection("relics");return section==null?List.of():new ArrayList<>(section.getKeys(false));}
    ItemStack item(String id){
        if(!ids().contains(id))return null;String k="relics."+id;Material material=Material.matchMaterial(life.config.getString(k+".material",""));if(material==null||!(material==Material.LIGHT||material==Material.ELYTRA||Set.of("IRON","DIAMOND","NETHERITE").stream().anyMatch(tier->Set.of("PICKAXE","AXE","SHOVEL","HOE","SWORD").stream().anyMatch(tool->material.name().equals(tier+"_"+tool)))))throw new IllegalStateException("Invalid relic material: "+id);
        int quantity=life.config.getInt(k+".quantity",1);if(quantity<1||quantity>material.getMaxStackSize())throw new IllegalStateException("Invalid relic quantity");ItemStack item=new ItemStack(material,quantity);var meta=item.getItemMeta();String name=life.config.getString(k+".name",id);meta.displayName(name.startsWith("<")?net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name):net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(name));meta.setLore(life.config.getStringList(k+".lore").stream().map(line->ChatColor.translateAlternateColorCodes('&',line)).toList());if(material!=Material.LIGHT)meta.setUnbreakable(true);meta.getPersistentDataContainer().set(new NamespacedKey(life.plugin,"relic-id"),PersistentDataType.STRING,id);
        var enchantments=life.config.getConfigurationSection(k+".enchantments");if(enchantments!=null)for(String key:enchantments.getKeys(false)){Enchantment enchantment=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));int level=enchantments.getInt(key);if(enchantment==null||level<1||level>enchantment.getMaxLevel())throw new IllegalStateException("Invalid relic enchantment: "+key);meta.addEnchant(enchantment,level,true);}
        if(material==Material.ELYTRA){meta.setEnchantmentGlintOverride(true);meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,new AttributeModifier(new NamespacedKey(life.plugin,"relic-toughness"),3,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.CHEST));meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE,new AttributeModifier(new NamespacedKey(life.plugin,"relic-stability"),0.1,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.CHEST));meta.addAttributeModifier(Attribute.ARMOR,new AttributeModifier(new NamespacedKey(life.plugin,"relic-armor"),8,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.CHEST));}
        item.setItemMeta(meta);return item;
    }
    void validate(){for(String id:ids()){double price=life.config.getDouble("relics."+id+".price");if(!Double.isFinite(price)||price<100000||price>1000000000)throw new IllegalStateException("Invalid relic price");item(id);}}
    void menu(Player p){
        List<CommunityMenu.Entry> entries=new ArrayList<>();for(String id:ids()){ItemStack item=item(id);String k="relics."+id;double price=life.config.getDouble(k+".price");String name=item.getItemMeta().getDisplayName();List<String> lore=new ArrayList<>(item.getItemMeta().getLore());lore.add("§6"+String.format(Locale.ROOT,"%,.0f",price)+" 金币");entries.add(new CommunityMenu.Entry(item.getType(),name,lore,()->life.menus.confirm(p,"购买奇珍",ChatColor.stripColor(name)+"\n"+String.format(Locale.ROOT,"%,.0f",price)+" 金币",()->{life.jobs.buyRelic(p,id);menu(p);},()->menu(p)),item));}
        life.menus.show(p,"奇珍阁","高价珍品，购买后到待领物资领取。",entries,0,()->life.ui.command(p,new String[]{"shop"}),List.of(new CommunityMenu.Entry(Material.CHEST,"§e待领物资",List.of(),()->life.jobs.mail(p))));
    }
}
