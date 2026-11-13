package cz.maramat.scada.tags;

import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.ScadaProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Parses a Maraflow PLC project file (.mfl XML Properties) and imports its Symbolic Tags,
 * Virtual Registers, xVar runtime variables, ST variables, and hardware I/O channels as ScadaTags.
 */
public final class MflTagImporter {

    private MflTagImporter() {
    }

    public static Map<String, ScadaTag> parseMflTags(File mflFile) throws IOException {
        if (mflFile == null || !mflFile.isFile()) {
            throw new IOException("MFL file not found: " + mflFile);
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(mflFile)) {
            props.loadFromXML(in);
        }
        return parseMflProperties(props);
    }

    public static Map<String, ScadaTag> parseMflProperties(Properties props) {
        Map<String, ScadaTag> imported = new LinkedHashMap<>();

        // 1. Symbolic Tags (project.tag.<i>.*)
        int tagCount = parseIntSafe(props.getProperty("project.tagCount"), 0);
        for (int ti = 0; ti < tagCount; ti++) {
            String tp = "project.tag." + ti + ".";
            String name = props.getProperty(tp + "name", "").trim();
            if (name.isEmpty()) continue;
            DataType dt = DataType.fromString(props.getProperty(tp + "type"), DataType.BOOL);
            String desc = props.getProperty(tp + "desc", "");

            ScadaTag tag = new ScadaTag(name, dt, TagSourceType.MFL_LINK);
            tag.setMflOrigin(isVirtualRegisterName(name) ? MflOrigin.VIRTUAL_REGISTER : MflOrigin.SYMBOLIC_TAG);
            tag.setAddressOrRef(name);
            tag.setDescription(desc);
            tag.resetRuntimeToInitial();
            imported.put(tag.getName(), tag);
        }

        // 2. Pages: ST Variables, FBD Blocks (xVar, IO.IREF/OREF/PWM/Encoder), and Ladder Elements
        int pageCount = parseIntSafe(props.getProperty("project.pageCount"), 0);
        for (int pi = 0; pi < pageCount; pi++) {
            String pfx = "page." + pi + ".";
            String pageName = props.getProperty(pfx + "name", "Page" + (pi + 1));

            // 2a. ST Variables
            int stVarCount = parseIntSafe(props.getProperty(pfx + "stVarCount"), 0);
            for (int vi = 0; vi < stVarCount; vi++) {
                String vp = pfx + "stVar." + vi + ".";
                String name = props.getProperty(vp + "name", "").trim();
                if (name.isEmpty()) continue;
                String scope = props.getProperty(vp + "scope", "VAR");
                DataType dt = DataType.fromString(props.getProperty(vp + "type"), DataType.INT);
                String init = props.getProperty(vp + "init", dt.getDefaultConstant());
                String desc = props.getProperty(vp + "desc", "");

                String tagName = imported.containsKey(name) ? pageName + "." + name : name;
                ScadaTag tag = new ScadaTag(tagName, dt, TagSourceType.MFL_LINK);
                tag.setMflOrigin(MflOrigin.ST_VARIABLE);
                tag.setAddressOrRef(name);
                tag.setInitialValue(init);
                tag.setDescription(desc.isEmpty() ? scope + " (" + pageName + ")" : desc);
                tag.resetRuntimeToInitial();
                imported.putIfAbsent(tag.getName(), tag);
            }

            // 2b. FBD Blocks
            int blockCount = parseIntSafe(props.getProperty(pfx + "blockCount"), 0);
            for (int bi = 0; bi < blockCount; bi++) {
                extractBlockTags(props, pfx + "block." + bi + ".", pageName, imported);
            }

            // 2c. Ladder Rungs (operands and embedded function blocks)
            int rungCount = parseIntSafe(props.getProperty(pfx + "rungCount"), 0);
            for (int ri = 0; ri < rungCount; ri++) {
                String rp = pfx + "rung." + ri + ".";
                extractLadderList(props, rp + "main.", parseIntSafe(props.getProperty(rp + "mainCount"), 0), pageName, imported);
                extractLadderList(props, rp + "coil.", parseIntSafe(props.getProperty(rp + "coilCount"), 0), pageName, imported);
                int branchCount = parseIntSafe(props.getProperty(rp + "branchCount"), 0);
                for (int bri = 0; bri < branchCount; bri++) {
                    String brp = rp + "branch." + bri + ".";
                    extractLadderList(props, brp + "el.", parseIntSafe(props.getProperty(brp + "elCount"), 0), pageName, imported);
                }
            }
        }

        return imported;
    }

    /**
     * Imports tags from the given .mfl file into the target ScadaProject,
     * preserving existing engineering units, min/max, and alarm configs on matching tag names.
     */
    public static int importIntoProject(File mflFile, ScadaProject project) throws IOException {
        Map<String, ScadaTag> parsed = parseMflTags(mflFile);
        project.setLinkedMflPath(mflFile.getAbsolutePath());

        // Remove stale MFL_LINK tags no longer present in the .mfl file, but preserve user metadata for surviving tags
        Map<String, ScadaTag> preservedMeta = new LinkedHashMap<>();
        project.getTags().entrySet().removeIf(entry -> {
            ScadaTag existing = entry.getValue();
            if (existing.getSourceType() == TagSourceType.MFL_LINK) {
                if (parsed.containsKey(entry.getKey())) {
                    preservedMeta.put(entry.getKey(), existing);
                }
                return true;
            }
            return false;
        });

        for (ScadaTag fresh : parsed.values()) {
            ScadaTag old = preservedMeta.get(fresh.getName());
            if (old != null) {
                fresh.setEngineeringUnits(old.getEngineeringUnits());
                fresh.setMinValue(old.getMinValue());
                fresh.setMaxValue(old.getMaxValue());
                fresh.setAlarmConfig(old.getAlarmConfig().copy());
                if (fresh.getDescription().isEmpty() && !old.getDescription().isEmpty()) {
                    fresh.setDescription(old.getDescription());
                }
            }
            project.putTag(fresh);
        }
        project.setDirty(true);
        return parsed.size();
    }

    private static void extractBlockTags(Properties props, String bp, String pageName, Map<String, ScadaTag> out) {
        String defId = props.getProperty(bp + "defId", "");
        String blockName = props.getProperty(bp + "name", "").trim();
        String desc = props.getProperty(bp + "description", "");
        Map<String, String> customProps = readCustomProps(props, bp);

        if (defId.startsWith("Visual.Vars.")) {
            DataType dt = DataType.fromString(props.getProperty(bp + "out.0.type"), DataType.BOOL);
            String curVal = customProps.getOrDefault("CurrentValue",
                    customProps.getOrDefault("ValA", dt.getDefaultConstant()));
            String name = blockName.isEmpty() ? defId : blockName;
            ScadaTag tag = new ScadaTag(name, dt, TagSourceType.MFL_LINK);
            tag.setMflOrigin(MflOrigin.X_VAR);
            tag.setAddressOrRef(name + ".Y");
            tag.setInitialValue(curVal);
            tag.setDescription(desc.isEmpty() ? "xVar (" + pageName + ")" : desc);
            tag.resetRuntimeToInitial();
            out.putIfAbsent(tag.getName(), tag);
            return;
        }

        if (defId.equals("IO.IREF") || defId.equals("IO.OREF")
                || defId.equals("IO.PWM") || defId.equals("IO.Encoder")) {
            String defaultChannel = switch (defId) {
                case "IO.IREF" -> "DI0";
                case "IO.OREF" -> "DO0";
                case "IO.PWM" -> "PWM0";
                default -> "ENC0";
            };
            String channel = customProps.getOrDefault("Channel", defaultChannel).trim();
            if (!channel.isEmpty()) {
                DataType dt = inferHardwareChannelType(channel, defId);
                ScadaTag tag = new ScadaTag(channel, dt, TagSourceType.MFL_LINK);
                tag.setMflOrigin(MflOrigin.HARDWARE_IO);
                tag.setAddressOrRef(channel);
                tag.setDescription(desc.isEmpty() ? defId + " (" + pageName + ")" : desc);
                tag.resetRuntimeToInitial();
                out.putIfAbsent(tag.getName(), tag);
            }
        }
    }

    private static void extractLadderList(Properties props, String prefix, int count,
                                          String pageName, Map<String, ScadaTag> out) {
        for (int i = 0; i < count; i++) {
            String ep = prefix + i + ".";
            String kind = props.getProperty(ep + "kind", "");
            if ("FUNCTION_BLOCK".equals(kind)) {
                extractBlockTags(props, ep + "fb.", pageName, out);
            }
            String operand = props.getProperty(ep + "operand", "").trim();
            if (!operand.isEmpty() && isVirtualRegisterName(operand) && !out.containsKey(operand)) {
                ScadaTag tag = new ScadaTag(operand, DataType.INT, TagSourceType.MFL_LINK);
                tag.setMflOrigin(MflOrigin.VIRTUAL_REGISTER);
                tag.setAddressOrRef(operand);
                tag.setDescription("Virtual Register (" + pageName + ")");
                tag.resetRuntimeToInitial();
                out.put(tag.getName(), tag);
            }
            String srcOperand = props.getProperty(ep + "srcOperand", "").trim();
            if (!srcOperand.isEmpty() && isVirtualRegisterName(srcOperand) && !out.containsKey(srcOperand)) {
                ScadaTag tag = new ScadaTag(srcOperand, DataType.INT, TagSourceType.MFL_LINK);
                tag.setMflOrigin(MflOrigin.VIRTUAL_REGISTER);
                tag.setAddressOrRef(srcOperand);
                tag.setDescription("Virtual Register (" + pageName + ")");
                tag.resetRuntimeToInitial();
                out.put(tag.getName(), tag);
            }
        }
    }

    private static Map<String, String> readCustomProps(Properties props, String bp) {
        Map<String, String> map = new LinkedHashMap<>();
        int propCount = parseIntSafe(props.getProperty(bp + "propCount"), 0);
        for (int i = 0; i < propCount; i++) {
            String k = props.getProperty(bp + "prop." + i + ".k");
            String v = props.getProperty(bp + "prop." + i + ".v", "");
            if (k != null && !k.isBlank()) {
                map.put(k, v);
            }
        }
        return map;
    }

    private static DataType inferHardwareChannelType(String channel, String defId) {
        String upper = channel.toUpperCase(Locale.ROOT);
        if (upper.startsWith("AI") || upper.startsWith("AO")) {
            return DataType.INT;
        }
        if (upper.startsWith("PWM") || defId.equals("IO.PWM")) {
            return DataType.FLOAT;
        }
        if (upper.startsWith("ENC") || defId.equals("IO.Encoder")) {
            return DataType.LONG;
        }
        return DataType.BOOL;
    }

    private static boolean isVirtualRegisterName(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("VR") || upper.startsWith("VREG") || upper.startsWith("REG");
    }

    private static int parseIntSafe(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
