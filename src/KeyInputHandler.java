import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.client.registry.ClientRegistry;

public class KeyInputHandler {
    public static bal keyOpenSkins;

    public static void init() {
        // Key 37 is 'K' in LWJGL Keyboard
        keyOpenSkins = new bal("Buscador de Skins de NPC", 37, "Custom NPCs");
        ClientRegistry.registerKeyBinding(keyOpenSkins);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (keyOpenSkins != null && keyOpenSkins.f()) { // isPressed
            bao mc = bao.B();
            if (mc != null && (mc.n == null || mc.n instanceof GuiSkinSearch)) {
                GuiQueue.open(new GuiSkinSearch());
            }
        }
    }
}
