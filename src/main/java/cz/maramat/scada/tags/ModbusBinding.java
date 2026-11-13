package cz.maramat.scada.tags;

/**
 * Modbus RTU / TCP register binding parameters for standalone SCADA mode.
 */
public class ModbusBinding {
    private int slaveId = 1;
    private int functionCode = 3;
    private int address = 0;
    private double scale = 1.0;
    private double offset = 0.0;

    public ModbusBinding() {
    }

    public ModbusBinding(int slaveId, int functionCode, int address, double scale, double offset) {
        this.slaveId = slaveId;
        this.functionCode = functionCode;
        this.address = address;
        this.scale = scale;
        this.offset = offset;
    }

    public ModbusBinding copy() {
        return new ModbusBinding(slaveId, functionCode, address, scale, offset);
    }

    public int getSlaveId() {
        return slaveId;
    }

    public void setSlaveId(int slaveId) {
        this.slaveId = Math.max(0, Math.min(255, slaveId));
    }

    public int getFunctionCode() {
        return functionCode;
    }

    public void setFunctionCode(int functionCode) {
        this.functionCode = functionCode;
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = Math.max(0, address);
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = (scale == 0.0) ? 1.0 : scale;
    }

    public double getOffset() {
        return offset;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }
}
