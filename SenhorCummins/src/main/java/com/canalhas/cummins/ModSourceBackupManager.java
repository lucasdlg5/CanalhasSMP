package com.canalhas.cummins;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Garante que todas as pastas dos mods e ferramentas que desenvolvemos juntos
 * (SenhorCummins, CanalhasServerDashboard, JARs compilados, configs, memória da IA e Mural)
 * sejam copiadas e mantidas sincronizadas dentro de `world/mods_desenvolvidos/`
 * E também sincronizadas automaticamente com a pasta na nuvem (Google Drive / OneDrive)
 * juntamente com os backups em .zip gerados pelo Textile Backup.
 */
public class ModSourceBackupManager {

    // Pastas de cache pesado/temporário de compilação que não precisam ir pro backup
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".gradle",
            ".tools",
            ".git",
            "obj",
            "tmp",
            "loom-cache",
            "remappedSrc"
    );

    private final Path serverDir;
    private final Path workspaceRootDir;
    private final Path targetBackupDir;
    private final ExecutorService cloudBackupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Cummins-CloudBackupSync");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean cloudSyncInProgress = new AtomicBoolean(false);
    private long lastSyncAt = 0L;
    private long lastCloudCheckAt = 0L;
    private long lastKnownBackupTimestamp = 0L;

    public ModSourceBackupManager() {
        this.serverDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        this.workspaceRootDir = this.serverDir.getParent() != null
                ? this.serverDir.getParent().toAbsolutePath().normalize()
                : this.serverDir;
        this.targetBackupDir = this.serverDir.resolve("world").resolve("mods_desenvolvidos");
    }

    /**
     * Executa a sincronização completa dos projetos e configs para `world/mods_desenvolvidos/`.
     */
    public synchronized void syncNow() {
        lastSyncAt = System.currentTimeMillis();
        try {
            Files.createDirectories(targetBackupDir);

            // 1. Projeto completo do mod SenhorCummins (código-fonte, gradle, build/libs)
            Path senhorCumminsSrc = workspaceRootDir.resolve("SenhorCummins");
            if (Files.isDirectory(senhorCumminsSrc)) {
                copyDirectoryFiltered(senhorCumminsSrc, targetBackupDir.resolve("SenhorCummins"));
            }

            // 1.1 Projeto completo do mod JacintoItemakino (código-fonte, gradle, build/libs)
            Path jacintoSrc = workspaceRootDir.resolve("JacintoItemakino");
            if (Files.isDirectory(jacintoSrc)) {
                copyDirectoryFiltered(jacintoSrc, targetBackupDir.resolve("JacintoItemakino"));
            }

            // 2. Projeto completo do CanalhasServerDashboard (código-fonte C#, .csproj, publish/)
            Path dashboardSrc = workspaceRootDir.resolve("CanalhasServerDashboard");
            if (Files.isDirectory(dashboardSrc)) {
                copyDirectoryFiltered(dashboardSrc, targetBackupDir.resolve("CanalhasServerDashboard"));
            }

            // 3. Script de inicialização do painel na raiz
            Path batFile = workspaceRootDir.resolve("INICIAR_PAINEL_CANALHAS.bat");
            if (Files.isRegularFile(batFile)) {
                Files.copy(batFile, targetBackupDir.resolve("INICIAR_PAINEL_CANALHAS.bat"), StandardCopyOption.REPLACE_EXISTING);
            }

            // 4. JARs compilados ativos na pasta mods do servidor (busca dinâmica por padrão de versão)
            Path jarTargetDir = targetBackupDir.resolve("mods_jar");
            Files.createDirectories(jarTargetDir);

            Path modsFolder = serverDir.resolve("mods");
            if (Files.isDirectory(modsFolder)) {
                try (var stream = Files.newDirectoryStream(modsFolder, "*.jar")) {
                    for (Path jar : stream) {
                        String name = jar.getFileName().toString();
                        if (name.startsWith("senhor-cummins-") || name.startsWith("jacinto-itemakino-")) {
                            Files.copy(jar, jarTargetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                            Files.copy(jar, targetBackupDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

            // 5. Configurações, memória de longo prazo (memoria.json), Mural e ícone do servidor
            Path configBackupDir = targetBackupDir.resolve("config_e_mural");
            Files.createDirectories(configBackupDir);

            Path cumminsConfigDir = serverDir.resolve("config").resolve("senhor-cummins");
            if (Files.isDirectory(cumminsConfigDir)) {
                copyDirectoryFiltered(cumminsConfigDir, configBackupDir.resolve("senhor-cummins"));
            }

            Path jacintoConfigDir = serverDir.resolve("config").resolve("jacinto");
            if (Files.isDirectory(jacintoConfigDir)) {
                copyDirectoryFiltered(jacintoConfigDir, configBackupDir.resolve("jacinto"));
            }

            Path muralFile = serverDir.resolve("MURAL_NOVIDADES.json");
            if (Files.isRegularFile(muralFile)) {
                Files.copy(muralFile, configBackupDir.resolve("MURAL_NOVIDADES.json"), StandardCopyOption.REPLACE_EXISTING);
            }

            Path serverIcon = serverDir.resolve("server-icon.png");
            if (Files.isRegularFile(serverIcon)) {
                Files.copy(serverIcon, configBackupDir.resolve("server-icon.png"), StandardCopyOption.REPLACE_EXISTING);
            }

            CumminsMod.LOG.info("[Cummins] Backup dos projetos e mods desenvolvidos sincronizado em: {}", targetBackupDir);
            syncCloudBackupsAsync();
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Falha ao sincronizar pasta de backup dos mods desenvolvidos: {}", e.getMessage());
        }
    }

    /**
     * Chamado periodicamente no tick do servidor: atualiza a cada 15 minutos para garantir
     * que a memória e qualquer alteração recente estejam prontas antes do Textile Backup rodar,
     * e verifica a cada 30 segundos se novos backups em .zip foram gerados para enviá-los à nuvem.
     */
    public void checkPeriodicSync() {
        long now = System.currentTimeMillis();
        if (now - lastSyncAt >= 15 * 60_000L) {
            syncNow();
            return;
        }

        if (now - lastCloudCheckAt >= 30_000L) {
            lastCloudCheckAt = now;
            checkForNewLocalBackupsAndSyncAsync();
        }
    }

    public void syncCloudBackupsAsync() {
        if (!cloudSyncInProgress.compareAndSet(false, true)) {
            return;
        }
        cloudBackupExecutor.submit(() -> {
            try {
                Path cloudDir = findCloudBackupDir();
                if (cloudDir == null) {
                    return;
                }

                Files.createDirectories(cloudDir);

                // 1. Sincroniza os arquivos .zip de backup gerados pelo Textile Backup
                Path localBackupsDir = serverDir.resolve("backups");
                if (Files.isDirectory(localBackupsDir)) {
                    Set<String> activeLocalZips = new HashSet<>();
                    long newestTs = lastKnownBackupTimestamp;

                    try (var stream = Files.newDirectoryStream(localBackupsDir, "*.zip")) {
                        for (Path localZip : stream) {
                            String zipName = localZip.getFileName().toString();
                            activeLocalZips.add(zipName);
                            long lastMod = Files.getLastModifiedTime(localZip).toMillis();
                            if (lastMod > newestTs) {
                                newestTs = lastMod;
                            }

                            Path targetZip = cloudDir.resolve(zipName);
                            boolean needsCopy = !Files.exists(targetZip) || Files.size(targetZip) != Files.size(localZip);
                            if (needsCopy) {
                                Files.copy(localZip, targetZip, StandardCopyOption.REPLACE_EXISTING);
                                CumminsMod.LOG.info("[Cummins] Novo backup enviado para a nuvem: {}", zipName);
                            }
                        }
                    }
                    this.lastKnownBackupTimestamp = newestTs;

                    // 2. Remove da nuvem os backups antigos expurgados localmente
                    if (!activeLocalZips.isEmpty()) {
                        try (var stream = Files.newDirectoryStream(cloudDir, "*.zip")) {
                            for (Path cloudZip : stream) {
                                String cloudZipName = cloudZip.getFileName().toString();
                                if (!activeLocalZips.contains(cloudZipName)) {
                                    try {
                                        Files.deleteIfExists(cloudZip);
                                        CumminsMod.LOG.info("[Cummins] Backup antigo removido da nuvem: {}", cloudZipName);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                }

                // 3. Copia a pasta mods_desenvolvidos para a nuvem
                if (Files.isDirectory(targetBackupDir)) {
                    Path cloudModsDir = cloudDir.resolve("mods_desenvolvidos");
                    copyDirectoryFiltered(targetBackupDir, cloudModsDir);
                }

                CumminsMod.LOG.info("[Cummins] Backups na nuvem sincronizados com sucesso em: {}", cloudDir);
            } catch (Exception e) {
                CumminsMod.LOG.warn("[Cummins] Aviso ao sincronizar backups com a nuvem: {}", e.getMessage());
            } finally {
                cloudSyncInProgress.set(false);
            }
        });
    }

    private void checkForNewLocalBackupsAndSyncAsync() {
        Path localBackupsDir = serverDir.resolve("backups");
        if (!Files.isDirectory(localBackupsDir)) return;

        try (var stream = Files.newDirectoryStream(localBackupsDir, "*.zip")) {
            for (Path zip : stream) {
                long lastMod = Files.getLastModifiedTime(zip).toMillis();
                if (lastMod > lastKnownBackupTimestamp) {
                    syncCloudBackupsAsync();
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    public static Path findCloudBackupDir() {
        Path[] directCandidates = new Path[]{
                Paths.get("G:/Meu Drive/Servidor Minecreft CanalhasSMP"),
                Paths.get("G:/Meu Drive/Servidor Minecraft CanalhasSMP"),
                Paths.get("G:/My Drive/Servidor Minecreft CanalhasSMP"),
                Paths.get("G:/My Drive/Servidor Minecraft CanalhasSMP"),
                Paths.get(System.getProperty("user.home"), "OneDrive", "Servidor Minecraft CanalhasSMP"),
                Paths.get(System.getProperty("user.home"), "OneDrive", "Servidor Minecreft CanalhasSMP")
        };
        for (Path c : directCandidates) {
            if (Files.isDirectory(c)) {
                return c;
            }
        }

        Path[] driveRoots = new Path[]{Paths.get("G:/Meu Drive"), Paths.get("G:/My Drive")};
        for (Path root : driveRoots) {
            if (Files.isDirectory(root)) {
                try (var stream = Files.newDirectoryStream(root)) {
                    for (Path sub : stream) {
                        if (Files.isDirectory(sub)) {
                            String name = sub.getFileName().toString().toLowerCase();
                            if (name.contains("canalhas") || (name.contains("servidor") && name.contains("minecr"))) {
                                return sub;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    private static void copyDirectoryFiltered(Path sourceRoot, Path targetRoot) throws IOException {
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = sourceRoot.relativize(dir);
                String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";

                if (!rel.toString().isEmpty()) {
                    if (EXCLUDED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // Dentro de build/ ou bin/, guarda apenas build/libs (os .jar finais) e publish/
                    String relUnix = rel.toString().replace('\\', '/');
                    if (relUnix.equals("build/classes") || relUnix.equals("build/generated")
                            || relUnix.equals("build/intermediates") || relUnix.equals("build/ overheat")
                            || relUnix.equals("build/resources") || relUnix.equals("build/tmp")
                            || relUnix.equals("build/devlibs") || relUnix.equals("bin")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                Path targetDir = targetRoot.resolve(rel);
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = sourceRoot.relativize(file);
                Path targetFile = targetRoot.resolve(rel);
                Files.createDirectories(targetFile.getParent());
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
