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
    private static final long MAX_BYTES_PER_CACHE = 32L * 1024L * 1024L;

    private final int maxEntries;
    private final Map<CacheKey, BufferedImage> backgroundCache;
    private final Map<CacheKey, BufferedImage> foregroundCache;
    private long backgroundBytes = 0L;
    private long foregroundBytes = 0L;

    public SymbolLayerCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public SymbolLayerCache(int maxEntries) {
        this.maxEntries = Math.max(16, maxEntries);
        this.backgroundCache = createLruMap(this.maxEntries, true);
        this.foregroundCache = createLruMap(this.maxEntries, false);
    }

    public synchronized BufferedImage getOrCreateBackground(CacheKey key, LayerPainter painter) {
        return getOrCreate(backgroundCache, key, painter, true);
    }

    public synchronized BufferedImage getOrCreateForeground(CacheKey key, LayerPainter painter) {
        return getOrCreate(foregroundCache, key, painter, false);
    }

    public synchronized void invalidateAll() {
        for (BufferedImage img : backgroundCache.values()) img.flush();
        for (BufferedImage img : foregroundCache.values()) img.flush();
        backgroundCache.clear();
        foregroundCache.clear();
        backgroundBytes = 0L;
        foregroundBytes = 0L;
    }

    public synchronized void invalidateSymbolType(String symbolType) {
        if (symbolType == null) return;
        backgroundCache.entrySet().removeIf(e -> {
            if (symbolType.equals(e.getKey().symbolType())) {
                backgroundBytes -= estimateBytes(e.getValue());
                e.getValue().flush();
                return true;
            }
            return false;
        });
        foregroundCache.entrySet().removeIf(e -> {
            if (symbolType.equals(e.getKey().symbolType())) {
                foregroundBytes -= estimateBytes(e.getValue());
                e.getValue().flush();
                return true;
            }
            return false;
        });
    }

    public synchronized int backgroundSize() {
        return backgroundCache.size();
    }

    public synchronized int foregroundSize() {
        return foregroundCache.size();
    }

    private static long estimateBytes(BufferedImage img) {
        return (long) img.getWidth() * img.getHeight() * 4L;
    }

    private BufferedImage getOrCreate(Map<CacheKey, BufferedImage> cache, CacheKey key, LayerPainter painter, boolean bg) {
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
        long bytes = estimateBytes(img);
        if (bg) backgroundBytes += bytes;
        else foregroundBytes += bytes;
        cache.put(key, img);
        return img;
    }

    private Map<CacheKey, BufferedImage> createLruMap(int max, boolean bg) {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, BufferedImage> eldest) {
                long curBytes = bg ? backgroundBytes : foregroundBytes;
                if (size() > max || curBytes > MAX_BYTES_PER_CACHE) {
                    long b = estimateBytes(eldest.getValue());
                    if (bg) backgroundBytes = Math.max(0L, backgroundBytes - b);
                    else foregroundBytes = Math.max(0L, foregroundBytes - b);
                    eldest.getValue().flush();
                    return true;
                }
                return false;
            }
        };
    }
}
