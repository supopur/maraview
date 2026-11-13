package cz.maramat.scada.model;

import cz.maramat.scada.tags.ScadaTag;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root Maraview SCADA project (.msl) containing screens, tags, communication settings, and optional .mfl link.
 */
public class ScadaProject {
    public static final String VERSION = "1.0";

    private String version = VERSION;
    private String projectName = "UntitledScada";
    private String author = "";
    private String description = "";
    private String linkedMflPath = "";
    private CommSettings commSettings = new CommSettings();
    private final List<ScadaScreen> screens = new ArrayList<>();
    private final Map<String, ScadaTag> tags = new LinkedHashMap<>();

    private transient File projectFile;
    private transient boolean dirty;

    public ScadaProject() {
        screens.add(new ScadaScreen("Overview"));
    }

    public void putTag(ScadaTag tag) {
        if (tag == null || tag.getName() == null || tag.getName().isBlank()) {
            return;
        }
        tags.put(tag.getName(), tag);
    }

    public ScadaTag getTag(String name) {
        if (name == null) return null;
        return tags.get(name);
    }

    public void removeTag(String name) {
        if (name != null) {
            tags.remove(name);
        }
    }

    public ScadaScreen findScreenByName(String name) {
        if (name == null) return null;
        for (ScadaScreen s : screens) {
            if (name.equals(s.getName())) {
                return s;
            }
        }
        return null;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = (version == null || version.isBlank()) ? VERSION : version.trim();
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = (projectName == null || projectName.isBlank()) ? "UntitledScada" : projectName.trim();
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = (author == null) ? "" : author;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = (description == null) ? "" : description;
    }

    public String getLinkedMflPath() {
        return linkedMflPath;
    }

    public void setLinkedMflPath(String linkedMflPath) {
        this.linkedMflPath = (linkedMflPath == null) ? "" : linkedMflPath.trim();
    }

    public CommSettings getCommSettings() {
        return commSettings;
    }

    public void setCommSettings(CommSettings commSettings) {
        this.commSettings = (commSettings == null) ? new CommSettings() : commSettings;
    }

    public List<ScadaScreen> getScreens() {
        return screens;
    }

    public Map<String, ScadaTag> getTags() {
        return tags;
    }

    public File getProjectFile() {
        return projectFile;
    }

    public void setProjectFile(File projectFile) {
        this.projectFile = projectFile;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
