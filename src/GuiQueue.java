import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class GuiQueue {
    private static volatile bdw screenToOpen = null;

    public static void open(bdw screen) {
        screenToOpen = screen;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && screenToOpen != null) {
            try {
                bao mc = bao.B();
                if (mc != null) {
                    mc.a(screenToOpen); // displayGuiScreen
                }
            } catch (Throwable ignored) {}
            screenToOpen = null;
        }
    }
}
