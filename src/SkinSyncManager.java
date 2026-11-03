import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public class SkinSyncManager {
    public static final String GITHUB_OWNER = "ElTitoAdri";
    public static final String GITHUB_REPO = "npc-skins";
    public static final String API_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/contents";

    private static final List<String> availableSkins = new CopyOnWriteArrayList<String>();
    private static volatile boolean isSyncing = false;
    private static volatile String lastStatus = "Listo";
    private static volatile String globalStatus = "";
    private static volatile long globalStatusUntil = 0;

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

    public static String getAuthToken() {
        try {
            File tokenFile = new File(getMinecraftDir(), "npc_skins_token.txt");
            if (tokenFile.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(tokenFile));
                String t = br.readLine();
                br.close();
                if (t != null && !t.trim().isEmpty()) {
                    return t.trim();
                }
            }
        } catch (Exception ignored) {}

        try {
            InputStream in = SkinSyncManager.class.getResourceAsStream("/token.dat");
            if (in != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String t = br.readLine();
                br.close();
                if (t != null && !t.trim().isEmpty()) {
                    return t.trim();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static List<String> getAvailableSkins() {
        reloadLocalSkins();
        return Collections.unmodifiableList(availableSkins);
    }

    public static boolean isSyncing() {
        return isSyncing;
    }

    public static String getLastStatus() {
        return lastStatus;
    }

    public static String getGlobalStatus() {
        if (System.currentTimeMillis() < globalStatusUntil) {
            return globalStatus;
        }
        return "";
    }

    public static void setGlobalStatus(String text, int displayMs) {
        globalStatus = text;
        globalStatusUntil = System.currentTimeMillis() + displayMs;
    }

    public static void invalidateCustomNpcsCache() {
        try {
            Class<?> clazz = Class.forName("noppes.npcs.client.gui.select.GuiTextureSelection");
            Field fDomains = clazz.getDeclaredField("cachedDomains");
            fDomains.setAccessible(true);
            fDomains.set(null, null);

            Field fTime = clazz.getDeclaredField("lastCacheTime");
            fTime.setAccessible(true);
            fTime.setLong(null, 0L);

            Field fTextures = clazz.getDeclaredField("cachedTextures");
            fTextures.setAccessible(true);
            fTextures.set(null, null);
        } catch (Throwable ignored) {}
    }

    public static void reloadLocalSkins() {
        Set<String> set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        File dir1 = getCustomNpcsDir();
        File dir2 = getSkinsDir();

        if (dir1.exists()) {
            File[] files = dir1.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) {
                        set.add(f.getName());
                        File f2 = new File(dir2, f.getName());
                        if (!f2.exists()) {
                            copyFile(f, f2);
                        }
                    }
                }
            }
        }
        if (dir2.exists()) {
            File[] files = dir2.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) {
                        set.add(f.getName());
                        File f1 = new File(dir1, f.getName());
                        if (!f1.exists()) {
                            copyFile(f, f1);
                        }
                    }
                }
            }
        }
        availableSkins.clear();
        availableSkins.addAll(set);
        invalidateCustomNpcsCache();
    }

    public static void syncAsync(final Runnable onComplete) {
        if (isSyncing) return;
        isSyncing = true;
        lastStatus = "Conectando con GitHub...";

        new Thread(new Runnable() {
            @Override
            public void run() {
                int downloaded = 0;
                int uploaded = 0;
                try {
                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "NPCSkinsMod-Minecraft-1.7.10");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    String token = getAuthToken();
                    if (token != null && !token.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + token);
                    }
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
                        Set<String> githubNames = new HashSet<String>();
                        for (SkinFileInfo info : list) {
                            githubNames.add(info.name.toLowerCase());
                        }

                        File target1 = getCustomNpcsDir();
                        File target2 = getSkinsDir();

                        // 1. Download skins from GitHub that are missing locally
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

                        // 2. Also auto-upload valid local skins that aren't on GitHub yet
                        if (target1.exists()) {
                            File[] localFiles = target1.listFiles();
                            if (localFiles != null) {
                                for (File f : localFiles) {
                                    if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) {
                                        if (!githubNames.contains(f.getName().toLowerCase())) {
                                            if (validateSkinFile(f) == null) {
                                                boolean ok = uploadSkinSync(f);
                                                if (ok) uploaded++;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        reloadLocalSkins();
                        String extra = "";
                        if (downloaded > 0 || uploaded > 0) {
                            extra = " (+" + downloaded + " descargadas, " + uploaded + " subidas)";
                        }
                        lastStatus = "Sincronizado: " + availableSkins.size() + " skins" + extra;
                        setGlobalStatus("§a" + lastStatus, 6000);
                    } else {
                        lastStatus = "GitHub error: HTTP " + code;
                        reloadLocalSkins();
                        setGlobalStatus("§c" + lastStatus, 5000);
                    }
                } catch (Exception e) {
                    lastStatus = "Error de conexion con GitHub";
                    reloadLocalSkins();
                    setGlobalStatus("§c" + lastStatus, 5000);
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

    public static String validateSkinFile(File file) {
        if (!file.exists() || !file.isFile()) {
            return "El archivo seleccionado no existe.";
        }
        if (!file.getName().toLowerCase().endsWith(".png")) {
            return "Formato incorrecto: debe ser una imagen .png";
        }
        if (file.length() > 300 * 1024) {
            return "Archivo demasiado grande (máximo 300 KB).";
        }
        if (file.length() < 100) {
            return "El archivo está vacío o dañado.";
        }
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                return "No se pudo leer la imagen (archivo PNG inválido).";
            }
            int w = img.getWidth();
            int h = img.getHeight();

            // Valid Minecraft Skin formats:
            // 64x32 (Classic 1.7)
            // 64x64 (Modern standard)
            // 128x128 (HD skins)
            boolean isValidDimension = (w == 64 && (h == 32 || h == 64)) || (w == 128 && h == 128);
            if (!isValidDimension) {
                return "Dimensiones inválidas (" + w + "x" + h + " px). Debe medir 64x64 o 64x32 píxeles.";
            }
        } catch (Exception e) {
            return "Error al verificar la imagen: " + e.getMessage();
        }
        return null;
    }

    public static boolean uploadSkinSync(File file) {
        try {
            String error = validateSkinFile(file);
            if (error != null) return false;

            String token = getAuthToken();
            if (token == null || token.isEmpty()) return false;

            String rawName = file.getName();
            if (rawName.toLowerCase().endsWith(".png")) {
                rawName = rawName.substring(0, rawName.length() - 4);
            }
            String cleanName = rawName.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
            if (cleanName.isEmpty()) {
                cleanName = "skin_" + (System.currentTimeMillis() % 100000);
            }
            String finalFileName = cleanName + ".png";

            byte[] bytes = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            int totalRead = 0;
            while (totalRead < bytes.length) {
                int r = fis.read(bytes, totalRead, bytes.length - totalRead);
                if (r == -1) break;
                totalRead += r;
            }
            fis.close();

            String base64Content = Base64.getEncoder().encodeToString(bytes);
            String sha = getFileSha(finalFileName);

            String jsonPayload;
            if (sha != null && !sha.isEmpty()) {
                jsonPayload = "{\"message\":\"Actualizar skin " + cleanName + "\",\"content\":\"" + base64Content + "\",\"sha\":\"" + sha + "\"}";
            } else {
                jsonPayload = "{\"message\":\"Subir skin " + cleanName + "\",\"content\":\"" + base64Content + "\"}";
            }

            URL url = new URL(API_URL + "/" + finalFileName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("User-Agent", "NPCSkinsMod-Minecraft-1.7.10");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            OutputStream os = conn.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code == 200 || code == 201) {
                File local1 = new File(getCustomNpcsDir(), finalFileName);
                File local2 = new File(getSkinsDir(), finalFileName);
                if (!file.equals(local1)) copyFile(file, local1);
                if (!file.equals(local2)) copyFile(file, local2);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static void uploadSkinAsync(final File file, final Runnable onDone) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String error = validateSkinFile(file);
                    if (error != null) {
                        setGlobalStatus("§c" + error, 7000);
                        return;
                    }

                    String token = getAuthToken();
                    if (token == null || token.isEmpty()) {
                        setGlobalStatus("§cError: No se encontró el token de GitHub para subir.", 6000);
                        return;
                    }

                    String rawName = file.getName();
                    if (rawName.toLowerCase().endsWith(".png")) {
                        rawName = rawName.substring(0, rawName.length() - 4);
                    }
                    String cleanName = rawName.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
                    if (cleanName.isEmpty()) {
                        cleanName = "skin_" + (System.currentTimeMillis() % 100000);
                    }

                    setGlobalStatus("§eSubiendo '" + cleanName + "' a GitHub...", 10000);

                    boolean ok = uploadSkinSync(file);
                    if (ok) {
                        reloadLocalSkins();
                        setGlobalStatus("§a¡Skin '" + cleanName + "' subida con éxito a GitHub!", 6000);
                    } else {
                        setGlobalStatus("§cError al subir a GitHub. Revisa la consola o conexión.", 6000);
                    }
                } catch (Exception e) {
                    setGlobalStatus("§cError: " + e.getMessage(), 6000);
                } finally {
                    if (onDone != null) {
                        try { onDone.run(); } catch (Exception ignored) {}
                    }
                }
            }
        }, "NPCSkins-Upload-Thread").start();
    }

    private static String getFileSha(String fileName) {
        try {
            String token = getAuthToken();
            URL url = new URL(API_URL + "/" + fileName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setRequestProperty("User-Agent", "NPCSkinsMod-Minecraft-1.7.10");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                int idx = sb.indexOf("\"sha\":\"");
                if (idx != -1) {
                    idx += 7;
                    int end = sb.indexOf("\"", idx);
                    if (end != -1) return sb.substring(idx, end);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void copyFile(File src, File dest) {
        try {
            if (src.getCanonicalPath().equals(dest.getCanonicalPath())) return;
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (Exception ignored) {}
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
