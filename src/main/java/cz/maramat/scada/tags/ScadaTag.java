package cz.maramat.scada.tags;

import cz.maramat.scada.model.DataType;

/**
 * Named SCADA tag with data type, source binding, alarm thresholds, and live runtime value.
 */
public class ScadaTag {
    private String name;
    private DataType dataType = DataType.BOOL;
    private TagSourceType sourceType = TagSourceType.INTERNAL;
    private MflOrigin mflOrigin = MflOrigin.NONE;
    private String addressOrRef = "";
    private String initialValue = "0";
    private String description = "";
    private String engineeringUnits = "";
    private double minValue = 0.0;
    private double maxValue = 100.0;
    private AlarmConfig alarmConfig = new AlarmConfig();
    private ModbusBinding modbusBinding;

    private transient volatile double runtimeValue = 0.0;
    private transient volatile TagQuality runtimeQuality = TagQuality.GOOD;
    private transient volatile long lastUpdatedMs = 0L;

    public ScadaTag() {
        this.name = "Tag1";
    }

    public ScadaTag(String name, DataType dataType, TagSourceType sourceType) {
        this.name = (name == null || name.isBlank()) ? "Tag1" : name.trim();
        this.dataType = (dataType == null) ? DataType.BOOL : dataType;
        this.sourceType = (sourceType == null) ? TagSourceType.INTERNAL : sourceType;
        this.initialValue = this.dataType.getDefaultConstant();
        this.runtimeValue = this.dataType.toNumericValue(this.initialValue);
    }

    public ScadaTag copy() {
        ScadaTag t = new ScadaTag(name, dataType, sourceType);
        t.mflOrigin = this.mflOrigin;
        t.addressOrRef = this.addressOrRef;
        t.initialValue = this.initialValue;
        t.description = this.description;
        t.engineeringUnits = this.engineeringUnits;
        t.minValue = this.minValue;
        t.maxValue = this.maxValue;
        t.alarmConfig = (this.alarmConfig != null) ? this.alarmConfig.copy() : new AlarmConfig();
        t.modbusBinding = (this.modbusBinding != null) ? this.modbusBinding.copy() : null;
        t.runtimeValue = this.runtimeValue;
        t.runtimeQuality = this.runtimeQuality;
        t.lastUpdatedMs = this.lastUpdatedMs;
        return t;
    }

    public void resetRuntimeToInitial() {
        this.runtimeValue = dataType.toNumericValue(initialValue);
        this.runtimeQuality = TagQuality.GOOD;
        this.lastUpdatedMs = System.currentTimeMillis();
    }

    public boolean asBoolean() {
        return runtimeValue != 0.0 && !Double.isNaN(runtimeValue);
    }

    public String getFormattedValue(boolean compact) {
        return dataType.formatRuntimeValue(runtimeValue, compact);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Tag1" : name.trim();
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = (dataType == null) ? DataType.BOOL : dataType;
    }

    public TagSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(TagSourceType sourceType) {
        this.sourceType = (sourceType == null) ? TagSourceType.INTERNAL : sourceType;
    }

    public MflOrigin getMflOrigin() {
        return mflOrigin;
    }

    public void setMflOrigin(MflOrigin mflOrigin) {
        this.mflOrigin = (mflOrigin == null) ? MflOrigin.NONE : mflOrigin;
    }

    public String getAddressOrRef() {
        return addressOrRef;
    }

    public void setAddressOrRef(String addressOrRef) {
        this.addressOrRef = (addressOrRef == null) ? "" : addressOrRef.trim();
    }

    public String getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(String initialValue) {
        this.initialValue = (initialValue == null || initialValue.isBlank())
                ? dataType.getDefaultConstant()
                : initialValue.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = (description == null) ? "" : description.trim();
    }

    public String getEngineeringUnits() {
        return engineeringUnits;
    }

    public void setEngineeringUnits(String engineeringUnits) {
        this.engineeringUnits = (engineeringUnits == null) ? "" : engineeringUnits.trim();
    }

    public double getMinValue() {
        return minValue;
    }

    public void setMinValue(double minValue) {
        this.minValue = minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }

    public AlarmConfig getAlarmConfig() {
        return alarmConfig;
    }

    public void setAlarmConfig(AlarmConfig alarmConfig) {
        this.alarmConfig = (alarmConfig == null) ? new AlarmConfig() : alarmConfig;
    }

    public ModbusBinding getModbusBinding() {
        return modbusBinding;
    }

    public void setModbusBinding(ModbusBinding modbusBinding) {
        this.modbusBinding = modbusBinding;
    }

    public double getRuntimeValue() {
        return runtimeValue;
    }

    public void setRuntimeValue(double runtimeValue) {
        this.runtimeValue = DataType.convertValue(runtimeValue, DataType.FLOAT, this.dataType);
        this.lastUpdatedMs = System.currentTimeMillis();
    }

    public TagQuality getRuntimeQuality() {
        return runtimeQuality;
    }

    public void setRuntimeQuality(TagQuality runtimeQuality) {
        this.runtimeQuality = (runtimeQuality == null) ? TagQuality.GOOD : runtimeQuality;
    }

    public long getLastUpdatedMs() {
        return lastUpdatedMs;
    }
}
