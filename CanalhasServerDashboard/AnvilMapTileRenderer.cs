using System;
using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.IO.Compression;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace CanalhasServerDashboard
{
    /// <summary>
    /// Renderizador nativo de regiões Anvil (.mca) do Minecraft 1.20.1 estilo Xaero's World Map / Minimap.
    /// Lê os blocos de superfície, biomas e profundidade de água, aplica sombreamento topográfico 3D (hillshading)
    /// e salva em cache PNG (512x512 por região) para carregamento instantâneo.
    /// </summary>
    public sealed class AnvilMapTileRenderer : IDisposable
    {
        private readonly string _serverDir;
        private readonly Action _onTileReady;
        private readonly ConcurrentDictionary<(int dim, int rx, int rz), Bitmap> _memoryCache = new();
        private readonly ConcurrentDictionary<(int dim, int rx, int rz), byte> _inFlight = new();
        private readonly ConcurrentDictionary<(int dim, int rx, int rz), byte> _failedOrEmpty = new();
        private readonly PriorityQueue<(int dim, int rx, int rz, bool forceReRender), double> _workQueue = new();
        private readonly object _queueLock = new();
        private readonly SemaphoreSlim _workSignal = new(0);
        private readonly CancellationTokenSource _cts = new();
        private int _pendingCount = 0;

        public int PendingCount => Volatile.Read(ref _pendingCount);
        public int LoadedCount => _memoryCache.Count;

        public AnvilMapTileRenderer(string serverDir, Action onTileReady)
        {
            _serverDir = serverDir;
            _onTileReady = onTileReady;

            int workers = Math.Clamp(Environment.ProcessorCount / 2, 2, 4);
            for (int i = 0; i < workers; i++)
            {
                Task.Run(() => WorkerLoop(_cts.Token));
            }
        }

        public bool TryGetTile(int dim, int rx, int rz, out Bitmap? bitmap)
        {
            return _memoryCache.TryGetValue((dim, rx, rz), out bitmap);
        }

        public void RequestTile(int dim, int rx, int rz, double priorityDistanceSq, bool forceReRender = false)
        {
            var key = (dim, rx, rz);
            if (!forceReRender && (_memoryCache.ContainsKey(key) || _failedOrEmpty.ContainsKey(key)))
                return;

            if (!_inFlight.TryAdd(key, 1))
                return;

            lock (_queueLock)
            {
                _workQueue.Enqueue((dim, rx, rz, forceReRender), priorityDistanceSq);
                Interlocked.Increment(ref _pendingCount);
            }
            _workSignal.Release();
        }

        public void InvalidateRegion(int dim, int rx, int rz, double priorityDistanceSq = 0)
        {
            var key = (dim, rx, rz);
            _failedOrEmpty.TryRemove(key, out _);
            RequestTile(dim, rx, rz, priorityDistanceSq, forceReRender: true);
        }

        public void ClearCacheAndReload()
        {
            lock (_queueLock)
            {
                _workQueue.Clear();
                Interlocked.Exchange(ref _pendingCount, 0);
            }
            _inFlight.Clear();
            _failedOrEmpty.Clear();
        }

        private async Task WorkerLoop(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    await _workSignal.WaitAsync(ct).ConfigureAwait(false);
                }
                catch (OperationCanceledException)
                {
                    break;
                }

                (int dim, int rx, int rz, bool forceReRender) item;
                lock (_queueLock)
                {
                    if (!_workQueue.TryDequeue(out item, out _))
                        continue;
                }

                try
                {
                    ProcessTile(item.dim, item.rx, item.rz, item.forceReRender);
                }
                catch
                {
                    _failedOrEmpty.TryAdd((item.dim, item.rx, item.rz), 1);
                }
                finally
                {
                    _inFlight.TryRemove((item.dim, item.rx, item.rz), out _);
                    Interlocked.Decrement(ref _pendingCount);
                }
            }
        }

        private string GetMcaPath(int dim, int rx, int rz)
        {
            string worldDir = Path.Combine(_serverDir, "world");
            string regDir = dim switch
            {
                1 => Path.Combine(worldDir, "DIM-1", "region"),
                2 => Path.Combine(worldDir, "DIM1", "region"),
                _ => Path.Combine(worldDir, "region")
            };
            return Path.Combine(regDir, $"r.{rx}.{rz}.mca");
        }

        private string GetCachePngPath(int dim, int rx, int rz)
        {
            string dimFolder = dim switch
            {
                1 => "nether",
                2 => "end",
                _ => "overworld"
            };
            string dir = Path.Combine(_serverDir, "world", "dashboard_tiles", dimFolder);
            Directory.CreateDirectory(dir);
            return Path.Combine(dir, $"r.{rx}.{rz}.png");
        }

        private void ProcessTile(int dim, int rx, int rz, bool forceReRender)
        {
            string mcaPath = GetMcaPath(dim, rx, rz);
            if (!File.Exists(mcaPath))
            {
                _failedOrEmpty.TryAdd((dim, rx, rz), 1);
                return;
            }

            string pngPath = GetCachePngPath(dim, rx, rz);
            var mcaInfo = new FileInfo(mcaPath);
            if (mcaInfo.Length < 8192)
            {
                _failedOrEmpty.TryAdd((dim, rx, rz), 1);
                return;
            }

            if (!forceReRender && File.Exists(pngPath))
            {
                var pngInfo = new FileInfo(pngPath);
                // Usa o PNG em cache se existir e não for mais antigo que o .mca (com margem de 5 min)
                if (pngInfo.Length > 100 && pngInfo.LastWriteTimeUtc >= mcaInfo.LastWriteTimeUtc.AddMinutes(-5))
                {
                    try
                    {
                        using var fs = new FileStream(pngPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                        using var tempBmp = new Bitmap(fs);
                        var clone = new Bitmap(tempBmp);
                        _memoryCache[(dim, rx, rz)] = clone;
                        _onTileReady();
                        return;
                    }
                    catch { }
                }
            }

            Bitmap? rendered = RenderMcaToBitmap(mcaPath, dim);
            if (rendered == null)
            {
                _failedOrEmpty.TryAdd((dim, rx, rz), 1);
                return;
            }

            try
            {
                rendered.Save(pngPath, ImageFormat.Png);
            }
            catch { }

            _memoryCache[(dim, rx, rz)] = rendered;
            _onTileReady();
        }

        private static Bitmap? RenderMcaToBitmap(string mcaPath, int dim)
        {
            byte[] mcaBytes;
            try
            {
                using var fs = new FileStream(mcaPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                if (fs.Length < 8192) return null;
                mcaBytes = new byte[fs.Length];
                int read = 0;
                while (read < mcaBytes.Length)
                {
                    int r = fs.Read(mcaBytes, read, mcaBytes.Length - read);
                    if (r <= 0) break;
                    read += r;
                }
            }
            catch
            {
                return null;
            }

            uint[] baseColors = new uint[512 * 512];
            short[] heights = new short[512 * 512];
            byte[] waterDepths = new byte[512 * 512];
            Array.Fill(heights, (short)-999);

            bool anyChunkRendered = false;
            byte[] decompressBuf = new byte[256 * 1024];

            for (int cz = 0; cz < 32; cz++)
            {
                for (int cx = 0; cx < 32; cx++)
                {
                    int headerIdx = (cx + cz * 32) * 4;
                    if (headerIdx + 4 > mcaBytes.Length) continue;

                    int sectorOffset = (mcaBytes[headerIdx] << 16) | (mcaBytes[headerIdx + 1] << 8) | mcaBytes[headerIdx + 2];
                    int sectorCount = mcaBytes[headerIdx + 3];
                    if (sectorOffset == 0 || sectorCount == 0) continue;

                    int byteOffset = sectorOffset * 4096;
                    if (byteOffset + 5 > mcaBytes.Length) continue;

                    int length = BinaryPrimitives.ReadInt32BigEndian(mcaBytes.AsSpan(byteOffset, 4));
                    byte compressionType = mcaBytes[byteOffset + 4];
                    if (length <= 1 || byteOffset + 4 + length > mcaBytes.Length) continue;

                    try
                    {
                        int nbtLen = DecompressChunk(mcaBytes, byteOffset + 5, length - 1, compressionType, ref decompressBuf);
                        if (nbtLen > 0)
                        {
                            if (ParseAndRenderChunk(decompressBuf.AsSpan(0, nbtLen), cx, cz, dim, baseColors, heights, waterDepths))
                            {
                                anyChunkRendered = true;
                            }
                        }
                    }
                    catch
                    {
                        // Ignora chunk corrompido ou incompleto
                    }
                }
            }

            if (!anyChunkRendered) return null;

            // Aplica sombreamento topográfico 3D (Hillshading Noroeste estilo Xaero's World Map) + profundidade de água
            uint[] finalPixels = new uint[512 * 512];
            for (int z = 0; z < 512; z++)
            {
                int rowOffset = z * 512;
                int northRowOffset = Math.Max(0, z - 1) * 512;

                for (int x = 0; x < 512; x++)
                {
                    int idx = rowOffset + x;
                    uint c = baseColors[idx];
                    if (c == 0) continue;

                    short h = heights[idx];
                    short hNorth = heights[northRowOffset + x];
                    short hWest = heights[rowOffset + Math.Max(0, x - 1)];

                    if (hNorth == -999) hNorth = h;
                    if (hWest == -999) hWest = h;

                    int r = (int)((c >> 16) & 0xFF);
                    int g = (int)((c >> 8) & 0xFF);
                    int b = (int)(c & 0xFF);

                    byte wDepth = waterDepths[idx];
                    if (wDepth > 0)
                    {
                        // Água: mistura a cor do fundo do mar/rio com azul oceânico conforme a profundidade
                        float waterFactor = Math.Clamp(0.48f + wDepth * 0.028f, 0.48f, 0.90f);
                        int wr = Math.Max(18, 48 - wDepth * 2);
                        int wg = Math.Max(48, 108 - wDepth * 3);
                        int wb = Math.Max(115, 195 - wDepth * 3);

                        r = (int)(r * (1f - waterFactor) + wr * waterFactor);
                        g = (int)(g * (1f - waterFactor) + wg * waterFactor);
                        b = (int)(b * (1f - waterFactor) + wb * waterFactor);

                        // Leve brilho se o fundo variar
                        int slopeW = Math.Clamp((h - hNorth) + (h - hWest), -4, 4);
                        int shadeW = slopeW * 3;
                        r = Math.Clamp(r + shadeW, 0, 255);
                        g = Math.Clamp(g + shadeW, 0, 255);
                        b = Math.Clamp(b + shadeW, 0, 255);
                    }
                    else
                    {
                        // Terreno sólido: Relevo 3D (Luz vinda do Noroeste) + leve gradiente de altitude
                        int slope = Math.Clamp((h - hNorth) + (h - hWest), -6, 6);
                        int altitudeDelta = Math.Clamp((h - 64) / 6, -12, 18);
                        int shade = slope * 9 + altitudeDelta;

                        r = Math.Clamp(r + shade, 0, 255);
                        g = Math.Clamp(g + shade, 0, 255);
                        b = Math.Clamp(b + shade, 0, 255);
                    }

                    finalPixels[idx] = 0xFF000000u | ((uint)r << 16) | ((uint)g << 8) | (uint)b;
                }
            }

            var bmp = new Bitmap(512, 512, PixelFormat.Format32bppArgb);
            var bmpData = bmp.LockBits(new Rectangle(0, 0, 512, 512), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            try
            {
                Marshal.Copy((int[])(object)finalPixels, 0, bmpData.Scan0, finalPixels.Length);
            }
            finally
            {
                bmp.UnlockBits(bmpData);
            }

            return bmp;
        }

        private static int DecompressChunk(byte[] src, int offset, int count, byte compressionType, ref byte[] dst)
        {
            using var ms = new MemoryStream(src, offset, count, writable: false);
            Stream decompStream = compressionType switch
            {
                1 => new GZipStream(ms, CompressionMode.Decompress),
                2 => new ZLibStream(ms, CompressionMode.Decompress),
                3 => ms,
                _ => throw new NotSupportedException()
            };

            using (decompStream)
            {
                int total = 0;
                while (true)
                {
                    if (total + 32768 > dst.Length)
                    {
                        Array.Resize(ref dst, dst.Length * 2);
                    }
                    int read = decompStream.Read(dst, total, dst.Length - total);
                    if (read <= 0) break;
                    total += read;
                }
                return total;
            }
        }

        private struct SectionData
        {
            public sbyte Y;
            public uint[] PaletteColors; // 0 = ar/invisível, 1 = água, >1 = cor RGB 0xFFRRGGBB
            public bool[] PaletteIsGrassOrFoliage;
            public long[]? BlockStatesData;
            public byte BiomeTint; // 0=normal, 1=snowy, 2=swamp, 3=badlands/savanna, 4=cherry, 5=dark_forest
        }

        private static bool ParseAndRenderChunk(
            ReadOnlySpan<byte> nbt,
            int cx,
            int cz,
            int dim,
            uint[] baseColors,
            short[] heights,
            byte[] waterDepths)
        {
            int pos = 0;
            if (nbt.Length < 4 || nbt[pos++] != 10) return false; // Root TAG_Compound
            int rootNameLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
            pos += 2 + rootNameLen;

            var sections = new List<SectionData>(24);

            while (pos < nbt.Length)
            {
                byte tagType = nbt[pos++];
                if (tagType == 0) break; // TAG_End

                int nameLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                pos += 2;
                var nameSpan = nbt.Slice(pos, nameLen);
                pos += nameLen;

                if (tagType == 9 && nameLen == 8 && nameSpan.SequenceEqual("sections"u8))
                {
                    byte elemType = nbt[pos++];
                    int count = BinaryPrimitives.ReadInt32BigEndian(nbt.Slice(pos, 4));
                    pos += 4;

                    if (elemType == 10 && count > 0)
                    {
                        for (int i = 0; i < count; i++)
                        {
                            if (ParseSectionCompound(nbt, ref pos, out SectionData sec))
                            {
                                sections.Add(sec);
                            }
                        }
                    }
                    else
                    {
                        for (int i = 0; i < count; i++) SkipTagPayload(nbt, ref pos, elemType);
                    }
                    break; // Já lemos sections, não precisamos do resto do chunk!
                }
                else
                {
                    SkipTagPayload(nbt, ref pos, tagType);
                }
            }

            if (sections.Count == 0) return false;

            // Ordena seções do topo (maior Y) para o fundo (menor Y)
            sections.Sort((a, b) => b.Y.CompareTo(a.Y));

            int baseX = cx * 16;
            int baseZ = cz * 16;

            for (int bz = 0; bz < 16; bz++)
            {
                int mapZ = baseZ + bz;
                int rowIndex = mapZ * 512 + baseX;

                for (int bx = 0; bx < 16; bx++)
                {
                    int pixelIdx = rowIndex + bx;
                    byte waterDepth = 0;
                    short waterSurfaceY = -999;
                    bool foundAirInNether = dim != 1; // No Nether (dim=1), ignora o teto de bedrock até achar ar abaixo de Y=120

                    for (int s = 0; s < sections.Count; s++)
                    {
                        var sec = sections[s];
                        if (dim == 1 && sec.Y >= 8) continue; // Acima do teto do Nether

                        var palette = sec.PaletteColors;
                        if (palette == null || palette.Length == 0) continue;

                        // Se a seção inteira for composta por apenas 1 bloco (ex: ar puro)
                        if (palette.Length == 1 || sec.BlockStatesData == null || sec.BlockStatesData.Length == 0)
                        {
                            uint singleColor = palette[0];
                            if (singleColor == 0)
                            {
                                foundAirInNether = true;
                                continue;
                            }
                            if (!foundAirInNether) continue;

                            if (singleColor == 1) // Seção inteira de água
                            {
                                if (waterSurfaceY == -999) waterSurfaceY = (short)((sec.Y << 4) + 15);
                                waterDepth = (byte)Math.Min(40, waterDepth + 16);
                                continue;
                            }

                            uint finalCol = ApplyBiomeTint(singleColor, sec.PaletteIsGrassOrFoliage[0], sec.BiomeTint);
                            baseColors[pixelIdx] = finalCol;
                            heights[pixelIdx] = waterSurfaceY != -999 ? (short)((sec.Y << 4) + 15) : (short)((sec.Y << 4) + 15);
                            waterDepths[pixelIdx] = waterDepth;
                            goto NextColumn;
                        }

                        int bits = Math.Max(4, CeilLog2(palette.Length));
                        int perLong = 64 / bits;
                        ulong mask = (1UL << bits) - 1UL;
                        var data = sec.BlockStatesData;

                        for (int by = 15; by >= 0; by--)
                        {
                            int worldY = (sec.Y << 4) + by;
                            if (dim == 1 && worldY >= 122) continue;

                            int blockIndex = (by << 8) | (bz << 4) | bx;
                            int longIndex = blockIndex / perLong;
                            if ((uint)longIndex >= (uint)data.Length) continue;

                            int bitOffset = (blockIndex % perLong) * bits;
                            int palIdx = (int)(((ulong)data[longIndex] >> bitOffset) & mask);
                            if ((uint)palIdx >= (uint)palette.Length) continue;

                            uint col = palette[palIdx];
                            if (col == 0) // Ar
                            {
                                foundAirInNether = true;
                                continue;
                            }
                            if (!foundAirInNether) continue;

                            if (col == 1) // Água
                            {
                                if (waterSurfaceY == -999) waterSurfaceY = (short)worldY;
                                if (waterDepth < 45) waterDepth++;
                                continue;
                            }

                            uint tinted = ApplyBiomeTint(col, sec.PaletteIsGrassOrFoliage[palIdx], sec.BiomeTint);
                            baseColors[pixelIdx] = tinted;
                            heights[pixelIdx] = (short)worldY;
                            waterDepths[pixelIdx] = waterDepth;
                            goto NextColumn;
                        }
                    }

                    // Se tinha água até o fundo sem achar bloco sólido
                    if (waterDepth > 0)
                    {
                        baseColors[pixelIdx] = 0xFF1A428A;
                        heights[pixelIdx] = waterSurfaceY;
                        waterDepths[pixelIdx] = waterDepth;
                    }

                NextColumn:;
                }
            }

            return true;
        }

        private static bool ParseSectionCompound(ReadOnlySpan<byte> nbt, ref int pos, out SectionData sec)
        {
            sec = default;
            sec.Y = -100;
            bool hasBlocks = false;

            while (pos < nbt.Length)
            {
                byte tagType = nbt[pos++];
                if (tagType == 0) break;

                int nameLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                pos += 2;
                var nameSpan = nbt.Slice(pos, nameLen);
                pos += nameLen;

                if (tagType == 1 && nameLen == 1 && nameSpan[0] == (byte)'Y')
                {
                    sec.Y = (sbyte)nbt[pos++];
                }
                else if (tagType == 10 && nameLen == 12 && nameSpan.SequenceEqual("block_states"u8))
                {
                    ParseBlockStatesCompound(nbt, ref pos, ref sec);
                    hasBlocks = sec.PaletteColors != null && sec.PaletteColors.Length > 0;
                }
                else if (tagType == 10 && nameLen == 6 && nameSpan.SequenceEqual("biomes"u8))
                {
                    sec.BiomeTint = ParseBiomesCompound(nbt, ref pos);
                }
                else
                {
                    SkipTagPayload(nbt, ref pos, tagType);
                }
            }

            return hasBlocks && sec.Y != -100;
        }

        private static void ParseBlockStatesCompound(ReadOnlySpan<byte> nbt, ref int pos, ref SectionData sec)
        {
            while (pos < nbt.Length)
            {
                byte tagType = nbt[pos++];
                if (tagType == 0) break;

                int nameLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                pos += 2;
                var nameSpan = nbt.Slice(pos, nameLen);
                pos += nameLen;

                if (tagType == 9 && nameLen == 7 && nameSpan.SequenceEqual("palette"u8))
                {
                    byte elemType = nbt[pos++];
                    int count = BinaryPrimitives.ReadInt32BigEndian(nbt.Slice(pos, 4));
                    pos += 4;

                    if (elemType == 10 && count > 0)
                    {
                        var colors = new uint[count];
                        var isFoliage = new bool[count];
                        for (int i = 0; i < count; i++)
                        {
                            string blockName = ParsePaletteEntryCompound(nbt, ref pos);
                            colors[i] = ClassifyBlockColor(blockName, out bool tintable);
                            isFoliage[i] = tintable;
                        }
                        sec.PaletteColors = colors;
                        sec.PaletteIsGrassOrFoliage = isFoliage;
                    }
                    else
                    {
                        for (int i = 0; i < count; i++) SkipTagPayload(nbt, ref pos, elemType);
                    }
                }
                else if (tagType == 12 && nameLen == 4 && nameSpan.SequenceEqual("data"u8))
                {
                    int len = BinaryPrimitives.ReadInt32BigEndian(nbt.Slice(pos, 4));
                    pos += 4;
                    if (len > 0)
                    {
                        var longs = new long[len];
                        for (int i = 0; i < len; i++)
                        {
                            longs[i] = BinaryPrimitives.ReadInt64BigEndian(nbt.Slice(pos, 8));
                            pos += 8;
                        }
                        sec.BlockStatesData = longs;
                    }
                }
                else
                {
                    SkipTagPayload(nbt, ref pos, tagType);
                }
            }
        }

        private static string ParsePaletteEntryCompound(ReadOnlySpan<byte> nbt, ref int pos)
        {
            string blockName = "";
            while (pos < nbt.Length)
            {
                byte tagType = nbt[pos++];
                if (tagType == 0) break;

                int nameLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                pos += 2;
                var nameSpan = nbt.Slice(pos, nameLen);
                pos += nameLen;

                if (tagType == 8 && nameLen == 4 && nameSpan.SequenceEqual("Name"u8))
                {
                    int strLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                    pos += 2;
                    blockName = Encoding.UTF8.GetString(nbt.Slice(pos, strLen));
                    pos += strLen;
                }
                else
                {
                    SkipTagPayload(nbt, ref pos, tagType);
                }
            }
            return blockName;
        }

        private static byte ParseBiomesCompound(ReadOnlySpan<byte> nbt, ref int pos)
        {
            byte tint = 0;
            while (pos < nbt.Length)
            {
                byte tagType = nbt[pos++];
                if (tagType == 0) break;

                int nameLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                pos += 2;
                var nameSpan = nbt.Slice(pos, nameLen);
                pos += nameLen;

                if (tagType == 9 && nameLen == 7 && nameSpan.SequenceEqual("palette"u8))
                {
                    byte elemType = nbt[pos++];
                    int count = BinaryPrimitives.ReadInt32BigEndian(nbt.Slice(pos, 4));
                    pos += 4;
                    if (elemType == 8 && count > 0)
                    {
                        for (int i = 0; i < count; i++)
                        {
                            int sLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                            pos += 2;
                            if (i == 0)
                            {
                                string bio = Encoding.UTF8.GetString(nbt.Slice(pos, sLen));
                                tint = ClassifyBiomeTint(bio);
                            }
                            pos += sLen;
                        }
                    }
                    else
                    {
                        for (int i = 0; i < count; i++) SkipTagPayload(nbt, ref pos, elemType);
                    }
                }
                else
                {
                    SkipTagPayload(nbt, ref pos, tagType);
                }
            }
            return tint;
        }

        private static byte ClassifyBiomeTint(string bio)
        {
            if (string.IsNullOrEmpty(bio)) return 0;
            if (bio.Contains("snow", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("frozen", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("ice", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("taiga", StringComparison.OrdinalIgnoreCase))
                return 1;
            if (bio.Contains("swamp", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("mangrove", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("marsh", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("bog", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("bayou", StringComparison.OrdinalIgnoreCase))
                return 2;
            if (bio.Contains("badlands", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("savanna", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("desert", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("arid", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("outback", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("steppe", StringComparison.OrdinalIgnoreCase))
                return 3;
            if (bio.Contains("cherry", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("sakura", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("blossom", StringComparison.OrdinalIgnoreCase))
                return 4;
            if (bio.Contains("dark_forest", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("blackwood", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("ebony", StringComparison.OrdinalIgnoreCase))
                return 5;
            if (bio.Contains("jungle", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("rainforest", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("tropics", StringComparison.OrdinalIgnoreCase) ||
                bio.Contains("bamboo", StringComparison.OrdinalIgnoreCase))
                return 6;
            return 0;
        }

        private static uint ApplyBiomeTint(uint baseCol, bool tintable, byte biomeTint)
        {
            if (!tintable || biomeTint == 0) return baseCol;

            int r = (int)((baseCol >> 16) & 0xFF);
            int g = (int)((baseCol >> 8) & 0xFF);
            int b = (int)(baseCol & 0xFF);

            switch (biomeTint)
            {
                case 1: // Taiga / Frio (Verde azulado escuro)
                    r = (r * 75) / 100;
                    g = (g * 90) / 100;
                    b = Math.Min(255, (b * 115) / 100);
                    break;
                case 2: // Pântano (Verde oliva musgo)
                    r = (r * 92) / 100;
                    g = (g * 86) / 100;
                    b = (b * 70) / 100;
                    break;
                case 3: // Savana / Badlands (Verde amarelado seco)
                    r = Math.Min(255, (r * 130) / 100);
                    g = (g * 95) / 100;
                    b = (b * 68) / 100;
                    break;
                case 4: // Cherry Grove (Verde vibrante fresco)
                    r = (r * 95) / 100;
                    g = Math.Min(255, (g * 112) / 100);
                    b = (b * 95) / 100;
                    break;
                case 5: // Dark Forest (Verde profundo escuro)
                    r = (r * 72) / 100;
                    g = (g * 82) / 100;
                    b = (b * 72) / 100;
                    break;
                case 6: // Jungle (Verde tropical vivo)
                    r = (r * 85) / 100;
                    g = Math.Min(255, (g * 118) / 100);
                    b = (b * 78) / 100;
                    break;
            }

            return 0xFF000000u | ((uint)r << 16) | ((uint)g << 8) | (uint)b;
        }

        private static uint ClassifyBlockColor(string fullId, out bool tintable)
        {
            tintable = false;
            if (string.IsNullOrEmpty(fullId)) return 0;

            int colon = fullId.IndexOf(':');
            string name = colon >= 0 ? fullId.Substring(colon + 1) : fullId;

            // 1. Ar e blocos invisíveis
            if (name is "air" or "cave_air" or "void_air" or "light" or "barrier" or "structure_void" or "moving_piston")
                return 0;

            // 2. Água e plantas subaquáticas
            if (name is "water" or "bubble_column" or "kelp" or "kelp_plant" or "seagrass" or "tall_seagrass")
                return 1;

            // 3. Lava e fogo
            if (name.Contains("lava") || name.Contains("magma") || name is "fire" or "campfire")
                return 0xFFE85C1A;
            if (name is "soul_fire" or "soul_campfire")
                return 0xFF39C2C9;

            // 4. Neve e Gelo
            if (name.Contains("snow"))
                return 0xFFF4FAFA;
            if (name.Contains("blue_ice"))
                return 0xFF6FA4F7;
            if (name.Contains("packed_ice"))
                return 0xFF8DB4F7;
            if (name.Contains("ice"))
                return 0xFF9EC2FA;

            // 5. Gramas, Musgos, Micélio, Podzol e Nylium
            if (name is "podzol" || name.Contains("podzol"))
                return 0xFF7A5633;
            if (name is "mycelium" || name.Contains("mycelium"))
                return 0xFF726173;
            if (name.Contains("crimson_nylium") || name.Contains("crimson_roots") || name.Contains("crimson_fungus"))
                return 0xFF8F2222;
            if (name.Contains("warped_nylium") || name.Contains("warped_roots") || name.Contains("warped_fungus") || name.Contains("warped_wart"))
                return 0xFF287C6E;
            if (name is "dirt_path" or "grass_path" || name.Contains("path"))
                return 0xFFA2854E;
            if (name is "farmland")
                return 0xFF6B4829;
            if (name.Contains("moss"))
                return 0xFF5A7F32;

            if (name is "grass_block" or "grass" or "tall_grass" or "short_grass" or "fern" or "large_fern" or "sugar_cane" or "bamboo"
                || name.Contains("grass") || name.Contains("turf") || name.Contains("clover") || name.Contains("fern"))
            {
                tintable = true;
                return 0xFF62A343;
            }

            // 6. Folhas de árvores (Vanilla + Mods como Regions Unexplored / Biomes O' Plenty)
            if (name.Contains("leaves") || name.Contains("foliage") || name.Contains("needles") || name.Contains("canopy") || name.Contains("vine"))
            {
                if (name.Contains("cherry") || name.Contains("pink") || name.Contains("sakura") || name.Contains("blossom"))
                    return 0xFFF29BC4;
                if (name.Contains("autumn") || name.Contains("orange") || name.Contains("amber"))
                    return 0xFFD97224;
                if (name.Contains("yellow") || name.Contains("golden") || name.Contains("larch") || name.Contains("aspen"))
                    return 0xFFD6AD29;
                if (name.Contains("red") || name.Contains("maple") || name.Contains("crimson"))
                    return 0xFFB83227;
                if (name.Contains("spruce") || name.Contains("pine") || name.Contains("fir") || name.Contains("redwood"))
                    return 0xFF39613B;
                if (name.Contains("birch"))
                    return 0xFF648744;
                if (name.Contains("azalea") || name.Contains("flowering"))
                    return 0xFF568A35;
                if (name.Contains("mangrove") || name.Contains("willow"))
                    return 0xFF4C6E2B;

                tintable = true;
                return 0xFF457D2E;
            }

            // 7. Areia e Arenito
            if (name.Contains("red_sand"))
                return 0xFFC96226;
            if (name.Contains("soul_sand") || name.Contains("soul_soil"))
                return 0xFF4F3C30;
            if (name.Contains("sand"))
                return 0xFFE3DBAC;

            // 8. Terra, Lama e Argila
            if (name.Contains("mud"))
                return 0xFF3E3A3E;
            if (name.Contains("clay"))
                return 0xFF9EA4B0;
            if (name.Contains("dirt") || name.Contains("coarse") || name.Contains("soil") || name.Contains("peat"))
                return 0xFF8C6342;
            if (name.Contains("gravel"))
                return 0xFF837F7E;

            // 9. Madeiras, Troncos e Tábuas (Construções)
            if (name.Contains("log") || name.Contains("wood") || name.Contains("planks") || name.Contains("stem")
                || name.Contains("slab") || name.Contains("stairs") || name.Contains("fence") || name.Contains("door")
                || name.Contains("trapdoor") || name.Contains("barrel") || name.Contains("chest") || name.Contains("bookshelf"))
            {
                if (name.Contains("spruce") || name.Contains("dark_oak")) return 0xFF573E24;
                if (name.Contains("birch")) return 0xFFC8B77B;
                if (name.Contains("jungle") || name.Contains("acacia")) return 0xFFA66238;
                if (name.Contains("cherry")) return 0xFFD99E9A;
                if (name.Contains("crimson")) return 0xFF6B2737;
                if (name.Contains("warped")) return 0xFF2B6E65;
                if (name.Contains("bamboo")) return 0xFFC7B356;
                if (name.Contains("mangrove")) return 0xFF75312B;
                if (!name.Contains("stone") && !name.Contains("brick") && !name.Contains("deepslate")
                    && !name.Contains("quartz") && !name.Contains("copper") && !name.Contains("andesite")
                    && !name.Contains("diorite") && !name.Contains("granite") && !name.Contains("cobble")
                    && !name.Contains("blackstone") && !name.Contains("prismarine") && !name.Contains("purpur"))
                {
                    return 0xFF8C6B3F;
                }
            }

            // 10. Blocos coloridos (Terracotta, Lã, Concreto, Vidro, Carpete, Cama)
            if (name.Contains("terracotta") || name.Contains("concrete") || name.Contains("wool") || name.Contains("carpet") || name.Contains("bed") || name.Contains("glazed"))
            {
                if (name.Contains("white")) return 0xFFD9DFDF;
                if (name.Contains("orange")) return 0xFFD46A24;
                if (name.Contains("magenta")) return 0xFFB046A4;
                if (name.Contains("light_blue")) return 0xFF6096C9;
                if (name.Contains("yellow")) return 0xFFD9B226;
                if (name.Contains("lime")) return 0xFF6DB528;
                if (name.Contains("pink")) return 0xFFD97D96;
                if (name.Contains("light_gray")) return 0xFF8C8C86;
                if (name.Contains("gray")) return 0xFF4A4E52;
                if (name.Contains("cyan")) return 0xFF2E7C8C;
                if (name.Contains("purple")) return 0xFF73329E;
                if (name.Contains("blue")) return 0xFF324299;
                if (name.Contains("brown")) return 0xFF634229;
                if (name.Contains("green")) return 0xFF4C6324;
                if (name.Contains("red")) return 0xFF962E2A;
                if (name.Contains("black")) return 0xFF212124;
                return 0xFF985E44; // Terracotta padrão
            }

            // 11. Flores, Plantas e Plantações
            if (name is "wheat" or "hay_block" || name.Contains("crop") || name.Contains("dandelion") || name.Contains("sunflower"))
                return 0xFFD6B836;
            if (name.Contains("poppy") || name.Contains("rose") || name.Contains("tulip") || name.Contains("mushroom_block"))
                return 0xFFC43B35;
            if (name.Contains("flower") || name.Contains("orchid") || name.Contains("allium") || name.Contains("lilac") || name.Contains("petal"))
                return 0xFFD175B8;
            if (name.Contains("pumpkin") || name.Contains("carrot"))
                return 0xFFD4741C;
            if (name.Contains("melon") || name.Contains("cactus") || name.Contains("lily_pad"))
                return 0xFF4B8228;

            // 12. Pedras, Rochas, Tijolos e Minérios
            if (name.Contains("end_stone"))
                return 0xFFDFE39F;
            if (name.Contains("purpur"))
                return 0xFFA679A6;
            if (name.Contains("netherrack") || name.Contains("nether_wart") || name.Contains("nether_brick"))
                return 0xFF6B2828;
            if (name.Contains("quartz") || name.Contains("calcite") || name.Contains("diorite") || name.Contains("marble"))
                return 0xFFD9D6D0;
            if (name.Contains("granite") || name.Contains("dripstone") || name.Contains("brick"))
                return 0xFF966353;
            if (name.Contains("prismarine"))
                return 0xFF52968B;
            if (name.Contains("copper"))
                return name.Contains("oxidized") || name.Contains("weathered") ? 0xFF4E947E : 0xFFC26B4E;
            if (name.Contains("amethyst"))
                return 0xFF895BB8;
            if (name.Contains("deepslate") || name.Contains("blackstone") || name.Contains("basalt") || name.Contains("obsidian") || name.Contains("coal"))
                return 0xFF3E3E45;
            if (name.Contains("gold") || name.Contains("glowstone") || name.Contains("shroomlight") || name.Contains("lantern") || name.Contains("torch"))
                return 0xFFE8C23A;
            if (name.Contains("iron") || name.Contains("anvil") || name.Contains("rail") || name.Contains("cauldron"))
                return 0xFFA8A8A8;
            if (name.Contains("glass"))
                return 0xFFA5C4D4;

            // Default: Pedra / Construção
            return 0xFF7D7D7D;
        }

        private static void SkipTagPayload(ReadOnlySpan<byte> nbt, ref int pos, byte tagType)
        {
            switch (tagType)
            {
                case 1: pos += 1; break; // Byte
                case 2: pos += 2; break; // Short
                case 3: pos += 4; break; // Int
                case 4: pos += 8; break; // Long
                case 5: pos += 4; break; // Float
                case 6: pos += 8; break; // Double
                case 7: // Byte_Array
                {
                    int len = BinaryPrimitives.ReadInt32BigEndian(nbt.Slice(pos, 4));
                    pos += 4 + len;
                    break;
                }
                case 8: // String
                {
                    int len = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                    pos += 2 + len;
                    break;
                }
                case 9: // List
                {
                    byte elemType = nbt[pos++];
                    int count = BinaryPrimitives.ReadInt32BigEndian(nbt.Slice(pos, 4));
                    pos += 4;
                    for (int i = 0; i < count; i++)
                    {
                        SkipTagPayload(nbt, ref pos, elemType);
                    }
                    break;
                }
                case 10: // Compound
                {
                    while (pos < nbt.Length)
                    {
                        byte t = nbt[pos++];
                        if (t == 0) break;
                        int nLen = BinaryPrimitives.ReadUInt16BigEndian(nbt.Slice(pos, 2));
                        pos += 2 + nLen;
                        SkipTagPayload(nbt, ref pos, t);
                    }
                    break;
                }
                case 11: // Int_Array
                {
                    int len = BinaryPrimitives.ReadInt32BigEndian(nbt.Slice(pos, 4));
                    pos += 4 + len * 4;
                    break;
                }
                case 12: // Long_Array
                {
                    int len = BinaryPrimitives.ReadInt32BigEndian(nbt.Slice(pos, 4));
                    pos += 4 + len * 8;
                    break;
                }
            }
        }

        private static int CeilLog2(int x)
        {
            if (x <= 1) return 0;
            return 32 - System.Numerics.BitOperations.LeadingZeroCount((uint)(x - 1));
        }

        public void Dispose()
        {
            _cts.Cancel();
            foreach (var bmp in _memoryCache.Values)
            {
                try { bmp.Dispose(); } catch { }
            }
            _memoryCache.Clear();
        }
    }
}
