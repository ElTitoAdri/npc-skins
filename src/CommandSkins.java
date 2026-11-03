import java.awt.Desktop;
import java.io.File;
import java.util.*;

public class CommandSkins implements aa {
    private final List<String> aliases = Arrays.asList("npcskins", "buscarskins");

    @Override
    public int compareTo(Object o) {
        return 0;
    }

    @Override
    public String c() { // getCommandName
        return "skins";
    }

    @Override
    public String c(ac sender) { // getCommandUsage
        return "/skins [sync|list|open]";
    }

    @Override
    public List b() { // getCommandAliases
        return this.aliases;
    }

    @Override
    public void b(final ac sender, String[] args) { // processCommand
        if (args.length == 0) {
            // Open GUI on client
            try {
                bao.B().a(new GuiSkinSearch());
            } catch (Throwable t) {
                sender.a(new fq("§cError al abrir el buscador de skins: " + t.getMessage()));
            }
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("sync")) {
            sender.a(new fq("§e[NPCSkins] Conectando con GitHub (" + SkinSyncManager.GITHUB_OWNER + "/" + SkinSyncManager.GITHUB_REPO + ")..."));
            SkinSyncManager.syncAsync(new Runnable() {
                @Override
                public void run() {
                    sender.a(new fq("§a[NPCSkins] " + SkinSyncManager.getLastStatus()));
                }
            });
        } else if (sub.equals("open")) {
            try {
                File dir = SkinSyncManager.getCustomNpcsDir();
                Desktop.getDesktop().open(dir);
                sender.a(new fq("§a[NPCSkins] Carpeta de skins abierta en el explorador."));
            } catch (Exception e) {
                sender.a(new fq("§c[NPCSkins] No se pudo abrir la carpeta: " + e.getMessage()));
            }
        } else if (sub.equals("list")) {
            List<String> list = SkinSyncManager.getAvailableSkins();
            sender.a(new fq("§6=== Skins de NPC Disponibles (" + list.size() + ") ==="));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                String s = list.get(i);
                if (s.endsWith(".png")) s = s.substring(0, s.length() - 4);
                sb.append("§e").append(s);
                if (i < list.size() - 1) sb.append("§7, ");
            }
            sender.a(new fq(sb.toString()));
        } else {
            sender.a(new fq("§6=== Comandos de Skins ==="));
            sender.a(new fq("§e/skins §7- Abre el buscador visual de skins"));
            sender.a(new fq("§e/skins sync §7- Sincroniza las skins con GitHub ahora"));
            sender.a(new fq("§e/skins open §7- Abre la carpeta local de skins"));
            sender.a(new fq("§e/skins list §7- Lista las skins descargadas en el chat"));
        }
    }

    @Override
    public boolean a(ac sender) { // canCommandSenderUseCommand
        return true;
    }

    @Override
    public List a(ac sender, String[] args) { // addTabCompletionOptions
        if (args.length == 1) {
            List<String> list = new ArrayList<String>();
            String p = args[0].toLowerCase();
            for (String opt : Arrays.asList("sync", "list", "open")) {
                if (opt.startsWith(p)) list.add(opt);
            }
            return list;
        }
        return null;
    }

    @Override
    public boolean a(String[] args, int index) { // isUsernameIndex
        return false;
    }
}
