package cn.nankexi;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.geysermc.cumulus.form.*;
import org.geysermc.floodgate.api.FloodgateApi;

/** One layout for native Java inventories and Bedrock forms. Text is deliberately short. */
final class CommunityMenu implements Listener {
    record Entry(Material icon,String name,List<String> lore,Runnable action,ItemStack preview){Entry(Material icon,String name,List<String> lore,Runnable action){this(icon,name,lore,action,null);}}
    private final CommunityLife life;
    private static final int[] GRID={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final class Holder implements InventoryHolder {
        final UUID owner;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        Holder(Player p){owner=p.getUniqueId();}public Inventory getInventory(){return inventory;}
    }
    CommunityMenu(CommunityLife life){this.life=life;Bukkit.getPluginManager().registerEvents(this,life.plugin);}
    private void put(Holder h,int slot,Entry entry){ItemStack item=entry.preview()==null?new ItemStack(entry.icon()):entry.preview().clone();var meta=item.getItemMeta();meta.setDisplayName(entry.name());meta.setLore(entry.lore());item.setItemMeta(meta);h.inventory.setItem(slot,item);h.actions.put(slot,entry.action());}
    void show(Player p,String title,String info,List<Entry> entries,int requested,Runnable back,List<Entry> tools){
        boolean pe=life.ui.bedrock(p);int size=pe?8:28,page=Math.max(0,Math.min(requested,Math.max(0,(entries.size()-1)/size))),start=page*size,end=Math.min(entries.size(),start+size);
        if(pe){
            var form=SimpleForm.builder().title(title).content(info+"\n第 "+(page+1)+" / "+Math.max(1,(entries.size()+size-1)/size)+" 页");List<Runnable> actions=new ArrayList<>();
            for(int i=start;i<end;i++){Entry entry=entries.get(i);String line=entry.lore().isEmpty()?"":ChatColor.stripColor(entry.lore().get(0));form.button(ChatColor.stripColor(entry.name())+(line.isEmpty()?"":"\n"+line));actions.add(entry.action());}
            if(page>0){form.button("上一页");actions.add(()->show(p,title,info,entries,page-1,back,tools));}
            if(end<entries.size()){form.button("下一页");actions.add(()->show(p,title,info,entries,page+1,back,tools));}
            for(Entry tool:tools){form.button(ChatColor.stripColor(tool.name()));actions.add(tool.action());}form.button("返回");actions.add(back);
            form.validResultHandler(r->life.later(p,()->actions.get(r.clickedButtonId()).run()));FloodgateApi.getInstance().sendForm(p.getUniqueId(),form.build());return;
        }
        Holder h=new Holder(p);h.inventory=Bukkit.createInventory(h,54,"§8"+title);
        Entry pane=new Entry(Material.GRAY_STAINED_GLASS_PANE," ",List.of(),()->{});for(int i=0;i<54;i++)put(h,i,pane);
        put(h,4,new Entry(Material.BOOK,"§f"+title,List.of("§7"+info),()->{}));
        int[] centered=switch(entries.size()){case 1->new int[]{22};case 2->new int[]{21,23};case 3->new int[]{21,22,23};case 4->new int[]{20,21,23,24};case 5->new int[]{20,21,22,23,24};default->GRID;};
        for(int i=start;i<end;i++)put(h,centered[i-start],entries.get(i));
        if(page>0)put(h,45,new Entry(Material.ARROW,"§f上一页",List.of(),()->show(p,title,info,entries,page-1,back,tools)));
        if(end<entries.size())put(h,53,new Entry(Material.ARROW,"§f下一页",List.of(),()->show(p,title,info,entries,page+1,back,tools)));
        put(h,49,new Entry(Material.COMPASS,"§f返回",List.of(),back));int[] slots={46,47,51,52};for(int i=0;i<Math.min(tools.size(),slots.length);i++)put(h,slots[i],tools.get(i));p.openInventory(h.inventory);
    }
    void confirm(Player p,String title,String text,Runnable accept,Runnable cancel){
        var used=new java.util.concurrent.atomic.AtomicBoolean();Runnable onceAccept=()->{if(used.compareAndSet(false,true))accept.run();},onceCancel=()->{if(used.compareAndSet(false,true))cancel.run();};
        if(life.ui.bedrock(p)){FloodgateApi.getInstance().sendForm(p.getUniqueId(),ModalForm.builder().title(title).content(text).button1("确认").button2("取消").validResultHandler(r->life.later(p,r.clickedButtonId()==0?onceAccept:onceCancel)).build());return;}
        Holder h=new Holder(p);h.inventory=Bukkit.createInventory(h,27,"§8"+title);put(h,4,new Entry(Material.PAPER,"§f"+title,Arrays.stream(text.split("\n")).map(s->"§7"+s).toList(),()->{}));put(h,11,new Entry(Material.LIME_DYE,"§a确认",List.of(),onceAccept));put(h,15,new Entry(Material.BARRIER,"§7取消",List.of(),onceCancel));p.openInventory(h.inventory);
    }
    void info(Player p,String title,List<String> lines,Runnable back){
        if(life.ui.bedrock(p)){FloodgateApi.getInstance().sendForm(p.getUniqueId(),SimpleForm.builder().title(ChatColor.stripColor(title)).content(String.join("\n",lines.stream().map(ChatColor::stripColor).toList())).button("返回小报").validResultHandler(r->life.later(p,back)).build());}
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p&&p.getUniqueId().equals(h.owner)&&e.getRawSlot()>=0&&e.getRawSlot()<h.inventory.getSize()){Runnable action=h.actions.get(e.getRawSlot());if(action!=null)life.later(p,action);}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
}
