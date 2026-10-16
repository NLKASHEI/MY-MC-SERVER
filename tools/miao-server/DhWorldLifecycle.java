package cn.nankexi;

import org.bukkit.*;

/** DHSupport 0.14.0 can receive ChunkPopulate before WorldLoad on hot creation. */
final class DhWorldLifecycle {
    static void initialized(World world){
        var plugin=Bukkit.getPluginManager().getPlugin("DHSupport");if(plugin==null||!plugin.isEnabled())return;
        try{
            Object core=plugin.getClass().getMethod("getDhSupport").invoke(plugin);Class<?> type=core.getClass();
            if(type.getMethod("getWorldInterface",java.util.UUID.class).invoke(core,world.getUID())!=null)return;
            Object config=type.getMethod("getConfig").invoke(core);ClassLoader loader=plugin.getClass().getClassLoader();
            Class<?> adapter=Class.forName("no.jckf.dhsupport.bukkit.BukkitWorldInterface",true,loader);
            Class<?> basePlugin=Class.forName("no.jckf.dhsupport.bukkit.DhSupportBukkitPlugin",true,loader);
            Class<?> configuration=Class.forName("no.jckf.dhsupport.core.configuration.Configuration",true,loader);
            Object wrapper=adapter.getConstructor(basePlugin,World.class,configuration).newInstance(plugin,world,config);
            adapter.getMethod("setLogger",java.util.logging.Logger.class).invoke(wrapper,type.getMethod("getLogger").invoke(core));
            adapter.getMethod("doUnsafeThings").invoke(wrapper);
            type.getMethod("setWorldInterface",java.util.UUID.class,Class.forName("no.jckf.dhsupport.core.world.WorldInterface",true,loader)).invoke(core,world.getUID(),wrapper);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot initialize locked DHSupport world lifecycle",e);}
    }
}
