package cz.maramat.scada.render;

import cz.maramat.scada.render.SymbolLayerCache.CacheKey;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class SymbolLayerCacheTest {

    @Test
    void cachesByCompositeKeyAndEvictsLru() {
        SymbolLayerCache cache = new SymbolLayerCache(16);
        AtomicInteger paintCount = new AtomicInteger();

        CacheKey keyA = new CacheKey("CentrifugalPump", 80, 100, 0, false, false, "STOPPED");
        CacheKey keyB = new CacheKey("CentrifugalPump", 80, 100, 90, false, false, "STOPPED");

        BufferedImage imgA1 = cache.getOrCreateBackground(keyA, (g, w, h) -> {
            paintCount.incrementAndGet();
            ScadaShader.paintVoluteCasing(g, w / 2.0, h / 2.0, 30, 18, ScadaPalette.Material.CAST_IRON);
        });
        BufferedImage imgA2 = cache.getOrCreateBackground(keyA, (g, w, h) -> paintCount.incrementAndGet());
        assertSame(imgA1, imgA2);
        assertEquals(1, paintCount.get());

        BufferedImage imgB = cache.getOrCreateBackground(keyB, (g, w, h) -> paintCount.incrementAndGet());
        assertNotSame(imgA1, imgB);
        assertEquals(2, paintCount.get());

        // Fill past capacity (16) to verify LRU bound
        for (int i = 0; i < 20; i++) {
            CacheKey k = new CacheKey("HandValve", 40 + i, 40, 0, false, false, "OPEN");
            cache.getOrCreateForeground(k, (g, w, h) -> {});
        }
        assertEquals(16, cache.foregroundSize());

        cache.invalidateSymbolType("CentrifugalPump");
        assertEquals(0, cache.backgroundSize());
        assertEquals(16, cache.foregroundSize());

        cache.invalidateAll();
        assertEquals(0, cache.foregroundSize());
    }
}
