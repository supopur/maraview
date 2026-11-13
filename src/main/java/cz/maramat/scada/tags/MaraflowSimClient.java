package cz.maramat.scada.tags;

import cz.maramat.scada.model.CommSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP client implementing {@link PlcTagEndpoint} and simulation control
 * against a running Maraflow {@code SimulatorBridgeServer}.
 */
public class MaraflowSimClient implements PlcTagEndpoint, AutoCloseable {

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

    private static final int CONNECT_TIMEOUT_MS = 350;
    private static final int SO_TIMEOUT_MS = 800;
    private static final long RECONNECT_COOLDOWN_MS = 1000L;

    private final Object ioLock = new Object();
    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    private volatile String host = "127.0.0.1";
    private volatile int port = CommSettings.DEFAULT_MARAFLOW_SIM_PORT;
    private volatile boolean connected = false;
    private volatile long lastConnectAttemptMs = 0L;
    private volatile SimStatus lastStatus = SimStatus.offline("Not connected");

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public MaraflowSimClient() {
    }

    public MaraflowSimClient(String host, int port) {
        configure(host, port);
    }

    public void configure(String host, int port) {
        String normalizedHost = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
        int normalizedPort = (port <= 0 || port > 65535) ? CommSettings.DEFAULT_MARAFLOW_SIM_PORT : port;
        synchronized (ioLock) {
            boolean changed = !normalizedHost.equals(this.host) || normalizedPort != this.port;
            this.host = normalizedHost;
            this.port = normalizedPort;
            if (changed) {
                closeSocketLocked("Reconfigured");
                this.lastConnectAttemptMs = 0L;
            }
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public SimStatus getLastStatus() {
        return lastStatus;
    }

    public void disconnect() {
        synchronized (ioLock) {
            closeSocketLocked("Disconnected");
            cache.clear();
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    @Override
    public boolean isOnline() {
        return connected;
    }

    @Override
    public String getStatusLabel() {
        SimStatus st = lastStatus;
        if (!connected || !st.online()) {
            return "OFFLINE";
        }
        return "ONLINE (" + st.simState() + ")";
    }

    public boolean connectNow() {
        synchronized (ioLock) {
            lastConnectAttemptMs = 0L;
            return ensureConnectedLocked();
        }
    }

    public SimStatus refreshStatus() {
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return lastStatus;
            }
            try {
                String resp = sendCommandLocked("STATUS");
                SimStatus parsed = parseStatusResponse(resp);
                this.lastStatus = parsed;
                return parsed;
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                return lastStatus;
            }
        }
    }

    public boolean ping() {
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return false;
            }
            try {
                String resp = sendCommandLocked("PING");
                return resp != null && resp.startsWith("OK");
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                return false;
            }
        }
    }

    public boolean startSimulation() {
        return sendSimpleStateCommand("START");
    }

    public boolean pauseSimulation() {
        return sendSimpleStateCommand("PAUSE");
    }

    public boolean stopSimulation() {
        return sendSimpleStateCommand("STOP");
    }

    public boolean setEStop(boolean pressed) {
        return sendSimpleStateCommand("ESTOP " + (pressed ? "1" : "0"));
    }

    public boolean resetEStop() {
        return sendSimpleStateCommand("ESTOP_RESET");
    }

    public boolean setSpeed(double multiplier) {
        return sendSimpleStateCommand("SPEED " + multiplier);
    }

    @Override
    public double readOperand(String operandOrAddress) {
        if (operandOrAddress == null || operandOrAddress.isBlank()) {
            return 0.0;
        }
        String key = operandOrAddress.trim();
        Double cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return 0.0;
            }
            try {
                String resp = sendCommandLocked("GET " + encode(key));
                if (resp != null && resp.startsWith("OK ")) {
                    double val = Double.parseDouble(resp.substring(3).trim());
                    cache.put(key, val);
                    return val;
                }
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
            } catch (NumberFormatException ignored) {
            }
            return 0.0;
        }
    }

    @Override
    public Map<String, Double> readOperands(Collection<String> operandsOrAddresses) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (operandsOrAddresses == null || operandsOrAddresses.isEmpty()) {
            return result;
        }
        List<String> keys = new ArrayList<>(operandsOrAddresses.size());
        for (String op : operandsOrAddresses) {
            if (op != null && !op.isBlank()) {
                keys.add(op.trim());
            }
        }
        if (keys.isEmpty()) {
            return result;
        }

        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return result;
            }
            StringBuilder cmd = new StringBuilder("MGET");
            for (String k : keys) {
                cmd.append(' ').append(encode(k));
            }
            try {
                String resp = sendCommandLocked(cmd.toString());
                if (resp != null && resp.startsWith("OK")) {
                    String[] parts = resp.split("\\s+");
                    for (int i = 0; i < keys.size() && (i + 1) < parts.length; i++) {
                        try {
                            double val = Double.parseDouble(parts[i + 1]);
                            String k = keys.get(i);
                            cache.put(k, val);
                            result.put(k, val);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public void writeOperand(String operandOrAddress, double value) {
        if (operandOrAddress == null || operandOrAddress.isBlank()) {
            return;
        }
        String key = operandOrAddress.trim();
        cache.put(key, value);
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return;
            }
            try {
                sendCommandLocked("SET " + encode(key) + " " + value);
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
            }
        }
    }

    public static SimStatus testConnection(String host, int port) {
        try (MaraflowSimClient probe = new MaraflowSimClient(host, port)) {
            if (!probe.connectNow()) {
                return probe.getLastStatus();
            }
            return probe.refreshStatus();
        }
    }

    private boolean sendSimpleStateCommand(String cmd) {
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return false;
            }
            try {
                String resp = sendCommandLocked(cmd);
                if (resp != null && resp.startsWith("OK")) {
                    String statusResp = sendCommandLocked("STATUS");
                    this.lastStatus = parseStatusResponse(statusResp);
                    return true;
                }
                return false;
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                return false;
            }
        }
    }

    private boolean ensureConnectedLocked() {
        if (connected && socket != null && !socket.isClosed()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastConnectAttemptMs < RECONNECT_COOLDOWN_MS) {
            return false;
        }
        lastConnectAttemptMs = now;
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(SO_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            this.socket = s;
            this.in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            this.connected = true;
            this.lastStatus = new SimStatus(true, "CONNECTED", false, 1.0, "", "");
            return true;
        } catch (IOException ex) {
            closeSocketLocked(ex.getMessage());
            return false;
        }
    }

    private String sendCommandLocked(String command) throws IOException {
        if (out == null || in == null) {
            throw new IOException("Not connected");
        }
        out.println(command);
        if (out.checkError()) {
            throw new IOException("Write failed");
        }
        String line = in.readLine();
        if (line == null) {
            throw new IOException("Connection closed by Maraflow");
        }
        return line;
    }

    private void closeSocketLocked(String reason) {
        this.connected = false;
        this.lastStatus = SimStatus.offline(reason);
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        this.socket = null;
        this.in = null;
        this.out = null;
    }

    private static SimStatus parseStatusResponse(String resp) {
        if (resp == null || !resp.startsWith("OK")) {
            return SimStatus.offline(resp == null ? "Empty response" : resp);
        }
        String state = "UNKNOWN";
        boolean estop = false;
        double speed = 1.0;
        String projectName = "";
        String[] tokens = resp.split("\\s+");
        for (int i = 1; i < tokens.length; i++) {
            int eq = tokens[i].indexOf('=');
            if (eq <= 0) continue;
            String k = tokens[i].substring(0, eq);
            String v = tokens[i].substring(eq + 1);
            switch (k) {
                case "state" -> state = v;
                case "estop" -> estop = "1".equals(v) || "true".equalsIgnoreCase(v);
                case "speed" -> {
                    try {
                        speed = Double.parseDouble(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
                case "project" -> projectName = URLDecoder.decode(v, StandardCharsets.UTF_8);
                default -> {
                }
            }
        }
        return new SimStatus(true, state, estop, speed, projectName, "");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
