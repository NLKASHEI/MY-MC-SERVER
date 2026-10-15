package cn.nankexi;

import java.util.*;
import java.util.regex.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.geysermc.floodgate.api.FloodgateApi;

/** Online PE account aliases only; nicknames never select an account. */
final class BedrockCommandNames implements Listener {
    private final MiaoInterface ui;
    private static final Pattern WORD=Pattern.compile("\\S+");
    private static final Set<String> FIRST=Set.of("tpa","tpahere","tpaccept","tpdeny","pay","msg","tell","w","whisper","m","message","tpto","trust","untrust","accesstrust","containertrust","permissiontrust");
    BedrockCommandNames(MiaoServer plugin,MiaoInterface ui){this.ui=ui;Bukkit.getPluginManager().registerEvents(this,plugin);}
    private Map<String,String> aliases(Player viewer){
        Map<String,String> result=new HashMap<>();
        for(Player target:Bukkit.getOnlinePlayers()){
            if(!viewer.canSee(target)||!ui.authenticated(target)||!ui.bedrock(target))continue;
            var floodgate=FloodgateApi.getInstance().getPlayer(target.getUniqueId());
            if(floodgate==null)continue;
            String account=floodgate.isLinked()?target.getName():floodgate.getUsername().replace(' ','_');
            result.put("[PE]"+account,target.getName());
        }
        return result;
    }
    private static boolean targetAt(List<String> words,int index){
        if(words.isEmpty()||index<1)return false;
        String command=words.getFirst().replaceFirst("^/","").toLowerCase(Locale.ROOT);
        int colon=command.indexOf(':');
        if(colon>=0){
            if(!Set.of("minecraft","essentials","miaoserver","griefprevention").contains(command.substring(0,colon)))return false;
            command=command.substring(colon+1);
        }
        if(FIRST.contains(command))return index==1;
        if(command.equals("tp")||command.equals("teleport"))return index<=2;
        if(Set.of("mteam","team").contains(command))return index==2&&words.size()>1&&Set.of("invite","kick").contains(words.get(1).toLowerCase(Locale.ROOT));
        return command.equals("miao")&&index==3&&words.size()>2&&words.get(1).equalsIgnoreCase("names")&&words.get(2).equalsIgnoreCase("note");
    }
    static String rewrite(String input,Map<String,String> aliases){
        Matcher matcher=WORD.matcher(input);List<String> words=new ArrayList<>();StringBuilder output=new StringBuilder();int end=0;
        while(matcher.find()){
            String word=matcher.group();words.add(word);String replacement=word;
            if(targetAt(words,words.size()-1))for(var alias:aliases.entrySet())if(alias.getKey().equalsIgnoreCase(word)){replacement=alias.getValue();break;}
            output.append(input,end,matcher.start()).append(replacement);end=matcher.end();
        }
        return output.append(input,end,input.length()).toString();
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent event){
        if(!event.getMessage().toLowerCase(Locale.ROOT).contains("[pe]")||!ui.authenticated(event.getPlayer()))return;
        event.setMessage(rewrite(event.getMessage(),aliases(event.getPlayer())));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void complete(TabCompleteEvent event){
        if(!(event.getSender() instanceof Player viewer)||!ui.authenticated(viewer))return;
        String buffer=event.getBuffer();List<String> words=new ArrayList<>(Arrays.asList(buffer.split("\\s+",-1)));
        int index=words.size()-1;if(!targetAt(words,index))return;
        String prefix=words.get(index).toLowerCase(Locale.ROOT);List<String> completions=new ArrayList<>(event.getCompletions());
        for(var alias:aliases(viewer).entrySet()){
            if(!alias.getKey().toLowerCase(Locale.ROOT).startsWith(prefix))continue;
            completions.removeIf(s->s.equalsIgnoreCase(alias.getValue()));
            if(!completions.contains(alias.getKey()))completions.add(alias.getKey());
        }
        event.setCompletions(completions);
    }
}
