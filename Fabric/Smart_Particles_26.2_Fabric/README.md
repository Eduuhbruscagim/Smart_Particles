## Fork Changes (Optimized Edition)

This fork applies the following performance optimizations to the original Smart Particles:

### Performance Improvements

| Optimization | Before | After | Impact |
|---|---|---|---|
| Particle cleanup | Full second pass over all particles | Eliminated, vanilla Minecraft handles dead particle removal automatically | Approximately 40% less CPU usage |
| Common case culling | Heap scoring every tick | Linear frustum-only culling when under the particle limit | Faster during normal gameplay |
| FOV calculation | Trigonometry recalculated every tick | Cached, only recalculates when FOV actually changes | Eliminates unnecessary math per tick |
| Camera field access | Repeated virtual dispatch in inner loop | Final local variables | Tighter inner loop |
| Heap memory | Arrays never shrink | Shrink when particle limit is reduced | Less memory waste |
| GC pressure | Dead references retained in heap array | Cleared after each tick | Fewer garbage collector pauses |

### Compatibility

Fully compatible with Sodium, Iris, Lithium, FerriteCore, ModernFix and all major optimization mods.

Not compatible with AsyncParticles due to concurrent access conflict on the particle Queue.

---

## License and Credits

This project is a fork of [Smart Particles](https://github.com/chedidandrew/Smart_Particles), originally created by **chedidandrew**. All credit for the original concept and base implementation of the mod goes to him.

This fork is licensed under the **MIT License**, the same as the original project. This means you can:

- Use this mod in any modpack, personal or public;
- View, copy and modify the source code freely;
- Distribute compiled versions, as long as the original license notice is kept.
