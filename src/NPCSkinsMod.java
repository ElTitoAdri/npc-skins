import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.client.ClientCommandHandler;

@Mod(modid = "npcskins", name = "NPC Skins Sync", version = "1.0", acceptableRemoteVersions = "*")
public class NPCSkinsMod {

    @Mod.Instance("npcskins")
    public static NPCSkinsMod instance;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            initClient();
        }
    }

    @SideOnly(Side.CLIENT)
    private void initClient() {
        try {
            // Register client command /skins
            ClientCommandHandler.instance.a(new CommandSkins());
        } catch (Throwable t) {
            System.err.println("[NPCSkins] Error al registrar comando /skins: " + t.getMessage());
        }

        try {
            // Register keybinding (K)
            KeyInputHandler.init();
            FMLCommonHandler.instance().bus().register(new KeyInputHandler());
        } catch (Throwable t) {
            System.err.println("[NPCSkins] Error al registrar KeyBinding: " + t.getMessage());
        }

        try {
            // Register GuiQueue for frame-safe GUI opening
            FMLCommonHandler.instance().bus().register(new GuiQueue());
        } catch (Throwable t) {
            System.err.println("[NPCSkins] Error al registrar GuiQueue: " + t.getMessage());
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            System.out.println("[NPCSkins] Iniciando sincronización de skins con GitHub (ElTitoAdri/npc-skins)...");
            SkinSyncManager.syncAsync(new Runnable() {
                @Override
                public void run() {
                    System.out.println("[NPCSkins] " + SkinSyncManager.getLastStatus());
                }
            });
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Safe server-side command handler
        try {
            event.registerServerCommand(new CommandSkins());
        } catch (Throwable ignored) {}
    }
}
