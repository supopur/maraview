package cz.maramat.scada.model;

import java.awt.Color;
import java.util.UUID;

/**
 * Freeform vector shape, text label, or embedded picture on a ScadaScreen.
 * Mirrors MaraFlow's GraphicElement model with additional IMAGE support.
 */
public class ScadaGraphicElement {

    public enum Kind {
        LINE("Line"),
        RECTANGLE("Rectangle"),
        ROUNDED_RECTANGLE("Rounded Rectangle"),
        TRIANGLE("Triangle"),
        PENTAGON("Pentagon"),
        HEXAGON("Hexagon"),
        OCTAGON("Octagon"),
        CUSTOM_NGON("Custom N-Gon"),
        CIRCLE("Circle"),
        ELLIPSE("Ellipse / Oval"),
        ARC("Arc"),
        SECTOR("Pie / Sector"),
        TEXT("Text"),
        IMAGE("Picture / Image");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private String id;
    private Kind kind;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private Color strokeColor = new Color(0x33, 0x33, 0x33);
    private Color fillColor = new Color(0xE8, 0xEE, 0xF5);
    private boolean filled = false;
    private int strokeWidth = 1;
    private boolean dashed = false;
    private int ngonSides = 6;
    private String text = "Label";
    private int fontSize = 11;
    private String imageBase64 = "";
    private String imagePath = "";
    private boolean preserveAspectRatio = true;
    private double opacity = 1.0;
    private boolean indicatorEnabled = false;
    private String indicatorPin = "";
    private Color indicatorActiveColor = new Color(0xE5, 0x39, 0x35);
    private Color indicatorInactiveColor = new Color(0xF0, 0xF4, 0xF8);

    public ScadaGraphicElement() {
        this.id = UUID.randomUUID().toString();
        this.kind = Kind.RECTANGLE;
        this.gridW = 6;
        this.gridH = 4;
    }

    public ScadaGraphicElement(Kind kind, int gridX, int gridY, int gridW, int gridH) {
        this.id = UUID.randomUUID().toString();
        this.kind = (kind == null) ? Kind.RECTANGLE : kind;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridW = gridW;
        this.gridH = gridH;
    }

    public ScadaGraphicElement copy() {
        ScadaGraphicElement g = new ScadaGraphicElement(kind, gridX, gridY, gridW, gridH);
        g.id = this.id;
        g.strokeColor = this.strokeColor;
        g.fillColor = this.fillColor;
        g.filled = this.filled;
        g.strokeWidth = this.strokeWidth;
        g.dashed = this.dashed;
        g.ngonSides = this.ngonSides;
        g.text = this.text;
        g.fontSize = this.fontSize;
        g.imageBase64 = this.imageBase64;
        g.imagePath = this.imagePath;
        g.preserveAspectRatio = this.preserveAspectRatio;
        g.opacity = this.opacity;
        g.indicatorEnabled = this.indicatorEnabled;
        g.indicatorPin = this.indicatorPin;
        g.indicatorActiveColor = this.indicatorActiveColor;
        g.indicatorInactiveColor = this.indicatorInactiveColor;
        return g;
    }

    public int normalizedX() {
        return (kind == Kind.LINE) ? Math.min(gridX, gridX + gridW) : Math.min(gridX, gridX + gridW);
    }

    public int normalizedY() {
        return (kind == Kind.LINE) ? Math.min(gridY, gridY + gridH) : Math.min(gridY, gridY + gridH);
    }

    public int normalizedW() {
        return Math.max(1, Math.abs(gridW));
    }

    public int normalizedH() {
        return Math.max(1, Math.abs(gridH));
    }

    public boolean containsGridPoint(int gx, int gy) {
        int x0 = Math.min(gridX, gridX + gridW);
        int y0 = Math.min(gridY, gridY + gridH);
        int w = Math.max(1, Math.abs(gridW));
        int h = Math.max(1, Math.abs(gridH));
        return gx >= x0 && gx <= x0 + w && gy >= y0 && gy <= y0 + h;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = (kind == null) ? Kind.RECTANGLE : kind;
    }

    public int getGridX() {
        return gridX;
    }

    public void setGridX(int gridX) {
        this.gridX = gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public void setGridY(int gridY) {
        this.gridY = gridY;
    }

    public int getGridW() {
        return gridW;
    }

    public void setGridW(int gridW) {
        this.gridW = gridW;
    }

    public int getGridH() {
        return gridH;
    }

    public void setGridH(int gridH) {
        this.gridH = gridH;
    }

    public Color getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(Color strokeColor) {
        this.strokeColor = (strokeColor == null) ? new Color(0x33, 0x33, 0x33) : strokeColor;
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = (fillColor == null) ? new Color(0xE8, 0xEE, 0xF5) : fillColor;
    }

    public boolean isFilled() {
        return filled;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }

    public int getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeWidth(int strokeWidth) {
        this.strokeWidth = Math.max(1, Math.min(12, strokeWidth));
    }

    public boolean isDashed() {
        return dashed;
    }

    public void setDashed(boolean dashed) {
        this.dashed = dashed;
    }

    public int getNgonSides() {
        return ngonSides;
    }

    public void setNgonSides(int ngonSides) {
        this.ngonSides = Math.max(3, Math.min(24, ngonSides));
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = (text == null) ? "" : text;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = Math.max(6, Math.min(96, fontSize));
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = (imageBase64 == null) ? "" : imageBase64;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = (imagePath == null) ? "" : imagePath;
    }

    public boolean isPreserveAspectRatio() {
        return preserveAspectRatio;
    }

    public void setPreserveAspectRatio(boolean preserveAspectRatio) {
        this.preserveAspectRatio = preserveAspectRatio;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.max(0.05, Math.min(1.0, opacity));
    }

    public boolean isIndicatorEnabled() {
        return indicatorEnabled;
    }

    public void setIndicatorEnabled(boolean indicatorEnabled) {
        this.indicatorEnabled = indicatorEnabled;
    }

    public String getIndicatorPin() {
        return indicatorPin;
    }

    public void setIndicatorPin(String indicatorPin) {
        this.indicatorPin = (indicatorPin == null) ? "" : indicatorPin.trim();
    }

    public Color getIndicatorActiveColor() {
        return indicatorActiveColor;
    }

    public void setIndicatorActiveColor(Color indicatorActiveColor) {
        this.indicatorActiveColor = (indicatorActiveColor == null) ? new Color(0xE5, 0x39, 0x35) : indicatorActiveColor;
    }

    public Color getIndicatorInactiveColor() {
        return indicatorInactiveColor;
    }

    public void setIndicatorInactiveColor(Color indicatorInactiveColor) {
        this.indicatorInactiveColor = (indicatorInactiveColor == null) ? new Color(0xF0, 0xF4, 0xF8) : indicatorInactiveColor;
    }
}
