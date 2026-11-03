import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class SkinSyncManager {
    public static final String GITHUB_OWNER = "ElTitoAdri";
    public static final String GITHUB_REPO = "npc-skins";
    public static final String API_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/contents";

    private static final List<String> availableSkins = new CopyOnWriteArrayList<String>();
    private static volatile boolean isSyncing = false;
    private static volatile String lastStatus = "Listo";

    public static File getMinecraftDir() {
        try {
            bao mc = bao.B();
            if (mc != null && mc.w != null) {
                return mc.w;
            }
        } catch (Throwable ignored) {}
        return new File(".");
    }

    public static File getCustomNpcsDir() {
        File mc = getMinecraftDir();
        File dir = new File(mc, "customnpcs/assets/customnpcs/textures/entity");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getSkinsDir() {
        File mc = getMinecraftDir();
        File dir = new File(mc, "npc_skins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<String> getAvailableSkins() {
        if (availableSkins.isEmpty()) {
            reloadLocalSkins();
        }
        return Collections.unmodifiableList(availableSkins);
    }

    public static boolean isSyncing() {
        return isSyncing;
    }

    public static String getLastStatus() {
        return lastStatus;
    }

    public static void reloadLocalSkins() {
        Set<String> set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        File dir1 = getCustomNpcsDir();
        if (dir1.exists()) {
            File[] files = dir1.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) {
                        set.add(f.getName());
                    }
                }
            }
        }
        File dir2 = getSkinsDir();
        if (dir2.exists()) {
            File[] files = dir2.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) {
                        set.add(f.getName());
                    }
                }
            }
        }
        availableSkins.clear();
        availableSkins.addAll(set);
    }

    public static void syncAsync(final Runnable onComplete) {
        if (isSyncing) return;
        isSyncing = true;
        lastStatus = "Conectando con GitHub...";

        new Thread(new Runnable() {
            @Override
            public void run() {
                int downloaded = 0;
                try {
                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "NPCSkinsMod-Minecraft-1.7.10");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(12000);

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        List<SkinFileInfo> list = parseGithubContents(sb.toString());
                        File target1 = getCustomNpcsDir();
                        File target2 = getSkinsDir();

                        for (SkinFileInfo info : list) {
                            if (info.name.toLowerCase().endsWith(".png")) {
                                File file1 = new File(target1, info.name);
                                File file2 = new File(target2, info.name);

                                if (!file1.exists() || file1.length() != info.size) {
                                    downloadFile(info.downloadUrl, file1);
                                    downloadFile(info.downloadUrl, file2);
                                    downloaded++;
                                }
                            }
                        }
                        reloadLocalSkins();
                        lastStatus = "Sincronizado: " + availableSkins.size() + " skins (" + downloaded + " nuevas)";
                    } else {
                        lastStatus = "GitHub error: HTTP " + code;
                        reloadLocalSkins();
                    }
                } catch (Exception e) {
                    lastStatus = "Error de conexion con GitHub";
                    reloadLocalSkins();
                } finally {
                    isSyncing = false;
                    if (onComplete != null) {
                        try {
                            onComplete.run();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }, "NPCSkins-Sync-Thread").start();
    }

    private static void downloadFile(String fileUrl, File dest) {
        if (fileUrl == null || fileUrl.isEmpty()) return;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "NPCSkinsMod-Minecraft-1.7.10");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);

            if (conn.getResponseCode() == 200) {
                in = conn.getInputStream();
                out = new FileOutputStream(dest);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    private static List<SkinFileInfo> parseGithubContents(String json) {
        List<SkinFileInfo> res = new ArrayList<SkinFileInfo>();
        int idx = 0;
        while ((idx = json.indexOf("{\"name\":\"", idx)) != -1) {
            idx += 9;
            int endName = json.indexOf("\"", idx);
            if (endName == -1) break;
            String name = json.substring(idx, endName);

            String dlUrl = null;
            int dlIdx = json.indexOf("\"download_url\":\"", idx);
            if (dlIdx != -1) {
                dlIdx += 16;
                int endDl = json.indexOf("\"", dlIdx);
                if (endDl != -1) {
                    dlUrl = json.substring(dlIdx, endDl);
                }
            }

            long size = 0;
            int sizeIdx = json.indexOf("\"size\":", idx);
            if (sizeIdx != -1) {
                sizeIdx += 7;
                int endSize = sizeIdx;
                while (endSize < json.length() && Character.isDigit(json.charAt(endSize))) {
                    endSize++;
                }
                try {
                    size = Long.parseLong(json.substring(sizeIdx, endSize));
                } catch (Exception ignored) {}
            }

            if (dlUrl != null && !dlUrl.equals("null")) {
                SkinFileInfo info = new SkinFileInfo();
                info.name = name;
                info.downloadUrl = dlUrl;
                info.size = size;
                res.add(info);
            }
        }
        return res;
    }

    private static class SkinFileInfo {
        String name;
        String downloadUrl;
        long size;
    }
}
