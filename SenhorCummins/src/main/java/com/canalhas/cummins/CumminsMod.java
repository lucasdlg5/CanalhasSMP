package com.canalhas.cummins;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class CumminsMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("SenhorCummins");

    private Path configFile;
    private CumminsConfig config;
    private ContextStore context;
    private LlmClient llm;
    private CumminsBrain brain;
    private DiscordPoller discord;
    private CumminsBody body;
    private VitoriaBody vitoria;
    private PipiraBody pipira;
    private JuninhoBody juninho;
    private JavidizineoBody javidizineo;
    private JinqiuzineoBody jinqiuzineo;
    private ServerTelemetry telemetry;
    private MuralManager mural;
    private ModSourceBackupManager modBackup;
    private LagReportManager lagReports;
    private CumminsTeleportManager teleport;
    private PlaytimeManager playtime;
    private CumminsPocheteManager pochete;
    private CumminsNightHunter nightHunter;
    private CumminsTourGuide tourGuide;
    private CumminsRHManager rh;
    private CumminsContractsManager contracts;
    private NetworkHealthMonitor networkMonitor;
    private DailyHighlightsTracker dailyHighlights;
    private CumminsMaloteManager malote;
    private CumminsAlmoxarifadoManager almoxarifado;
    private CumminsMutiraoManager mutirao;
    private CumminsCafeManager cafe;
    private CumminsTvManager tvManager;
    private LlmTokenTracker tokenTracker;
    private net.minecraft.server.MinecraftServer server;
    private String lastShiftReportDate = "";

    public CumminsConfig config() { return config; }
    public ContextStore context() { return context; }
    public LlmClient llm() { return llm; }
    public LlmTokenTracker tokenTracker() { return tokenTracker; }
    public CumminsBrain brain() { return brain; }
    public DiscordPoller discord() { return discord; }
    public CumminsBody body() { return body; }
    public VitoriaBody vitoria() { return vitoria; }
    public PipiraBody pipira() { return pipira; }
    public JuninhoBody juninho() { return juninho; }
    public JavidizineoBody javidizineo() { return javidizineo; }
    public JinqiuzineoBody jinqiuzineo() { return jinqiuzineo; }
    public ServerTelemetry telemetry() { return telemetry; }
    public MuralManager mural() { return mural; }
    public ModSourceBackupManager modBackup() { return modBackup; }
    public LagReportManager lagReports() { return lagReports; }
    public CumminsTeleportManager teleport() { return teleport; }
    public PlaytimeManager playtime() { return playtime; }
    public CumminsPocheteManager pochete() { return pochete; }
    public CumminsNightHunter nightHunter() { return nightHunter; }
    public CumminsTourGuide tourGuide() { return tourGuide; }
    public CumminsRHManager rh() { return rh; }
    public CumminsContractsManager contracts() { return contracts; }
    public NetworkHealthMonitor networkMonitor() { return networkMonitor; }
    public DailyHighlightsTracker dailyHighlights() { return dailyHighlights; }
    public CumminsMaloteManager malote() { return malote; }
    public CumminsAlmoxarifadoManager almoxarifado() { return almoxarifado; }
    public CumminsMutiraoManager mutirao() { return mutirao; }
    public CumminsCafeManager cafe() { return cafe; }
    public CumminsTvManager tvManager() { return tvManager; }
    public net.minecraft.server.MinecraftServer server() { return server; }

    @Override
    public void onInitialize() {
        preloadAllModClasses();
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("senhor-cummins");
        configFile = dir.resolve("config.json");
        config = CumminsConfig.load(configFile);
        context = new ContextStore(dir.resolve("memoria.json"));
        context.load();
        OfflineTrivia.load(dir.resolve("offline_trivia.json"));
        ModKnowledgeBase.load(dir.resolve("mod_knowledge.json"));
        tokenTracker = new LlmTokenTracker(this, dir);
        llm = new LlmClient();
        llm.setTokenTracker(tokenTracker);
        brain = new CumminsBrain(this);
        discord = new DiscordPoller(this);
        body = new CumminsBody(this);
        vitoria = new VitoriaBody(this);
        pipira = new PipiraBody(this);
        juninho = new JuninhoBody(this);
        javidizineo = new JavidizineoBody(this);
        jinqiuzineo = new JinqiuzineoBody(this);
        telemetry = new ServerTelemetry(this);
        mural = new MuralManager(dir);
        modBackup = new ModSourceBackupManager();
        lagReports = new LagReportManager(dir);
        teleport = new CumminsTeleportManager(this);
        playtime = new PlaytimeManager(this, dir);
        pochete = new CumminsPocheteManager(this, dir);
        nightHunter = new CumminsNightHunter(this);
        tourGuide = new CumminsTourGuide(this, dir);
        rh = new CumminsRHManager(this, dir);
        contracts = new CumminsContractsManager(this, dir);
        networkMonitor = new NetworkHealthMonitor(this, dir);
        dailyHighlights = new DailyHighlightsTracker(this);
        malote = new CumminsMaloteManager(this, dir);
        almoxarifado = new CumminsAlmoxarifadoManager(this, dir);
        mutirao = new CumminsMutiraoManager(this);
        cafe = new CumminsCafeManager(this);
        tvManager = new CumminsTvManager(this, dir);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            telemetry.start();
            brain.start(server);
            discord.start();
            body.start(server);
            vitoria.start(server);
            pipira.start(server);
            juninho.start(server);
            javidizineo.start(server);
            jinqiuzineo.start(server);
            modBackup.syncNow();
            playtime.onServerStarted(server);
            if (config.networkMonitor != null && config.networkMonitor.enabled) {
                networkMonitor.start();
            }
            LOG.info("[Cummins] {} chegou no servidor. Modelo: {}", config.botName, config.api.model);
            if (config.autoSparkProfiler) {
                try {
                    LOG.info("[Cummins] Iniciando o spark profiler automaticamente...");
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), "spark profiler start");
                } catch (Exception e) {
                    LOG.warn("[Cummins] Falha ao iniciar spark profiler automaticamente: {}", e.getMessage());
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (config.autoSparkProfiler) {
                try {
                    LOG.info("[Cummins] Parando o spark profiler e gerando relatório...");
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), "spark profiler stop");
                } catch (Exception e) {
                    LOG.warn("[Cummins] Falha ao parar spark profiler: {}", e.getMessage());
                }
            }
            if (networkMonitor != null) networkMonitor.stop();
            llm.stop();
            brain.stop();
            discord.stop();
            body.stop();
            vitoria.stop();
            pipira.stop();
            juninho.stop();
            javidizineo.stop();
            jinqiuzineo.stop();
            telemetry.stop();
            if (playtime != null) playtime.doPeriodicUpdate(server);
            if (pochete != null) pochete.save();
            if (malote != null) malote.save();
            if (almoxarifado != null) almoxarifado.save();
            if (tourGuide != null) tourGuide.save();
            if (rh != null) rh.save();
            if (contracts != null) contracts.save();
            context.save();
            if (modBackup != null) modBackup.syncNow();
            this.server = null;
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (dailyHighlights != null) dailyHighlights.onChatMessage(sender.getGameProfile().getName(), message.getContent().getString());
            brain.onChat(sender, message.getContent().getString());
        });
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) ->
            brain.onCommandMessage(source.getName(), message.getContent().getString()));
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (dailyHighlights != null) dailyHighlights.onGameMessage(message);
            if (!overlay) brain.onGameMessage(message);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (dailyHighlights != null) dailyHighlights.recordDeath(player.getGameProfile().getName());
                brain.onPlayerDeath(player, source);
                if (rh != null) rh.onPlayerDeath(player, source);
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            net.minecraft.entity.Entity attacker = source.getAttacker();
            if (attacker != null && attacker.getCommandTags().contains(VitoriaBody.VITORIA_TAG)) {
                // Vitória NUNCA causa dano em jogadores ou criaturas aliadas, apenas em monstros!
                if (!(entity instanceof net.minecraft.entity.mob.Monster) || entity instanceof net.minecraft.entity.player.PlayerEntity) {
                    if (attacker instanceof WolfEntity dog) {
                        dog.setTarget(null);
                        dog.setAttacking(false);
                        dog.setAngryAt(null);
                        dog.setAngerTime(0);
                        dog.setAttacker(null);
                    }
                    return false;
                }
            }
            if (entity.getCommandTags().contains(PipiraBody.PIPIRA_TAG)) {
                // Pipira é 100% imortal e pacífico
                return false;
            }
            if (entity.getCommandTags().contains(JuninhoBody.JUNINHO_TAG)) {
                // Juninho é 100% imortal e guardião
                return false;
            }
            if (entity.getCommandTags().contains(JavidizineoBody.JAVIDIZINEO_TAG)) {
                // Javidizineo é 100% imortal e mini-leão guardião
                return false;
            }
            if (entity.getCommandTags().contains(JinqiuzineoBody.JINQIUZINEO_TAG)) {
                // Jinqiuzineo é 100% imortal e mini-tigre guardião
                return false;
            }
            return true;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (dailyHighlights != null) dailyHighlights.recordPlayerActive(handler.player.getGameProfile().getName());
            brain.onJoin(handler.player);
            mural.onPlayerJoin(handler.player);
            playtime.onPlayerJoin(handler.player);
            if (malote != null) malote.onPlayerJoin(handler.player);
            if (networkMonitor != null) networkMonitor.onPlayerJoin(handler.player);
            if (pipira != null && handler.player.getGameProfile().getName().equalsIgnoreCase(PipiraBody.OWNER_NAME)) {
                pipira.summonOrTeleportTo(handler.player);
            }
            if (juninho != null && handler.player.getGameProfile().getName().equalsIgnoreCase(JuninhoBody.OWNER_NAME)) {
                juninho.summonOrTeleportTo(handler.player);
            }
            if (javidizineo != null && handler.player.getGameProfile().getName().equalsIgnoreCase(JavidizineoBody.OWNER_NAME)) {
                javidizineo.summonOrTeleportTo(handler.player);
            }
            if (jinqiuzineo != null && handler.player.getGameProfile().getName().equalsIgnoreCase(JinqiuzineoBody.OWNER_NAME)) {
                jinqiuzineo.summonOrTeleportTo(handler.player);
            }
            if (tvManager != null) tvManager.onPlayerJoin(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            brain.onLeave(handler.player);
            playtime.onPlayerLeave(handler.player);
            if (networkMonitor != null) networkMonitor.onPlayerDisconnect(handler.player);
            if (tvManager != null) tvManager.onPlayerLeave(handler.player);
            server.execute(() -> {
                if (pipira != null) pipira.onPlayerLeave(handler.player);
                if (juninho != null) juninho.onPlayerLeave(handler.player);
                if (javidizineo != null) javidizineo.onPlayerLeave(handler.player);
                if (jinqiuzineo != null) jinqiuzineo.onPlayerLeave(handler.player);
                if (mutirao != null) mutirao.onPlayerLeave(handler.player);
                checkEndOfTurnOnDisconnect(server);
            });
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && hand == Hand.MAIN_HAND) {
                if (entity instanceof CatEntity cat && cat.getCommandTags().contains(CumminsBody.BODY_TAG)) {
                    if (player instanceof ServerPlayerEntity sp) {
                        if (sp.isSneaking() && pochete != null) {
                            pochete.openPochete(sp);
                        } else {
                            body.onPet(cat, sp);
                        }
                    }
                    return ActionResult.SUCCESS;
                }
                if (entity instanceof WolfEntity dog && dog.getCommandTags().contains(VitoriaBody.VITORIA_TAG)) {
                    if (player instanceof ServerPlayerEntity sp) {
                        vitoria.onPet(dog, sp);
                    }
                    return ActionResult.SUCCESS;
                }
                if (entity instanceof net.minecraft.entity.passive.SalmonEntity fish && fish.getCommandTags().contains(PipiraBody.PIPIRA_TAG)) {
                    if (player instanceof ServerPlayerEntity sp) {
                        pipira.onPet(fish, sp);
                    }
                    return ActionResult.SUCCESS;
                }
                if (entity instanceof net.minecraft.entity.passive.BatEntity bat && bat.getCommandTags().contains(JuninhoBody.JUNINHO_TAG)) {
                    if (player instanceof ServerPlayerEntity sp) {
                        juninho.onPet(bat, sp);
                    }
                    return ActionResult.SUCCESS;
                }
                if (entity instanceof CatEntity cat && cat.getCommandTags().contains(JavidizineoBody.JAVIDIZINEO_TAG)) {
                    if (player instanceof ServerPlayerEntity sp) {
                        javidizineo.onPet(cat, sp);
                    }
                    return ActionResult.SUCCESS;
                }
                if (entity instanceof CatEntity cat && cat.getCommandTags().contains(JinqiuzineoBody.JINQIUZINEO_TAG)) {
                    if (player instanceof ServerPlayerEntity sp) {
                        jinqiuzineo.onPet(cat, sp);
                    }
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> telemetry.onTickStart());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            telemetry.onTickEnd(server);
            brain.onTick();
            body.onTick();
            vitoria.onTick();
            if (pipira != null) pipira.onTick();
            if (juninho != null) juninho.onTick();
            if (javidizineo != null) javidizineo.onTick();
            if (jinqiuzineo != null) jinqiuzineo.onTick();
            mural.onTick(server);
            playtime.onTick(server);
            if (mutirao != null) mutirao.tick(server);
            if (nightHunter != null) nightHunter.onTick(body.getCat());
            if (tourGuide != null) tourGuide.onTick(body.getCat(), server);
            if (tvManager != null) tvManager.onTick(server);
            if (modBackup != null) modBackup.checkPeriodicSync();
            checkShiftReport(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CumminsCommands.register(dispatcher, this));
    }

    private void checkShiftReport(net.minecraft.server.MinecraftServer server) {
        if (server == null || server.getTicks() % 400 != 0) return;
        java.time.LocalTime now = java.time.LocalTime.now();
        String today = java.time.LocalDate.now().toString();
        if (now.getHour() == 23 && now.getMinute() >= 30 && !today.equals(lastShiftReportDate)) {
            lastShiftReportDate = today;
            if (discord != null) discord.postShiftReport(server);
        }
    }

    private void checkEndOfTurnOnDisconnect(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        if (server.getPlayerManager().getCurrentPlayerCount() <= 1) {
            java.time.LocalTime now = java.time.LocalTime.now();
            String today = java.time.LocalDate.now().toString();
            if ((now.getHour() >= 22 || now.getHour() <= 4) && !today.equals(lastShiftReportDate)) {
                lastShiftReportDate = today;
                if (discord != null) discord.postShiftReport(server);
            }
        }
    }

    public void reload() {
        config = CumminsConfig.load(configFile);
        if (mural != null) mural.loadOrCreateDefault();
        if (playtime != null) playtime.load();
        if (tourGuide != null) tourGuide.load();
        if (rh != null) rh.load();
        if (contracts != null) contracts.load();
        if (networkMonitor != null) {
            networkMonitor.stop();
            if (config.networkMonitor != null && config.networkMonitor.enabled) {
                networkMonitor.start();
            }
        }
        if (modBackup != null) modBackup.syncNow();
        discord.start();
    }

    public void saveConfig() {
        config.save(configFile);
    }

    /**
     * Força o carregamento antecipado (Eager Preload) de todas as classes do mod na memória JVM.
     * Isso impede qualquer ZipException / ZipFile invalid LOC header se o arquivo .jar for atualizado em disco,
     * pois todas as classes e classes internas já estarão residentes na memória Metaspace da JVM.
     */
    private static void preloadAllModClasses() {
        ClassLoader loader = CumminsMod.class.getClassLoader();
        List<String> classes = List.of(
                "com.canalhas.cummins.CompassBiomeMatch",
                "com.canalhas.cummins.ContextStore",
                "com.canalhas.cummins.CumminsBody",
                "com.canalhas.cummins.CumminsBody$GiftResult",
                "com.canalhas.cummins.CumminsBrain",
                "com.canalhas.cummins.CumminsCommands",
                "com.canalhas.cummins.CumminsConfig",
                "com.canalhas.cummins.CumminsConfig$Chances",
                "com.canalhas.cummins.CumminsContractsManager",
                "com.canalhas.cummins.CumminsContractsManager$ContractStore",
                "com.canalhas.cummins.CumminsContractsManager$DailyContract",
                "com.canalhas.cummins.CumminsMod",
                "com.canalhas.cummins.CumminsNightHunter",
                "com.canalhas.cummins.CumminsPocheteManager",
                "com.canalhas.cummins.CumminsRHManager",
                "com.canalhas.cummins.CumminsRHManager$RHComplaint",
                "com.canalhas.cummins.CumminsRHManager$RHStore",
                "com.canalhas.cummins.CumminsScanner",
                "com.canalhas.cummins.CumminsScanner$DistanceCategory",
                "com.canalhas.cummins.CumminsScanner$ScanResult",
                "com.canalhas.cummins.CumminsScanner$ContainerItemMatch",
                "com.canalhas.cummins.CumminsTeleportManager",
                "com.canalhas.cummins.CumminsTeleportManager$ParsedTeleportRequest",
                "com.canalhas.cummins.CumminsTourGuide",
                "com.canalhas.cummins.CumminsTourGuide$TourLocation",
                "com.canalhas.cummins.CumminsTourGuide$TourStore",
                "com.canalhas.cummins.CumminsTvManager",
                "com.canalhas.cummins.CumminsTvManager$TvCameraMode",
                "com.canalhas.cummins.CumminsTvManager$TvConfig",
                "com.canalhas.cummins.CumminsTvManager$SpectatorSession",
                "com.canalhas.cummins.DiscordPoller",
                "com.canalhas.cummins.LagReport",
                "com.canalhas.cummins.LagReportManager",
                "com.canalhas.cummins.LagReportManager$LagReport",
                "com.canalhas.cummins.LlmClient",
                "com.canalhas.cummins.ModKnowledgeBase",
                "com.canalhas.cummins.ModKnowledgeBase$KnowledgeData",
                "com.canalhas.cummins.ModKnowledgeBase$KnowledgeEntry",
                "com.canalhas.cummins.ModSourceBackupManager",
                "com.canalhas.cummins.MuralManager",
                "com.canalhas.cummins.MuralManager$MuralData",
                "com.canalhas.cummins.MuralManager$SeenStore",
                "com.canalhas.cummins.NetworkHealthMonitor",
                "com.canalhas.cummins.NetworkHealthMonitor$NetworkStats5Min",
                "com.canalhas.cummins.NetworkHealthMonitor$PingSample",
                "com.canalhas.cummins.OfflineTrivia",
                "com.canalhas.cummins.OfflineTrivia$TriviaData",
                "com.canalhas.cummins.PlayerNewsFetcher",
                "com.canalhas.cummins.PlayerRelationshipManager",
                "com.canalhas.cummins.PlayerScanRecord",
                "com.canalhas.cummins.PlaytimeManager",
                "com.canalhas.cummins.PlaytimeManager$PlaytimeData",
                "com.canalhas.cummins.PlaytimeManager$SeenData",
                "com.canalhas.cummins.ServerTelemetry",
                "com.canalhas.cummins.VitoriaBody",
                "com.canalhas.cummins.PipiraBody",
                "com.canalhas.cummins.JuninhoBody",
                "com.canalhas.cummins.JavidizineoBody",
                "com.canalhas.cummins.JavidizineoDialogueLibrary",
                "com.canalhas.cummins.JinqiuzineoBody",
                "com.canalhas.cummins.JinqiuzineoDialogueLibrary"
        );

        int loaded = 0;
        for (String clsName : classes) {
            try {
                Class.forName(clsName, true, loader);
                loaded++;
            } catch (Throwable ignored) {}
        }
        LOG.info("[Cummins-Preload] {} classes do mod pré-carregadas na memória JVM com sucesso!", loaded);
    }
}
