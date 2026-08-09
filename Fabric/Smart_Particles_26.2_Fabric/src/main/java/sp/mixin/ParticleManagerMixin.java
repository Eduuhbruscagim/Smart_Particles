package sp.mixin;

import sp.SPConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public abstract class ParticleManagerMixin {

    @Shadow
    private Map<ParticleRenderType, ParticleGroup> particles;

    @Shadow
    private Object2IntOpenHashMap<ParticleLimit> trackedParticleCounts;

    // Heap arrays for the top-N closest particles algorithm
    @Unique
    private Particle[] spHeapParticles;
    @Unique
    private double[] spHeapScores;

    // FOV cache to avoid recalculating cos/toRadians every tick
    @Unique
    private double spCachedFov = -1;
    @Unique
    private double spCachedFrustumThresholdSq;

    @Inject(method = "tick", at = @At("TAIL"))
    private void smartparticles$enforceParticleLimit(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        int limit = Math.max(0, SPConfig.instance.particleLimit);
        boolean smartCulling = SPConfig.instance.smartCameraCulling;

        var world = client.level;
        double protectionThresholdSq = (world != null && (world.isRaining() || world.isThundering())) ? 512.0 : 25.0;

        // 1. LIMIT = 0 LOGIC — remove all particles
        if (limit == 0) {
            for (ParticleGroup group : particles.values()) {
                Queue<Particle> q = ((ParticleGroupAccessor) group).smartparticles$getParticles();
                Iterator<? extends Particle> it = q.iterator();
                while (it.hasNext()) {
                    Particle p = (Particle) it.next();
                    it.remove();

                    // FIX: Null Check
                    if (p == null) continue;

                    p.remove();
                    decrementGroupCount(p);
                }
            }
            return;
        }

        // Count total particles across all groups
        int total = 0;
        for (ParticleGroup group : particles.values()) {
            total += group.size();
        }

        // Early exit: no smart culling and under the limit — nothing to do
        if (!smartCulling && total <= limit) return;

        // Camera setup
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        Vec3 camDir = Vec3.directionFromRotation(camera.xRot(), camera.yRot());

        // [Opt 1] Cache FOV → frustum threshold. Only recalculate when FOV changes.
        double fov = ((Integer) client.options.fov().get()).doubleValue();
        if (fov != spCachedFov) {
            spCachedFov = fov;
            double cos = Math.cos(Math.toRadians((fov / 2.0) + 30.0));
            spCachedFrustumThresholdSq = cos * cos;
        }
        double frustumThresholdSq = spCachedFrustumThresholdSq;

        // [Opt 3] Extract camera and player fields to final locals for tight loop
        final double px = player.getX();
        final double py = player.getY();
        final double pz = player.getZ();
        final double camX = camPos.x;
        final double camY = camPos.y;
        final double camZ = camPos.z;
        final double camDirX = camDir.x;
        final double camDirY = camDir.y;
        final double camDirZ = camDir.z;

        // [Opt 3.4] Fast path: smartCulling on but under limit — frustum cull only, skip heap entirely.
        // This avoids O(n log n) heap operations when only O(n) frustum culling is needed.
        if (smartCulling && total <= limit) {
            for (ParticleGroup group : particles.values()) {
                Queue<Particle> q = ((ParticleGroupAccessor) group).smartparticles$getParticles();
                Iterator<Particle> it = q.iterator();
                while (it.hasNext()) {
                    Particle p = it.next();
                    if (p == null) { it.remove(); continue; }

                    SPAccessor acc = (SPAccessor) p;
                    double dx = acc.smartparticles$getX() - px;
                    double dy = acc.smartparticles$getY() - py;
                    double dz = acc.smartparticles$getZ() - pz;
                    double distSq = dx * dx + dy * dy + dz * dz;

                    // Protected particles (close to player) are never frustum-culled
                    if (distSq <= protectionThresholdSq) continue;

                    double ex = acc.smartparticles$getX() - camX;
                    double ey = acc.smartparticles$getY() - camY;
                    double ez = acc.smartparticles$getZ() - camZ;
                    double dot = ex * camDirX + ey * camDirY + ez * camDirZ;

                    if (dot > 0) {
                        double eDistSq = ex * ex + ey * ey + ez * ez;
                        if (dot * dot > frustumThresholdSq * eDistSq) continue; // in frustum
                    }

                    // Outside frustum and not protected — remove
                    it.remove();
                    p.remove();
                    decrementGroupCount(p);
                }
            }
            return;
        }

        // --- Full heap-based enforcement (total > limit or smartCulling is off) ---

        // [Opt 4] Allocate or shrink heap arrays as needed
        if (this.spHeapParticles == null
                || this.spHeapParticles.length < limit
                || this.spHeapParticles.length > limit * 4) {
            this.spHeapParticles = new Particle[limit];
            this.spHeapScores = new double[limit];
        }

        final Particle[] heapParticles = this.spHeapParticles;
        final double[] heapScores = this.spHeapScores;
        int heapSize = 0;

        double frustumPenalty = 1.0e10;

        // 2. MAIN SCORING LOOP
        for (ParticleGroup group : particles.values()) {
            Queue<Particle> q = ((ParticleGroupAccessor) group).smartparticles$getParticles();
            Iterator<Particle> it = q.iterator();
            while (it.hasNext()) {
                Particle p = it.next();

                // FIX: Null Check (Safely remove garbage if found)
                if (p == null) {
                    it.remove();
                    continue;
                }

                SPAccessor acc = (SPAccessor) p;

                double dx = acc.smartparticles$getX() - px;
                double dy = acc.smartparticles$getY() - py;
                double dz = acc.smartparticles$getZ() - pz;
                double distSq = dx * dx + dy * dy + dz * dz;

                boolean protectedParticle = distSq <= protectionThresholdSq;
                boolean inFrustum = false;

                if (!protectedParticle) {
                    double ex = acc.smartparticles$getX() - camX;
                    double ey = acc.smartparticles$getY() - camY;
                    double ez = acc.smartparticles$getZ() - camZ;

                    double dot = ex * camDirX + ey * camDirY + ez * camDirZ;

                    if (dot > 0) {
                        double eDistSq = ex * ex + ey * ey + ez * ez;
                        if (dot * dot > frustumThresholdSq * eDistSq) {
                            inFrustum = true;
                        }
                    }
                }

                // Smart culling: immediately remove particles outside camera view
                if (smartCulling && !inFrustum && !protectedParticle) {
                    it.remove();
                    p.remove();
                    decrementGroupCount(p);
                    continue;
                }

                double score = distSq;
                if (!smartCulling && !inFrustum && !protectedParticle) {
                    score += frustumPenalty;
                }

                if (heapSize < limit) {
                    // Heap has room — add directly
                    heapParticles[heapSize] = p;
                    heapScores[heapSize] = score;
                    heapSiftUp(heapParticles, heapScores, heapSize);
                    heapSize++;
                } else if (score < heapScores[0]) {
                    // [Opt 3.1] Evict the heap root: mark it dead and leave it in the Queue.
                    // Vanilla's next tick() will find !isAlive(), remove it from the Queue,
                    // and handle trackedParticleCounts decrement automatically.
                    // This eliminates the entire second-pass cleanup loop.
                    heapParticles[0].remove();

                    heapParticles[0] = p;
                    heapScores[0] = score;
                    heapSiftDown(heapParticles, heapScores, heapSize, 0);
                } else {
                    // Particle is farther than all heap entries — remove immediately via iterator
                    it.remove();
                    p.remove();
                    decrementGroupCount(p);
                }
            }
        }

        // [Opt 1] Clear heap array references to allow GC of dead particles between ticks
        for (int i = 0; i < heapSize; i++) {
            heapParticles[i] = null;
        }
    }

    @Unique
    private void decrementGroupCount(Particle p) {
        // Using ifPresent to handle the Optional from getParticleLimit()
        p.getParticleLimit().ifPresent(group -> {
            int current = trackedParticleCounts.getInt(group);
            if (current <= 1) {
                trackedParticleCounts.removeInt(group);
            } else {
                trackedParticleCounts.put(group, current - 1);
            }
        });
    }

    @Unique
    private static void heapSiftUp(Particle[] ps, double[] ds, int idx) {
        while (idx > 0) {
            int parent = (idx - 1) >>> 1;
            if (ds[parent] >= ds[idx]) return;
            swap(ps, ds, parent, idx);
            idx = parent;
        }
    }

    @Unique
    private static void heapSiftDown(Particle[] ps, double[] ds, int size, int idx) {
        while (true) {
            int left = (idx << 1) + 1;
            if (left >= size) return;

            int right = left + 1;
            int largest = left;

            if (right < size && ds[right] > ds[left]) {
                largest = right;
            }

            if (ds[idx] >= ds[largest]) return;

            swap(ps, ds, idx, largest);
            idx = largest;
        }
    }

    @Unique
    private static void swap(Particle[] ps, double[] ds, int a, int b) {
        Particle tp = ps[a];
        ps[a] = ps[b];
        ps[b] = tp;

        double td = ds[a];
        ds[a] = ds[b];
        ds[b] = td;
    }
}
