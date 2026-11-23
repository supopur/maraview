package cz.maramat.scada.tags;

import cz.maramat.scada.model.CommSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MaraProt v1 TCP client implementing {@link PlcTagEndpoint} and runtime control
 * against either a running MaraFlow Simulator or a physical F407PLC over TCP/IP.
 */
public class MaraflowSimClient implements PlcTagEndpoint, AutoCloseable {

    public static final int MAGIC_0 = 0x4D;
    public static final int MAGIC_1 = 0x50;
    public static final int VERSION_1 = 0x10;
    public static final int FLAG_ENCRYPTED = 0x04;
    public static final int HEADER_BYTES = 10;
    public static final int CRC_BYTES = 2;

    public static final int OP_PING = 0x01;
    public static final int OP_GET_INFO = 0x02;
    public static final int OP_AUTH_CHALLENGE = 0x03;
    public static final int OP_AUTH_VERIFY = 0x04;
    public static final int OP_VM_STATUS = 0x10;
    public static final int OP_VM_CONTROL = 0x11;
    public static final int OP_OPERAND_READ_BATCH = 0x60;
    public static final int OP_OPERAND_WRITE_BATCH = 0x61;

    public static final int CTRL_STOP = 0;
    public static final int CTRL_START = 1;
    public static final int CTRL_PAUSE = 2;
    public static final int CTRL_ESTOP_RESET = 5;

    public record SimStatus(
            boolean online,
            String simState,
            boolean eStopActive,
            double speedMultiplier,
            String projectName,
            String errorMessage
    ) {
        public static SimStatus offline(String errorMessage) {
            return new SimStatus(false, "OFFLINE", false, 1.0, "", errorMessage == null ? "" : errorMessage);
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 500;
    private static final int SO_TIMEOUT_MS = 1000;
    private static final long RECONNECT_COOLDOWN_MS = 1000L;

    private final Object ioLock = new Object();
    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    private volatile String host = "127.0.0.1";
    private volatile int port = CommSettings.DEFAULT_MARAFLOW_SIM_PORT;
    private volatile int stationId = 1;
    private volatile String password = "admin";
    private volatile boolean connected = false;
    private volatile long lastConnectAttemptMs = 0L;
    private volatile SimStatus lastStatus = SimStatus.offline("Not connected");

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int nextSeqId = 1;
    private byte[] sessionKey;
    private boolean encryptEnabled = false;

    public MaraflowSimClient() {
    }

    public MaraflowSimClient(String host, int port) {
        configure(host, port, 1, "admin");
    }

    public MaraflowSimClient(String host, int port, int stationId, String password) {
        configure(host, port, stationId, password);
    }

    public void configure(String host, int port) {
        configure(host, port, this.stationId, this.password);
    }

    public void configure(String host, int port, int stationId, String password) {
        String normalizedHost = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
        int normalizedPort = (port <= 0 || port > 65535) ? CommSettings.DEFAULT_MARAFLOW_SIM_PORT : port;
        int normalizedStation = Math.max(0, Math.min(254, stationId));
        String normalizedPw = (password == null) ? "" : password;
        synchronized (ioLock) {
            boolean changed = !normalizedHost.equals(this.host)
                    || normalizedPort != this.port
                    || normalizedStation != this.stationId
                    || !normalizedPw.equals(this.password);
            this.host = normalizedHost;
            this.port = normalizedPort;
            this.stationId = normalizedStation;
            this.password = normalizedPw;
            if (changed) {
                closeSocketLocked("Reconfigured");
                this.lastConnectAttemptMs = 0L;
            }
        }
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getStationId() { return stationId; }
    public SimStatus getLastStatus() { return lastStatus; }

    @Override
    public boolean isOnline() {
        return connected && lastStatus.online();
    }

    public void disconnect() {
        synchronized (ioLock) {
            closeSocketLocked("Disconnected");
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    private void ensureConnectedLocked() throws IOException {
        if (connected && socket != null && !socket.isClosed()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastConnectAttemptMs < RECONNECT_COOLDOWN_MS) {
            throw new IOException(lastStatus.errorMessage().isBlank() ? "Reconnect cooldown" : lastStatus.errorMessage());
        }
        lastConnectAttemptMs = now;

        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            s.setSoTimeout(SO_TIMEOUT_MS);
            this.socket = s;
            this.in = s.getInputStream();
            this.out = s.getOutputStream();
            this.sessionKey = null;
            this.encryptEnabled = false;
            this.connected = true;
            authenticateLocked();
        } catch (IOException ex) {
            try { s.close(); } catch (IOException ignored) {}
            this.socket = null;
            this.in = null;
            this.out = null;
            this.connected = false;
            this.lastStatus = SimStatus.offline(ex.getMessage());
            throw ex;
        }
    }

    private void authenticateLocked() throws IOException {
        byte[] chal = transceiveLocked(OP_AUTH_CHALLENGE, new byte[0], false);
        if (chal.length < 17) {
            throw new IOException("Invalid AUTH_CHALLENGE response");
        }
        boolean authRequired = chal[0] != 0;
        byte[] challenge = new byte[16];
        System.arraycopy(chal, 1, challenge, 0, 16);
        if (!authRequired) {
            this.sessionKey = null;
            this.encryptEnabled = false;
            return;
        }
        byte[] pwBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] key = sha256Two(pwBytes, challenge);
        byte[] proof = sha256Two(key, challenge);
        byte[] verify = transceiveLocked(OP_AUTH_VERIFY, proof, false);
        if (verify.length < 1 || verify[0] == 0) {
            throw new IOException("Authentication failed");
        }
        this.sessionKey = key;
        this.encryptEnabled = true;
    }

    private void closeSocketLocked(String reason) {
        connected = false;
        sessionKey = null;
        encryptEnabled = false;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
        socket = null;
        in = null;
        out = null;
        lastStatus = SimStatus.offline(reason);
    }

    public SimStatus refreshStatus() {
        synchronized (ioLock) {
            try {
                ensureConnectedLocked();
                byte[] info = transceiveLocked(OP_GET_INFO, new byte[0], encryptEnabled);
                String modelName = "";
                if (info.length >= 44) {
                    int n = 0;
                    while (n < 24 && info[20 + n] != 0) n++;
                    modelName = new String(info, 20, n, StandardCharsets.US_ASCII);
                }
                byte[] st = transceiveLocked(OP_VM_STATUS, new byte[0], encryptEnabled);
                String stateName = "STOPPED";
                boolean estop = false;
                if (st.length >= 2) {
                    stateName = switch (st[0] & 0xFF) {
                        case 1 -> "RUNNING";
                        case 2 -> "PAUSED";
                        case 3 -> "FAULT";
                        case 4 -> "ESTOP";
                        default -> "STOPPED";
                    };
                    estop = st[1] != 0;
                }
                SimStatus status = new SimStatus(true, stateName, estop, 1.0, modelName, "");
                lastStatus = status;
                return status;
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                return lastStatus;
            }
        }
    }

    public SimStatus sendCommand(String cmd) {
        synchronized (ioLock) {
            try {
                ensureConnectedLocked();
                String upper = cmd == null ? "" : cmd.trim().toUpperCase();
                int action = switch (upper) {
                    case "START" -> CTRL_START;
                    case "PAUSE" -> CTRL_PAUSE;
                    case "ESTOP_RESET" -> CTRL_ESTOP_RESET;
                    default -> CTRL_STOP;
                };
                transceiveLocked(OP_VM_CONTROL, new byte[]{(byte) action}, encryptEnabled);
                return refreshStatus();
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                return lastStatus;
            }
        }
    }

    public boolean startSimulation() { return sendCommand("START").online(); }
    public boolean pauseSimulation() { return sendCommand("PAUSE").online(); }
    public boolean stopSimulation() { return sendCommand("STOP").online(); }

    public Map<String, Double> pollStatusAndOperands(Collection<String> keys) {
        refreshStatus();
        return readOperands(keys);
    }

    @Override
    public double readOperand(String operandOrAddress) {
        if (operandOrAddress == null || operandOrAddress.isBlank()) return 0.0;
        Map<String, Double> m = readOperands(List.of(operandOrAddress));
        return m.getOrDefault(operandOrAddress.trim(), 0.0);
    }

    @Override
    public Map<String, Double> readOperands(Collection<String> operandsOrAddresses) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (operandsOrAddresses == null || operandsOrAddresses.isEmpty()) {
            return result;
        }
        List<String> keys = new ArrayList<>();
        for (String k : operandsOrAddresses) {
            if (k != null && !k.isBlank()) keys.add(k.trim());
        }
        if (keys.isEmpty()) return result;

        synchronized (ioLock) {
            try {
                ensureConnectedLocked();
                if (lastStatus == null || !lastStatus.online()) {
                    refreshStatus();
                }
                int idx = 0;
                while (idx < keys.size()) {
                    ByteArrayOutputStream req = new ByteArrayOutputStream();
                    int batchStart = idx;
                    int batchCount = 0;
                    req.write(0);
                    req.write(0);
                    while (idx < keys.size()) {
                        byte[] nameBytes = keys.get(idx).getBytes(StandardCharsets.UTF_8);
                        if (req.size() + 2 + nameBytes.length > 480 && batchCount > 0) break;
                        req.write(nameBytes.length & 0xFF);
                        req.write((nameBytes.length >>> 8) & 0xFF);
                        req.write(nameBytes, 0, nameBytes.length);
                        batchCount++;
                        idx++;
                    }
                    byte[] reqBytes = req.toByteArray();
                    reqBytes[0] = (byte) (batchCount & 0xFF);
                    reqBytes[1] = (byte) ((batchCount >>> 8) & 0xFF);
                    byte[] resp = transceiveLocked(OP_OPERAND_READ_BATCH, reqBytes, encryptEnabled);
                    if (resp.length >= 2) {
                        ByteBuffer bb = ByteBuffer.wrap(resp).order(ByteOrder.LITTLE_ENDIAN);
                        int respCount = bb.getShort() & 0xFFFF;
                        for (int i = 0; i < respCount && bb.remaining() >= 8 && (batchStart + i) < keys.size(); i++) {
                            double val = bb.getDouble();
                            String key = keys.get(batchStart + i);
                            result.put(key, val);
                            cache.put(key, val);
                        }
                    }
                }
                return result;
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                for (String key : keys) {
                    Double cached = cache.get(key);
                    if (cached != null) result.put(key, cached);
                }
                return result;
            }
        }
    }

    @Override
    public void writeOperand(String operandOrAddress, double value) {
        if (operandOrAddress == null || operandOrAddress.isBlank()) return;
        String key = operandOrAddress.trim();
        cache.put(key, value);
        synchronized (ioLock) {
            try {
                ensureConnectedLocked();
                byte[] nameBytes = key.getBytes(StandardCharsets.UTF_8);
                ByteBuffer bb = ByteBuffer.allocate(2 + 2 + nameBytes.length + 8).order(ByteOrder.LITTLE_ENDIAN);
                bb.putShort((short) 1);
                bb.putShort((short) nameBytes.length);
                bb.put(nameBytes);
                bb.putDouble(value);
                transceiveLocked(OP_OPERAND_WRITE_BATCH, bb.array(), encryptEnabled);
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
            }
        }
    }

    public static SimStatus testConnection(String host, int port) {
        return testConnection(host, port, 1, "admin");
    }

    public static SimStatus testConnection(String host, int port, int stationId, String password) {
        try (MaraflowSimClient probe = new MaraflowSimClient(host, port, stationId, password)) {
            return probe.refreshStatus();
        }
    }

    private byte[] transceiveLocked(int opcode, byte[] payload, boolean useEncrypt) throws IOException {
        int seqId = nextSeqId++;
        if (nextSeqId > 0xFFFF) nextSeqId = 1;
        byte[] wirePayload = payload;
        int verFlags = VERSION_1;
        if (useEncrypt && sessionKey != null) {
            wirePayload = (payload == null) ? new byte[0] : payload.clone();
            if (wirePayload.length > 0) {
                cryptPayload(sessionKey, seqId, 0x01, wirePayload);
            }
            verFlags |= FLAG_ENCRYPTED;
        }
        byte[] frame = encodeFrame(stationId, seqId, verFlags, opcode, wirePayload);
        out.write(frame);
        out.flush();

        byte[] hdr = new byte[HEADER_BYTES];
        int state = 0;
        while (state < 2) {
            int b = in.read();
            if (b < 0) throw new IOException("Connection closed by server");
            if (state == 0 && b == MAGIC_0) {
                hdr[0] = (byte) b;
                state = 1;
            } else if (state == 1 && b == MAGIC_1) {
                hdr[1] = (byte) b;
                state = 2;
            } else {
                state = (b == MAGIC_0) ? 1 : 0;
            }
        }
        readExact(in, hdr, 2, HEADER_BYTES - 2);
        int rxStatus = hdr[7] & 0xFF;
        int rxLen = (hdr[8] & 0xFF) | ((hdr[9] & 0xFF) << 8);
        if (rxLen > 1024) throw new IOException("Invalid payload length: " + rxLen);

        byte[] fullFrame = new byte[HEADER_BYTES + rxLen + CRC_BYTES];
        System.arraycopy(hdr, 0, fullFrame, 0, HEADER_BYTES);
        readExact(in, fullFrame, HEADER_BYTES, rxLen + CRC_BYTES);

        int crcCalc = calcCrc16(fullFrame, HEADER_BYTES + rxLen);
        int crcRx = (fullFrame[HEADER_BYTES + rxLen] & 0xFF) | ((fullFrame[HEADER_BYTES + rxLen + 1] & 0xFF) << 8);
        if (crcCalc != crcRx) throw new IOException("CRC mismatch on MaraProt response");
        if (rxStatus != 0) {
            throw new IOException("MaraProt error status=" + rxStatus + " (op=0x" + Integer.toHexString(opcode) + ")");
        }

        byte[] respPayload = new byte[rxLen];
        if (rxLen > 0) System.arraycopy(fullFrame, HEADER_BYTES, respPayload, 0, rxLen);
        int rxVerFlags = fullFrame[2] & 0xFF;
        if ((rxVerFlags & FLAG_ENCRYPTED) != 0 && sessionKey != null && rxLen > 0) {
            int rxSeq = (fullFrame[4] & 0xFF) | ((fullFrame[5] & 0xFF) << 8);
            cryptPayload(sessionKey, rxSeq, 0x02, respPayload);
        }
        return respPayload;
    }

    private static void readExact(InputStream in, byte[] buf, int off, int len) throws IOException {
        int read = 0;
        while (read < len) {
            int n = in.read(buf, off + read, len - read);
            if (n < 0) throw new IOException("Connection closed");
            read += n;
        }
    }

    private static byte[] encodeFrame(int stationId, int seqId, int verFlags, int opcode, byte[] payload) {
        int pLen = (payload != null) ? payload.length : 0;
        byte[] frame = new byte[HEADER_BYTES + pLen + CRC_BYTES];
        frame[0] = (byte) MAGIC_0;
        frame[1] = (byte) MAGIC_1;
        frame[2] = (byte) (verFlags & 0xFF);
        frame[3] = (byte) (stationId & 0xFF);
        frame[4] = (byte) (seqId & 0xFF);
        frame[5] = (byte) ((seqId >>> 8) & 0xFF);
        frame[6] = (byte) (opcode & 0xFF);
        frame[7] = 0;
        frame[8] = (byte) (pLen & 0xFF);
        frame[9] = (byte) ((pLen >>> 8) & 0xFF);
        if (pLen > 0) {
            System.arraycopy(payload, 0, frame, HEADER_BYTES, pLen);
        }
        int crc = calcCrc16(frame, HEADER_BYTES + pLen);
        frame[HEADER_BYTES + pLen] = (byte) (crc & 0xFF);
        frame[HEADER_BYTES + pLen + 1] = (byte) ((crc >>> 8) & 0xFF);
        return frame;
    }

    private static int calcCrc16(byte[] data, int len) {
        int crc = 0xFFFF;
        for (int i = 0; i < len; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                else crc = (crc << 1) & 0xFFFF;
            }
        }
        return crc;
    }

    private static byte[] sha256Two(byte[] a, byte[] b) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (a != null && a.length > 0) md.update(a);
            if (b != null && b.length > 0) md.update(b);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    private static void cryptPayload(byte[] key, int seqId, int dir, byte[] data) throws IOException {
        byte[] iv = new byte[]{(byte) (seqId & 0xFF), (byte) ((seqId >>> 8) & 0xFF), (byte) (dir & 0xFF), 0};
        int offset = 0;
        int blk = 0;
        while (offset < data.length) {
            iv[3] = (byte) (blk++ & 0xFF);
            byte[] stream = sha256Two(key, iv);
            for (int i = 0; i < 32 && offset < data.length; i++, offset++) {
                data[offset] ^= stream[i];
            }
        }
    }
}
