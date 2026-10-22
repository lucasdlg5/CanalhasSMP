package com.canalhas.cummins;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 * Garante que todas as pastas dos mods e ferramentas que desenvolvemos juntos
 * (SenhorCummins, CanalhasServerDashboard, JARs compilados, configs, memória da IA e Mural)
 * sejam copiadas e mantidas sincronizadas dentro de `world/mods_desenvolvidos/`.
 *
 * Como o Textile Backup compacta toda a pasta `world/` para o Google Drive a cada backup
 * (ex: `2026.10.17_10-16-03.zip`), todos os backups passam a incluir automaticamente
 * a pasta completa com os projetos, códigos-fonte, executáveis e configurações!
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
    private long lastSyncAt = 0L;

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
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Falha ao sincronizar pasta de backup dos mods desenvolvidos: {}", e.getMessage());
        }
    }

    /**
     * Chamado periodicamente no tick do servidor: atualiza a cada 15 minutos para garantir
     * que a memória e qualquer alteração recente estejam prontas antes do Textile Backup rodar.
     */
    public void checkPeriodicSync() {
        long now = System.currentTimeMillis();
        if (now - lastSyncAt >= 15 * 60_000L) {
            syncNow();
        }
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
