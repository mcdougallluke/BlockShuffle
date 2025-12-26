package org.lukeeirl.blockShuffle.game;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Manages pre-generation of fresh worlds with async chunk pre-loading.
 * Each world has a unique random seed and is deleted after use.
 * Uses async chunk pre-loading (inspired by Chunky) to prepare worlds without blocking the main thread.
 */
public class WorldPoolService {

    private final Plugin plugin;
    private final WorldService worldService;
    private final Queue<PooledWorld> availableWorlds = new ConcurrentLinkedQueue<>();
    private final Set<String> worldsInUse = ConcurrentHashMap.newKeySet();
    private final int poolSize;
    private final int chunkPreloadRadius;
    private final int maxConcurrentChunkLoads;
    private final AtomicInteger worldCounter = new AtomicInteger(0);

    private volatile boolean initialized = false;
    private volatile boolean shuttingDown = false;

    /**
     * Represents a world in the pool with metadata
     */
    public static class PooledWorld {
        private final World overworld;
        private final World nether;
        private final World theEnd;
        private final String baseName;
        private volatile boolean chunksLoaded;

        public PooledWorld(World overworld, World nether, World theEnd, String baseName) {
            this.overworld = overworld;
            this.nether = nether;
            this.theEnd = theEnd;
            this.baseName = baseName;
            this.chunksLoaded = false;
        }

        public World getOverworld() { return overworld; }
        public World getNether() { return nether; }
        public World getTheEnd() { return theEnd; }
        public String getBaseName() { return baseName; }
        public boolean isChunksLoaded() { return chunksLoaded; }
        public void setChunksLoaded(boolean loaded) { this.chunksLoaded = loaded; }
    }

    public WorldPoolService(Plugin plugin, WorldService worldService, int poolSize, int chunkPreloadRadius, int maxConcurrentChunkLoads) {
        this.plugin = plugin;
        this.worldService = worldService;
        this.poolSize = poolSize;
        this.chunkPreloadRadius = chunkPreloadRadius;
        this.maxConcurrentChunkLoads = maxConcurrentChunkLoads;
    }

    /**
     * Initialize the world pool on server startup.
     * Creates worlds with unique random seeds and pre-loads chunks asynchronously.
     */
    public void initialize() {
        if (initialized) {
            plugin.getLogger().warning("WorldPoolService already initialized!");
            return;
        }

        plugin.getLogger().info("Initializing world pool with " + poolSize + " fresh worlds...");
        plugin.getLogger().info("Each world will have a unique random seed");
        plugin.getLogger().info("Chunk preload radius: " + chunkPreloadRadius + " (±" + chunkPreloadRadius + " chunks around spawn)");

        initialized = true;

        // Stagger world creation to avoid overwhelming server on startup
        for (int i = 0; i < poolSize; i++) {
            long delay = i * 100L; // 5 second delay between each world creation

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!shuttingDown) {
                    generateFreshWorld();
                }
            }, delay);
        }
    }

    /**
     * Generate a fresh world with a unique random seed and async chunk pre-loading.
     */
    private void generateFreshWorld() {
        int worldId = worldCounter.incrementAndGet();
        String baseName = "blockshuffle_pool_" + System.currentTimeMillis() + "_" + worldId;

        plugin.getLogger().info("Creating fresh world with unique seed: " + baseName);

        // World creation MUST happen on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Create the three linked worlds with random seeds (overworld, nether, end)
                World overworld = worldService.createLinkedWorlds(baseName);
                World nether = Bukkit.getWorld(baseName + "_nether");
                World theEnd = Bukkit.getWorld(baseName + "_the_end");

                if (overworld == null || nether == null || theEnd == null) {
                    plugin.getLogger().severe("Failed to create fresh world: " + baseName);
                    return;
                }

                PooledWorld pooledWorld = new PooledWorld(overworld, nether, theEnd, baseName);

                plugin.getLogger().info("World created: " + baseName + ", now pre-loading chunks...");

                // Pre-load chunks asynchronously (Chunky's pattern!)
                preLoadSpawnChunks(pooledWorld, () -> {
                    // Mark as ready and add to pool
                    pooledWorld.setChunksLoaded(true);
                    availableWorlds.offer(pooledWorld);

                    plugin.getLogger().info("Fresh world ready: " + baseName +
                        " (" + availableWorlds.size() + "/" + poolSize + " worlds ready)");
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error creating fresh world: " + baseName, e);
            }
        });
    }

    /**
     * Pre-load chunks around spawn asynchronously.
     * Uses Paper's async chunk loading API (inspired by Chunky).
     *
     * @param pooledWorld The world to pre-load chunks for
     * @param onComplete Callback when chunk loading is complete
     */
    private void preLoadSpawnChunks(PooledWorld pooledWorld, Runnable onComplete) {
        // Pre-load chunks for all three dimensions
        CompletableFuture<Void> overworldFuture = preLoadWorldChunks(pooledWorld.getOverworld());
        CompletableFuture<Void> netherFuture = preLoadWorldChunks(pooledWorld.getNether());
        CompletableFuture<Void> endFuture = preLoadWorldChunks(pooledWorld.getTheEnd());

        // Wait for all dimensions to complete
        CompletableFuture.allOf(overworldFuture, netherFuture, endFuture)
            .thenRun(() -> {
                // Back to main thread for callback
                Bukkit.getScheduler().runTask(plugin, onComplete);
            })
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING,
                    "Error during chunk pre-loading for " + pooledWorld.getBaseName(), ex);
                // Still mark as complete even if some chunks failed
                Bukkit.getScheduler().runTask(plugin, onComplete);
                return null;
            });
    }

    /**
     * Pre-load chunks for a single world dimension.
     * Uses semaphore-based backpressure control (like Chunky).
     */
    private CompletableFuture<Void> preLoadWorldChunks(World world) {
        return CompletableFuture.runAsync(() -> {
            final Semaphore working = new Semaphore(maxConcurrentChunkLoads);
            List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();

            int chunksScheduled = 0;

            // Iterate through chunks in a square around spawn
            for (int x = -chunkPreloadRadius; x <= chunkPreloadRadius; x++) {
                for (int z = -chunkPreloadRadius; z <= chunkPreloadRadius; z++) {
                    if (shuttingDown) {
                        break;
                    }

                    try {
                        // Acquire semaphore permit (blocks if too many chunks loading)
                        working.acquire();

                        final int chunkX = x;
                        final int chunkZ = z;

                        // Use Paper's async chunk loading API
                        CompletableFuture<Chunk> chunkFuture = world.getChunkAtAsync(chunkX, chunkZ, true)
                            .whenComplete((chunk, throwable) -> {
                                // Release semaphore permit
                                working.release();

                                if (throwable != null) {
                                    plugin.getLogger().warning(
                                        "Failed to load chunk [" + chunkX + ", " + chunkZ + "] in " +
                                        world.getName() + ": " + throwable.getMessage()
                                    );
                                }
                            });

                        chunkFutures.add(chunkFuture);
                        chunksScheduled++;

                    } catch (InterruptedException e) {
                        plugin.getLogger().warning("Chunk pre-loading interrupted for " + world.getName());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (shuttingDown || Thread.currentThread().isInterrupted()) {
                    break;
                }
            }

            plugin.getLogger().info("Scheduled " + chunksScheduled + " chunks for pre-loading in " + world.getName());

            // Wait for all chunks to complete
            try {
                CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                    "Some chunks failed to load in " + world.getName(), e);
            }

        }, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("BlockShuffle-ChunkPreloader-" + world.getName());
            thread.setDaemon(true);
            return thread;
        }));
    }

    /**
     * Get a ready world from the pool (instant!).
     * Returns null if no worlds are available.
     *
     * After a world is retrieved, a new fresh world is generated in the background.
     */
    public PooledWorld getReadyWorld() {
        PooledWorld world = availableWorlds.poll();

        if (world != null) {
            worldsInUse.add(world.getBaseName());
            plugin.getLogger().info("Assigned fresh world: " + world.getBaseName() +
                " (" + availableWorlds.size() + " worlds remaining in pool)");

            // Generate a new world in the background to refill the pool
            if (!shuttingDown) {
                Bukkit.getScheduler().runTaskLater(plugin, this::generateFreshWorld, 20L); // 1 second delay
                plugin.getLogger().info("Generating new fresh world to refill pool...");
            }
        } else {
            plugin.getLogger().warning("World pool is empty! No pre-generated worlds available.");
        }

        return world;
    }

    /**
     * Delete a world after use (worlds are NOT recycled - each game gets a fresh seed).
     */
    public void deleteUsedWorld(PooledWorld pooledWorld) {
        if (pooledWorld == null) {
            return;
        }

        worldsInUse.remove(pooledWorld.getBaseName());
        plugin.getLogger().info("Deleting used world: " + pooledWorld.getBaseName());

        // Delete world on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Delete all three dimensions
                worldService.deleteWorld(pooledWorld.getOverworld());
                plugin.getLogger().info("Deleted world: " + pooledWorld.getBaseName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                    "Error deleting world: " + pooledWorld.getBaseName(), e);
            }
        });
    }

    /**
     * Check if the pool is ready (has at least one world available).
     */
    public boolean isPoolReady() {
        return !availableWorlds.isEmpty();
    }

    /**
     * Get the number of available worlds in the pool.
     */
    public int getAvailableWorldCount() {
        return availableWorlds.size();
    }

    /**
     * Get the total pool size configured.
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Shutdown the world pool service.
     * Called when plugin is disabled.
     */
    public void shutdown() {
        shuttingDown = true;
        plugin.getLogger().info("Shutting down world pool service...");

        // Clean up all pooled worlds
        for (PooledWorld pooledWorld : availableWorlds) {
            try {
                worldService.deleteWorld(pooledWorld.getOverworld());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                    "Error deleting pooled world during shutdown: " + pooledWorld.getBaseName(), e);
            }
        }

        availableWorlds.clear();
        worldsInUse.clear();
    }
}
