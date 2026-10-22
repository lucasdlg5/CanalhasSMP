package com.canalhas.jacinto;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Integração inteligente de proteção de blocos e claims para o modpack Prominence II:
 * - Suporta Open Parties and Claims (OPAC - mod de claims padrão do Prominence II)
 * - Suporta FTB Chunks (caso ativo)
 * - Delimita o teto subterrâneo para mineração de pedras/rochas ornamentais
 *   garantindo que o Jacinto nunca quebre construções de jogadores (ex: castelo de Andesito).
 */
public final class JacintoClaimHelper {
    private static final Logger LOG = LoggerFactory.getLogger("JacintoClaimHelper");

    // Camada máxima de mineração subterrânea segura para rochas (Andesito, Granito, Pedra, etc.)
    // Castelos e edificações de jogadores são construídos na superfície (Y >= 60).
    public static final int SAFE_SUBTERRANEAN_MAX_Y = 40;

    // Cache de reflexão para Open Parties and Claims (OPAC)
    private static boolean opacChecked = false;
    private static boolean opacAvailable = false;
    private static Method opacGetApiMethod = null;
    private static Method opacGetClaimsManagerMethod = null;
    private static Method opacGetClaimMethod = null;

    // Cache de reflexão para FTB Chunks
    private static boolean ftbChecked = false;
    private static boolean ftbAvailable = false;
    private static Method ftbGetManagerMethod = null;
    private static Method ftbGetChunkMethod = null;

    private JacintoClaimHelper() {}

    /**
     * Verifica se a coordenada indicada está dentro de um Chunk reivindicado (Claim)
     * por qualquer jogador ou party. Se estiver em um claim, o Jacinto JAMAIS quebra blocos ali.
     */
    public static boolean isPosClaimed(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return false;

        // 1. Checagem Open Parties and Claims (OPAC - Prominence II)
        if (checkOpacClaim(world, pos)) {
            return true;
        }

        // 2. Checagem FTB Chunks (se instalado)
        if (checkFtbClaim(world, pos)) {
            return true;
        }

        return false;
    }

    /**
     * Verifica se o item/termo é considerado pedra, rocha ou material de construção
     * que deve ser minerado estritamente no subterrâneo para não danificar castelos e bases.
     */
    public static boolean isConstructionOrStoneType(String query, String displayName) {
        String q = (query != null ? query : "").toLowerCase();
        String d = (displayName != null ? displayName : "").toLowerCase();

        return q.contains("andesit") || d.contains("andesit") ||
               q.contains("diorit") || d.contains("diorit") ||
               q.contains("granit") || d.contains("granit") ||
               q.contains("pedra") || d.contains("pedra") ||
               q.contains("pedregulho") || d.contains("pedregulho") ||
               q.contains("cobblestone") || d.contains("cobblestone") ||
               q.contains("stone") || d.contains("stone") ||
               q.contains("tufo") || d.contains("tufo") ||
               q.contains("tuff") || d.contains("tuff") ||
               q.contains("calcita") || d.contains("calcita") ||
               q.contains("calcite") || d.contains("calcite") ||
               q.contains("deepslate") || d.contains("deepslate") ||
               q.contains("ardosia") || d.contains("ardosia") ||
               q.contains("ardósia") || d.contains("ardósia") ||
               q.contains("basalto") || d.contains("basalto") ||
               q.contains("basalt") || d.contains("basalt") ||
               q.contains("blackstone") || d.contains("blackstone") ||
               q.contains("arenito") || d.contains("arenito") ||
               q.contains("sandstone") || d.contains("sandstone");
    }

    /* =========================================================================
     *  INTEGRAÇÃO OPEN PARTIES AND CLAIMS (OPAC)
     * ========================================================================= */

    private static boolean checkOpacClaim(ServerWorld world, BlockPos pos) {
        initOpac();
        if (!opacAvailable) return false;

        try {
            MinecraftServer server = world.getServer();
            if (server == null) return false;

            Object api = opacGetApiMethod.invoke(null, server);
            if (api != null) {
                Object claimsManager = opacGetClaimsManagerMethod.invoke(api);
                if (claimsManager != null) {
                    Identifier dimId = world.getRegistryKey().getValue();
                    int chunkX = pos.getX() >> 4;
                    int chunkZ = pos.getZ() >> 4;
                    Object claim = opacGetClaimMethod.invoke(claimsManager, dimId, chunkX, chunkZ);
                    return claim != null; // Se claim != null, o chunk pertence a um jogador/party
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void initOpac() {
        if (opacChecked) return;
        opacChecked = true;

        if (!FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
            return;
        }

        try {
            Class<?> apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            opacGetApiMethod = apiClass.getMethod("get", MinecraftServer.class);
            opacGetClaimsManagerMethod = apiClass.getMethod("getServerClaimsManager");

            Class<?> serverClaimsManagerClass = Class.forName("xaero.pac.common.server.claims.ServerClaimsManager");
            opacGetClaimMethod = serverClaimsManagerClass.getMethod("get", Identifier.class, int.class, int.class);

            opacAvailable = true;
            LOG.info("[Jacinto] Integração com Open Parties and Claims (OPAC) vinculada com sucesso! Jacinto respeitará todos os claims da base.");
        } catch (Throwable t) {
            LOG.warn("[Jacinto] Não foi possível vincular à API do Open Parties and Claims: " + t.getMessage());
        }
    }

    /* =========================================================================
     *  INTEGRAÇÃO FTB CHUNKS (FALLBACK)
     * ========================================================================= */

    private static boolean checkFtbClaim(ServerWorld world, BlockPos pos) {
        initFtb();
        if (!ftbAvailable) return false;

        try {
            Object manager = ftbGetManagerMethod.invoke(null);
            if (manager != null) {
                // ChunkDimPos constructor
                Class<?> chunkDimPosClass = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
                Object chunkPos = chunkDimPosClass.getConstructor(ServerWorld.class, BlockPos.class).newInstance(world, pos);
                Object chunk = ftbGetChunkMethod.invoke(manager, chunkPos);
                return chunk != null;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void initFtb() {
        if (ftbChecked) return;
        ftbChecked = true;

        if (!FabricLoader.getInstance().isModLoaded("ftbchunks")) {
            return;
        }

        try {
            Class<?> ftbApiClass = Class.forName("dev.ftb.mods.ftbchunks.data.FTBChunksAPI");
            ftbGetManagerMethod = ftbApiClass.getMethod("getManager");

            Class<?> managerClass = Class.forName("dev.ftb.mods.ftbchunks.data.ClaimedChunkManager");
            Class<?> chunkDimPosClass = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
            ftbGetChunkMethod = managerClass.getMethod("getChunk", chunkDimPosClass);

            ftbAvailable = true;
            LOG.info("[Jacinto] Integração com FTB Chunks vinculada com sucesso! Jacinto respeitará os claims FTB.");
        } catch (Throwable t) {
            // FTB Chunks não presente ou com API diferente
        }
    }
}
