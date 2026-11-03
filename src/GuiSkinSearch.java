import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class GuiSkinSearch extends bdw {
    private bcd searchBox;
    private List<String> filteredList = new ArrayList<String>();
    private int selectedIdx = -1;
    private int scrollOffset = 0;
    private String localStatusText = "";
    private long localStatusClearTime = 0;

    private static final int ITEMS_PER_PAGE = 7;
    private static final int ITEM_HEIGHT = 20;

    @Override
    @SuppressWarnings("unchecked")
    public void b() { // initGui
        this.n.clear();

        // Always reload local files and invalidate Custom NPCs cache when opening GUI
        SkinSyncManager.reloadLocalSkins();

        int boxW = 260;
        int boxX = (this.l - boxW) / 2;
        this.searchBox = new bcd(this.q, boxX, 35, boxW, 20);
        this.searchBox.f(64); // setMaxStringLength
        this.searchBox.b(true); // setFocused

        int leftX = (this.l - 260) / 2;
        int btnRow1Y = this.m - 50;
        int btnRow2Y = this.m - 26;

        // Row 1: 3 buttons (Width: 84 each)
        this.n.add(new bcb(1, leftX, btnRow1Y, 84, 20, "§aCopiar Ruta"));
        this.n.add(new bcb(5, leftX + 88, btnRow1Y, 84, 20, "§2⬆ Subir"));
        this.n.add(new bcb(2, leftX + 176, btnRow1Y, 84, 20, "§eSincronizar"));

        // Row 2: 2 buttons (Width: 127 each)
        this.n.add(new bcb(3, leftX, btnRow2Y, 127, 20, "§7Abrir Carpeta"));
        this.n.add(new bcb(4, leftX + 133, btnRow2Y, 127, 20, "Cerrar"));

        updateFilter();
    }

    private void updateFilter() {
        String query = "";
        if (this.searchBox != null && this.searchBox.b() != null) {
            query = this.searchBox.b().trim().toLowerCase();
        }

        List<String> all = SkinSyncManager.getAvailableSkins();
        filteredList.clear();

        for (String s : all) {
            String cleanName = s.toLowerCase();
            if (cleanName.endsWith(".png")) {
                cleanName = cleanName.substring(0, cleanName.length() - 4);
            }
            if (query.isEmpty() || cleanName.contains(query)) {
                filteredList.add(s);
            }
        }

        if (selectedIdx >= filteredList.size()) {
            selectedIdx = filteredList.isEmpty() ? -1 : 0;
        }
        if (selectedIdx < 0 && !filteredList.isEmpty()) {
            selectedIdx = 0;
        }

        // Adjust scroll
        if (scrollOffset > Math.max(0, filteredList.size() - ITEMS_PER_PAGE)) {
            scrollOffset = Math.max(0, filteredList.size() - ITEMS_PER_PAGE);
        }
    }

    @Override
    public void a(int mouseX, int mouseY, float partialTicks) { // drawScreen
        this.c(); // drawDefaultBackground

        // Title and header
        String title = "§6§lBuscador y Gestor de Skins de NPC";
        this.q.a(title, (this.l - this.q.a(title)) / 2, 8, 0xFFFFFF);

        String subtitle = "§7Repo: §f" + SkinSyncManager.GITHUB_OWNER + "/" + SkinSyncManager.GITHUB_REPO +
                          " §7| Total skins: §a" + SkinSyncManager.getAvailableSkins().size();
        this.q.a(subtitle, (this.l - this.q.a(subtitle)) / 2, 22, 0xAAAAAA);

        // Draw Search Box
        this.searchBox.f(); // drawTextBox

        // Draw Skins List Box
        int listX = (this.l - 260) / 2;
        int listY = 60;
        int listW = 260;
        int listH = ITEMS_PER_PAGE * ITEM_HEIGHT + 2;

        a(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF222222);
        a(listX, listY, listX + listW, listY + listH, 0xFF111111);

        if (filteredList.isEmpty()) {
            String empty = "§8No se encontraron skins";
            this.q.a(empty, listX + (listW - this.q.a(empty)) / 2, listY + 50, 0x888888);
        } else {
            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int itemIndex = scrollOffset + i;
                if (itemIndex >= filteredList.size()) break;

                String skinName = filteredList.get(itemIndex);
                String displayName = skinName.endsWith(".png") ? skinName.substring(0, skinName.length() - 4) : skinName;
                int itemY = listY + (i * ITEM_HEIGHT) + 2;

                boolean isSelected = (itemIndex == selectedIdx);
                if (isSelected) {
                    a(listX + 2, itemY, listX + listW - 2, itemY + ITEM_HEIGHT - 2, 0xFF2A52BE);
                    this.q.a("§a§l> §f" + displayName, listX + 8, itemY + 4, 0xFFFFFF);
                } else {
                    boolean isHover = (mouseX >= listX && mouseX <= listX + listW && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT);
                    if (isHover) {
                        a(listX + 2, itemY, listX + listW - 2, itemY + ITEM_HEIGHT - 2, 0xFF333333);
                    }
                    this.q.a("  §7" + displayName, listX + 8, itemY + 4, 0xDDDDDD);
                }
            }
        }

        // Draw hint or status messages (priority: globalStatus > localStatusText > syncing status > hint)
        String globalMsg = SkinSyncManager.getGlobalStatus();
        if (!globalMsg.isEmpty()) {
            this.q.a(globalMsg, (this.l - this.q.a(globalMsg)) / 2, this.m - 64, 0xFFFFFF);
        } else if (System.currentTimeMillis() < localStatusClearTime && !localStatusText.isEmpty()) {
            this.q.a(localStatusText, (this.l - this.q.a(localStatusText)) / 2, this.m - 64, 0x55FF55);
        } else if (SkinSyncManager.isSyncing()) {
            String syncStr = "§e" + SkinSyncManager.getLastStatus();
            this.q.a(syncStr, (this.l - this.q.a(syncStr)) / 2, this.m - 64, 0xFFFF55);
        } else {
            String hint = "§8Elige una skin y pulsa Copiar Ruta para pegarla en tu NPC";
            this.q.a(hint, (this.l - this.q.a(hint)) / 2, this.m - 64, 0x888888);
        }

        super.a(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void a(bcb button) { // actionPerformed
        if (button.k == 1) { // Copiar Ruta
            copySelectedSkin();
        } else if (button.k == 5) { // Subir Skin
            openSkinUploadDialog();
        } else if (button.k == 2) { // Sincronizar
            SkinSyncManager.setGlobalStatus("§eConectando y sincronizando con GitHub...", 8000);
            SkinSyncManager.syncAsync(new Runnable() {
                @Override
                public void run() {
                    updateFilter();
                }
            });
        } else if (button.k == 3) { // Abrir carpeta
            try {
                File dir = SkinSyncManager.getCustomNpcsDir();
                Desktop.getDesktop().open(dir);
                localStatusText = "§aCarpeta abierta. Puedes arrastrar tus .png ahí y pulsar Sincronizar.";
                localStatusClearTime = System.currentTimeMillis() + 6000;
            } catch (Exception e) {
                try {
                    Desktop.getDesktop().open(SkinSyncManager.getSkinsDir());
                } catch (Exception ignored) {}
            }
        } else if (button.k == 4) { // Cerrar
            this.k.a((bdw)null);
        }
    }

    private void openSkinUploadDialog() {
        SkinSyncManager.setGlobalStatus("§eAbriendo selector de archivos...", 6000);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    } catch (Throwable ignored) {}

                    // Topmost dummy frame ensures dialog appears in front of Minecraft
                    JFrame dummy = new JFrame();
                    dummy.setUndecorated(true);
                    dummy.setSize(0, 0);
                    dummy.setLocationRelativeTo(null);
                    dummy.setAlwaysOnTop(true);
                    dummy.setVisible(true);
                    dummy.toFront();
                    dummy.requestFocus();

                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("Selecciona una skin de Minecraft (.png 64x64)");
                    chooser.setFileFilter(new FileNameExtensionFilter("Skins de Minecraft (*.png)", "png"));
                    chooser.setAcceptAllFileFilterUsed(false);

                    File desktop = new File(System.getProperty("user.home"), "Desktop");
                    if (desktop.exists()) {
                        chooser.setCurrentDirectory(desktop);
                    }

                    int res = chooser.showOpenDialog(dummy);
                    dummy.dispose();

                    if (res == JFileChooser.APPROVE_OPTION) {
                        final File chosen = chooser.getSelectedFile();
                        if (chosen != null) {
                            SkinSyncManager.uploadSkinAsync(chosen, new Runnable() {
                                @Override
                                public void run() {
                                    updateFilter();
                                }
                            });
                        }
                    } else {
                        SkinSyncManager.setGlobalStatus("§7Subida cancelada.", 3000);
                    }
                } catch (Throwable t) {
                    SkinSyncManager.setGlobalStatus("§cError al abrir ventana: " + t.getMessage(), 5000);
                }
            }
        });
    }

    private void copySelectedSkin() {
        if (selectedIdx >= 0 && selectedIdx < filteredList.size()) {
            String skin = filteredList.get(selectedIdx);
            if (!skin.toLowerCase().endsWith(".png")) {
                skin = skin + ".png";
            }
            // The exact format needed by Custom NPCs:
            String texturePath = "customnpcs:textures/entity/" + skin;
            try {
                StringSelection selection = new StringSelection(texturePath);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                localStatusText = "§a¡Ruta copiada! Pégala con Ctrl+V en Display -> Texture del NPC";
                localStatusClearTime = System.currentTimeMillis() + 5000;
            } catch (Exception e) {
                localStatusText = "§cError al copiar";
                localStatusClearTime = System.currentTimeMillis() + 3000;
            }
        } else {
            localStatusText = "§cSelecciona una skin primero";
            localStatusClearTime = System.currentTimeMillis() + 3000;
        }
    }

    @Override
    protected void a(char typedChar, int keyCode) { // keyTyped
        if (keyCode == 1) { // ESC
            this.k.a((bdw)null);
            return;
        }

        if (this.searchBox.a(typedChar, keyCode)) { // textboxKeyTyped
            updateFilter();
            return;
        }

        // Arrow keys navigation
        if (keyCode == 200) { // UP arrow
            if (selectedIdx > 0) {
                selectedIdx--;
                if (selectedIdx < scrollOffset) {
                    scrollOffset = selectedIdx;
                }
            }
            return;
        }
        if (keyCode == 208) { // DOWN arrow
            if (selectedIdx < filteredList.size() - 1) {
                selectedIdx++;
                if (selectedIdx >= scrollOffset + ITEMS_PER_PAGE) {
                    scrollOffset = selectedIdx - ITEMS_PER_PAGE + 1;
                }
            }
            return;
        }
        if (keyCode == 28) { // ENTER
            copySelectedSkin();
            return;
        }

        super.a(typedChar, keyCode);
    }

    @Override
    protected void a(int mouseX, int mouseY, int mouseButton) { // mouseClicked
        super.a(mouseX, mouseY, mouseButton);
        this.searchBox.a(mouseX, mouseY, mouseButton);

        int listX = (this.l - 260) / 2;
        int listY = 60;
        int listW = 260;

        if (mouseX >= listX && mouseX <= listX + listW) {
            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int itemIndex = scrollOffset + i;
                if (itemIndex >= filteredList.size()) break;

                int itemY = listY + (i * ITEM_HEIGHT) + 2;
                if (mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT) {
                    if (selectedIdx == itemIndex) {
                        copySelectedSkin();
                    } else {
                        selectedIdx = itemIndex;
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void e() { // updateScreen
        this.searchBox.a(); // cursor counter
    }

    @Override
    public boolean d() { // doesGuiPauseGame
        return false;
    }
}
