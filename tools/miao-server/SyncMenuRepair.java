package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Replace only the old shipped guide lines; leave account databases and custom text alone. */
final class SyncMenuRepair {
    static void apply(MiaoServer p){
        File marker=new File(p.getDataFolder(),"sync-menu-v0922.done");if(marker.exists())return;
        Path file=new File(p.getDataFolder().getParentFile(),"Skript/scripts/survival.sk").toPath();
        try{
            if(Files.exists(file)){
                String old=Files.readString(file,StandardCharsets.UTF_8),text=old
                    .replace("先登录两个账号，点击账号菜单的绑定按钮，或使用 /accountlink。","两个账号各注册登录一次，再用 /accountlink 打开可选同步面板。")
                    .replace("Java 输入 /linkaccount 手机Xbox名；PE 输入 /linkaccount Java账号名 验证码。","支持 PC/PE 互通及同 IP 同平台账号；也可 /miao sync select 对方名字。")
                    .replace("可选择保留 PE 或 PC 进度，验证对方的服内密码；旧数据保留，不叠加背包金币。","先选择保留谁的进度，再按提示验证对方服内密码；取消则各自进度不变。")
                    .replace("同一账号不能双端同时在线；/unlinkaccount 解除绑定。","共享进度只能一个账号在线；/miao sync unlink 解除说明；不叠加物品金币。");
                if(!old.equals(text)){Path backup=file.resolveSibling(file.getFileName()+".before-sync-menu-v0922");if(!Files.exists(backup))Files.copy(file,backup);Files.writeString(file,text,StandardCharsets.UTF_8);}
            }
            Files.writeString(marker.toPath(),"Updated shipped sync guide only; account data retained\n");
        }catch(IOException e){throw new IllegalStateException("Cannot update sync menu guide",e);}
    }
}
