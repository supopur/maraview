package cz.maramat.scada.io;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.CommSettings;
import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipeDiameter;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.tags.AlarmConfig;
import cz.maramat.scada.tags.MflOrigin;
import cz.maramat.scada.tags.ModbusBinding;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagSourceType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic UTF-8 JSON serializer/deserializer for Maraview SCADA Project (.msl) files.
 */
public final class MslProjectSerializer {

    private MslProjectSerializer() {
    }

    public static void saveProject(ScadaProject project, File file) throws IOException {
        if (project == null) {
            throw new IllegalArgumentException("Project cannot be null.");
        }
        if (file == null) {
            throw new IllegalArgumentException("Target file cannot be null.");
        }
        String json = toJsonString(project);
        Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        project.setProjectFile(file);
        project.setDirty(false);
    }

    public static ScadaProject loadProject(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("MSL file not found: " + file);
        }
        String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        ScadaProject project = fromJsonString(json);
        project.setProjectFile(file);
        project.setDirty(false);
        return project;
    }

    public static String toJsonString(ScadaProject project) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", "maraview-scada");
        root.put("version", project.getVersion());
        root.put("projectName", project.getProjectName());
        root.put("author", project.getAuthor());
        root.put("description", project.getDescription());
        root.put("linkedMflPath", project.getLinkedMflPath());
        root.put("commSettings", serializeCommSettings(project.getCommSettings()));

        List<Object> tagList = new ArrayList<>();
        for (ScadaTag tag : project.getTags().values()) {
            tagList.add(serializeTag(tag));
        }
        root.put("tags", tagList);

        List<Object> screenList = new ArrayList<>();
        for (ScadaScreen screen : project.getScreens()) {
            screenList.add(serializeScreen(screen));
        }
        root.put("screens", screenList);

        return JsonCodec.stringify(root);
    }

    public static ScadaProject fromJsonString(String json) throws IOException {
        Map<String, Object> root = JsonCodec.parseObject(json);
        ScadaProject project = new ScadaProject();
        project.getScreens().clear();

        project.setVersion(getString(root, "version", ScadaProject.VERSION));
        project.setProjectName(getString(root, "projectName", "UntitledScada"));
        project.setAuthor(getString(root, "author", ""));
        project.setDescription(getString(root, "description", ""));
        project.setLinkedMflPath(getString(root, "linkedMflPath", ""));

        Map<String, Object> commMap = getMap(root, "commSettings");
        if (commMap != null) {
            project.setCommSettings(deserializeCommSettings(commMap));
        }

        for (Object item : getList(root, "tags")) {
            if (item instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                ScadaTag tag = deserializeTag((Map<String, Object>) m);
                project.putTag(tag);
            }
        }

        for (Object item : getList(root, "screens")) {
            if (item instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                ScadaScreen screen = deserializeScreen((Map<String, Object>) m);
                project.getScreens().add(screen);
            }
        }

        if (project.getScreens().isEmpty()) {
            project.getScreens().add(new ScadaScreen("Overview"));
        }
        return project;
    }

    private static Map<String, Object> serializeCommSettings(CommSettings comm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", comm.getMode().name());
        m.put("host", comm.getHost());
        m.put("tcpPort", comm.getTcpPort());
        m.put("serialPort", comm.getSerialPort());
        m.put("baudRate", comm.getBaudRate());
        m.put("slaveId", comm.getSlaveId());
        m.put("pollIntervalMs", comm.getPollIntervalMs());
        return m;
    }

    private static CommSettings deserializeCommSettings(Map<String, Object> m) {
        CommSettings c = new CommSettings();
        c.setMode(parseEnum(CommSettings.Mode.class, getString(m, "mode", "INTERNAL_SIM"), CommSettings.Mode.INTERNAL_SIM));
        c.setHost(getString(m, "host", "127.0.0.1"));
        c.setTcpPort(getInt(m, "tcpPort", 502));
        c.setSerialPort(getString(m, "serialPort", ""));
        c.setBaudRate(getInt(m, "baudRate", 115200));
        c.setSlaveId(getInt(m, "slaveId", 1));
        c.setPollIntervalMs(getInt(m, "pollIntervalMs", 50));
        return c;
    }

    private static Map<String, Object> serializeTag(ScadaTag tag) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", tag.getName());
        m.put("dataType", tag.getDataType().name());
        m.put("sourceType", tag.getSourceType().name());
        m.put("mflOrigin", tag.getMflOrigin().name());
        m.put("addressOrRef", tag.getAddressOrRef());
        m.put("initialValue", tag.getInitialValue());
        m.put("description", tag.getDescription());
        m.put("engineeringUnits", tag.getEngineeringUnits());
        m.put("minValue", tag.getMinValue());
        m.put("maxValue", tag.getMaxValue());
        m.put("alarmConfig", serializeAlarmConfig(tag.getAlarmConfig()));
        if (tag.getModbusBinding() != null) {
            m.put("modbusBinding", serializeModbusBinding(tag.getModbusBinding()));
        }
        return m;
    }

    private static ScadaTag deserializeTag(Map<String, Object> m) {
        String name = getString(m, "name", "Tag1");
        DataType dt = parseEnum(DataType.class, getString(m, "dataType", "BOOL"), DataType.BOOL);
        TagSourceType src = parseEnum(TagSourceType.class, getString(m, "sourceType", "INTERNAL"), TagSourceType.INTERNAL);
        ScadaTag tag = new ScadaTag(name, dt, src);
        tag.setMflOrigin(parseEnum(MflOrigin.class, getString(m, "mflOrigin", "NONE"), MflOrigin.NONE));
        tag.setAddressOrRef(getString(m, "addressOrRef", ""));
        tag.setInitialValue(getString(m, "initialValue", dt.getDefaultConstant()));
        tag.setDescription(getString(m, "description", ""));
        tag.setEngineeringUnits(getString(m, "engineeringUnits", ""));
        tag.setMinValue(getDouble(m, "minValue", 0.0));
        tag.setMaxValue(getDouble(m, "maxValue", 100.0));

        Map<String, Object> alarmMap = getMap(m, "alarmConfig");
        if (alarmMap != null) {
            tag.setAlarmConfig(deserializeAlarmConfig(alarmMap));
        }
        Map<String, Object> modbusMap = getMap(m, "modbusBinding");
        if (modbusMap != null) {
            tag.setModbusBinding(deserializeModbusBinding(modbusMap));
        }
        tag.resetRuntimeToInitial();
        return tag;
    }

    private static Map<String, Object> serializeAlarmConfig(AlarmConfig a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", a.isEnabled());
        m.put("highHighLimit", a.getHighHighLimit());
        m.put("highLimit", a.getHighLimit());
        m.put("lowLimit", a.getLowLimit());
        m.put("lowLowLimit", a.getLowLowLimit());
        m.put("faultOnTrue", a.isFaultOnTrue());
        m.put("alarmMessage", a.getAlarmMessage());
        return m;
    }

    private static AlarmConfig deserializeAlarmConfig(Map<String, Object> m) {
        AlarmConfig a = new AlarmConfig();
        a.setEnabled(getBoolean(m, "enabled", false));
        a.setHighHighLimit(getNullableDouble(m, "highHighLimit"));
        a.setHighLimit(getNullableDouble(m, "highLimit"));
        a.setLowLimit(getNullableDouble(m, "lowLimit"));
        a.setLowLowLimit(getNullableDouble(m, "lowLowLimit"));
        a.setFaultOnTrue(getBoolean(m, "faultOnTrue", false));
        a.setAlarmMessage(getString(m, "alarmMessage", ""));
        return a;
    }

    private static Map<String, Object> serializeModbusBinding(ModbusBinding b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slaveId", b.getSlaveId());
        m.put("functionCode", b.getFunctionCode());
        m.put("address", b.getAddress());
        m.put("scale", b.getScale());
        m.put("offset", b.getOffset());
        return m;
    }

    private static ModbusBinding deserializeModbusBinding(Map<String, Object> m) {
        ModbusBinding b = new ModbusBinding();
        b.setSlaveId(getInt(m, "slaveId", 1));
        b.setFunctionCode(getInt(m, "functionCode", 3));
        b.setAddress(getInt(m, "address", 0));
        b.setScale(getDouble(m, "scale", 1.0));
        b.setOffset(getDouble(m, "offset", 0.0));
        return b;
    }

    private static Map<String, Object> serializeScreen(ScadaScreen s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("gridSizePx", s.getGridSizePx());
        m.put("backgroundColorHex", s.getBackgroundColorHex());
        m.put("showGrid", s.isShowGrid());

        List<Object> symList = new ArrayList<>();
        for (ScadaSymbolInstance sym : s.getSymbols()) {
            symList.add(serializeSymbol(sym));
        }
        m.put("symbols", symList);

        List<Object> pipeList = new ArrayList<>();
        for (ScadaPipe pipe : s.getPipes()) {
            pipeList.add(serializePipe(pipe));
        }
        m.put("pipes", pipeList);
        return m;
    }

    private static ScadaScreen deserializeScreen(Map<String, Object> m) {
        ScadaScreen s = new ScadaScreen(getString(m, "name", "Screen"));
        s.setId(getString(m, "id", s.getId()));
        s.setGridSizePx(getInt(m, "gridSizePx", ScadaScreen.DEFAULT_GRID_SIZE_PX));
        s.setBackgroundColorHex(getString(m, "backgroundColorHex", ScadaScreen.BG_LIGHT_SLATE));
        s.setShowGrid(getBoolean(m, "showGrid", true));

        for (Object item : getList(m, "symbols")) {
            if (item instanceof Map<?, ?> symMap) {
                @SuppressWarnings("unchecked")
                ScadaSymbolInstance sym = deserializeSymbol((Map<String, Object>) symMap);
                s.getSymbols().add(sym);
            }
        }
        for (Object item : getList(m, "pipes")) {
            if (item instanceof Map<?, ?> pipeMap) {
                @SuppressWarnings("unchecked")
                ScadaPipe pipe = deserializePipe((Map<String, Object>) pipeMap);
                s.getPipes().add(pipe);
            }
        }
        return s;
    }

    private static Map<String, Object> serializeSymbol(ScadaSymbolInstance sym) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", sym.getId());
        m.put("symbolType", sym.getSymbolType());
        m.put("name", sym.getName());
        m.put("gridX", sym.getGridX());
        m.put("gridY", sym.getGridY());
        m.put("gridW", sym.getGridW());
        m.put("gridH", sym.getGridH());
        m.put("rotationDeg", sym.getRotationDeg());
        m.put("flipH", sym.isFlipH());
        m.put("flipV", sym.isFlipV());
        m.put("zIndex", sym.getZIndex());
        m.put("tooltip", sym.getTooltip());
        m.put("properties", new LinkedHashMap<>(sym.getProperties()));
        m.put("tagBindings", new LinkedHashMap<>(sym.getTagBindings()));

        List<Object> portList = new ArrayList<>();
        for (PipePort port : sym.getPorts()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", port.id());
            pm.put("label", port.label());
            pm.put("offsetX", port.offsetX());
            pm.put("offsetY", port.offsetY());
            pm.put("direction", port.direction().name());
            portList.add(pm);
        }
        m.put("ports", portList);
        return m;
    }

    private static ScadaSymbolInstance deserializeSymbol(Map<String, Object> m) {
        ScadaSymbolInstance sym = new ScadaSymbolInstance(
                getString(m, "symbolType", "Generic"),
                getString(m, "name", "SYM-1"),
                getInt(m, "gridX", 0),
                getInt(m, "gridY", 0),
                getInt(m, "gridW", 6),
                getInt(m, "gridH", 6)
        );
        sym.setId(getString(m, "id", sym.getId()));
        sym.setRotationDeg(getInt(m, "rotationDeg", 0));
        sym.setFlipH(getBoolean(m, "flipH", false));
        sym.setFlipV(getBoolean(m, "flipV", false));
        sym.setZIndex(getInt(m, "zIndex", 0));
        sym.setTooltip(getString(m, "tooltip", ""));

        Map<String, Object> propsMap = getMap(m, "properties");
        if (propsMap != null) {
            for (Map.Entry<String, Object> e : propsMap.entrySet()) {
                sym.getProperties().put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
        }
        Map<String, Object> bindMap = getMap(m, "tagBindings");
        if (bindMap != null) {
            for (Map.Entry<String, Object> e : bindMap.entrySet()) {
                if (e.getValue() != null) {
                    sym.getTagBindings().put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
        }
        for (Object item : getList(m, "ports")) {
            if (item instanceof Map<?, ?> pmRaw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pm = (Map<String, Object>) pmRaw;
                String pid = getString(pm, "id", "port");
                String label = getString(pm, "label", pid);
                int ox = getInt(pm, "offsetX", 0);
                int oy = getInt(pm, "offsetY", 0);
                CardinalDirection dir = parseEnum(CardinalDirection.class, getString(pm, "direction", "EAST"), CardinalDirection.EAST);
                sym.getPorts().add(new PipePort(pid, label, ox, oy, dir));
            }
        }
        return sym;
    }

    private static Map<String, Object> serializePipe(ScadaPipe pipe) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", pipe.getId());
        m.put("sourceSymbolId", pipe.getSourceSymbolId());
        m.put("sourcePortId", pipe.getSourcePortId());
        m.put("targetSymbolId", pipe.getTargetSymbolId());
        m.put("targetPortId", pipe.getTargetPortId());
        m.put("sourceTeePipeId", pipe.getSourceTeePipeId());
        m.put("diameter", pipe.getDiameter().name());
        m.put("fluidType", pipe.getFluidType().name());
        m.put("autoFlow", pipe.isAutoFlow());
        m.put("flowTag", pipe.getFlowTag());
        m.put("label", pipe.getLabel());

        List<Object> verts = new ArrayList<>();
        for (GridPoint gp : pipe.getVertices()) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("x", gp.x());
            vm.put("y", gp.y());
            verts.add(vm);
        }
        m.put("vertices", verts);
        return m;
    }

    private static ScadaPipe deserializePipe(Map<String, Object> m) {
        ScadaPipe pipe = new ScadaPipe(getString(m, "id", null));
        pipe.setSourceSymbolId(getNullableString(m, "sourceSymbolId"));
        pipe.setSourcePortId(getNullableString(m, "sourcePortId"));
        pipe.setTargetSymbolId(getNullableString(m, "targetSymbolId"));
        pipe.setTargetPortId(getNullableString(m, "targetPortId"));
        pipe.setSourceTeePipeId(getNullableString(m, "sourceTeePipeId"));
        pipe.setDiameter(parseEnum(PipeDiameter.class, getString(m, "diameter", "MEDIUM"), PipeDiameter.MEDIUM));
        pipe.setFluidType(parseEnum(FluidType.class, getString(m, "fluidType", "WATER"), FluidType.WATER));
        pipe.setAutoFlow(getBoolean(m, "autoFlow", true));
        pipe.setFlowTag(getString(m, "flowTag", ""));
        pipe.setLabel(getString(m, "label", ""));

        for (Object item : getList(m, "vertices")) {
            if (item instanceof Map<?, ?> vmRaw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> vm = (Map<String, Object>) vmRaw;
                pipe.getVertices().add(new GridPoint(getInt(vm, "x", 0), getInt(vm, "y", 0)));
            }
        }
        return pipe;
    }

    private static String getString(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        return (v == null) ? fallback : String.valueOf(v);
    }

    private static String getNullableString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v == null) ? null : String.valueOf(v);
    }

    private static int getInt(Map<String, Object> m, String key, int fallback) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double getDouble(Map<String, Object> m, String key, double fallback) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static Double getNullableDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static boolean getBoolean(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Map<?, ?> map) ? (Map<String, Object>) map : null;
    }

    private static List<?> getList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof List<?> list) ? list : List.of();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String raw, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(enumClass, raw.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
