import java.io.*;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.*;
import com.sk89q.worldedit.extent.clipboard.io.*;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;

/** Assembly-only console tool. Remove the jar after exporting the clean lobby. */
public final class HubMapImport extends JavaPlugin {
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return false;
        if (args.length > 0 && args[0].equals("verify")) {
            for (World w : Bukkit.getWorlds()) sender.sendMessage("WORLD_VERIFY name="+w.getName()+" key="+w.getKey()+" folder="+w.getWorldFolder()+" spawn="+w.getSpawnLocation());
            World w = Bukkit.getWorld(NamespacedKey.minecraft("lobby"));
            if (w != null) sender.sendMessage("LOBBY_VERIFY ground="+w.getBlockAt(0,99,0).getType()+" feet="+w.getBlockAt(0,100,0).getType()+" head="+w.getBlockAt(0,101,0).getType());
            return true;
        }
        Path marker = getDataFolder().toPath().resolve("map-import-complete.txt");
        if (Files.exists(marker)) { sender.sendMessage("Map already imported; refusing to overwrite."); return true; }
        try {
            File file = new File("plugins/WorldEdit/schematics/kahcerus-lobby.schem");
            Clipboard clipboard;
            try (ClipboardReader reader = ClipboardFormats.findByFile(file).getReader(new FileInputStream(file))) {
                clipboard = reader.read();
            }
            getLogger().info("Importing Kahcerus lobby dimensions " + clipboard.getDimensions());
            World world = new WorldCreator("lobby").type(WorldType.FLAT).generateStructures(false)
                .generatorSettings("{\"biome\":\"minecraft:the_void\",\"layers\":[],\"structure_overrides\":[]}").createWorld();
            if (world == null) throw new IOException("World creation failed");
            try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operations.complete(new ClipboardHolder(clipboard).createPaste(session)
                    .to(BlockVector3.at(0,100,0)).ignoreAirBlocks(true).copyEntities(false).build());
            }
            world.setSpawnLocation(0,100,0,0);
            world.setTime(6000);
            world.setStorm(false);
            world.getWorldBorder().setCenter(0,0);
            world.getWorldBorder().setSize(512);
            world.save();
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "Kahcerus / PMC free lobby / SHA256 455187e15040ef42d9b8df01312e5e9a4c2ed1b10819354945b4483d51638534\n");
            sender.sendMessage("LOBBY_IMPORT_OK spawn=0,100,0 ground="+world.getBlockAt(0,99,0).getType());
        } catch (Exception e) { getLogger().log(java.util.logging.Level.SEVERE,"Lobby import failed",e); }
        return true;
    }
}
