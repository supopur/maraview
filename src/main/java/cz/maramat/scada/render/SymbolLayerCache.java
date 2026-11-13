package cz.maramat.scada.render;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU offscreen BufferedImage cache for static equipment background shells and foreground overlays,
 * keyed by (symbolType, widthPx, heightPx, rotationDeg, flipH, flipV, stateVariant).
 */
public class SymbolLayerCache {

    public record CacheKey(
            String symbolType,
            int widthPx,
            int heightPx,
            int rotationDeg,
            boolean flipH,
            boolean flipV,
            String stateVariant
    ) {
        public CacheKey {
            symbolType = (symbolType == null) ? "" : symbolType;
            widthPx = Math.max(1, widthPx);
            heightPx = Math.max(1, heightPx);
            stateVariant = (stateVariant == null) ? "" : stateVariant;
        }
    }

    @FunctionalInterface
    public interface LayerPainter {
        void paint(Graphics2D g, int widthPx, int heightPx);
    }

    public static final int DEFAULT_MAX_ENTRIES = 512;

    private final int maxEntries;
    private final Map<CacheKey, BufferedImage> backgroundCache;
    private final Map<CacheKey, BufferedImage> foregroundCache;

    public SymbolLayerCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public SymbolLayerCache(int maxEntries) {
        this.maxEntries = Math.max(16, maxEntries);
        this.backgroundCache = createLruMap(this.maxEntries);
        this.foregroundCache = createLruMap(this.maxEntries);
    }

    public synchronized BufferedImage getOrCreateBackground(CacheKey key, LayerPainter painter) {
        return getOrCreate(backgroundCache, key, painter);
    }

    public synchronized BufferedImage getOrCreateForeground(CacheKey key, LayerPainter painter) {
        return getOrCreate(foregroundCache, key, painter);
    }

    public synchronized void invalidateAll() {
        backgroundCache.clear();
        foregroundCache.clear();
    }

    public synchronized void invalidateSymbolType(String symbolType) {
        if (symbolType == null) return;
        backgroundCache.keySet().removeIf(k -> symbolType.equals(k.symbolType()));
        foregroundCache.keySet().removeIf(k -> symbolType.equals(k.symbolType()));
    }

    public synchronized int backgroundSize() {
        return backgroundCache.size();
    }

    public synchronized int foregroundSize() {
        return foregroundCache.size();
    }

    private BufferedImage getOrCreate(Map<CacheKey, BufferedImage> cache, CacheKey key, LayerPainter painter) {
        BufferedImage img = cache.get(key);
        if (img != null) {
            return img;
        }
        img = new BufferedImage(key.widthPx(), key.heightPx(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            painter.paint(g, key.widthPx(), key.heightPx());
        } finally {
            g.dispose();
        }
        cache.put(key, img);
        return img;
    }

    private static Map<CacheKey, BufferedImage> createLruMap(int max) {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, BufferedImage> eldest) {
                return size() > max;
            }
        };
    }
}
