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
    private final List<String> subscribedKeys = new ArrayList<>();
    private boolean subscriptionActive = false;
    private boolean pollDeltaSupported = true;

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
                return refreshStatusLocked();
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                return lastStatus;
            }
        }
    }

    private SimStatus refreshStatusLocked() throws IOException {
        String resp = sendCommandLocked("STATUS");
        SimStatus parsed = parseStatusResponse(resp);
        this.lastStatus = parsed;
        return parsed;
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
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                Double cached = cache.get(key);
                return cached != null ? cached : 0.0;
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
            Double cached = cache.get(key);
            return cached != null ? cached : 0.0;
        }
    }

    @Override
    public Map<String, Double> readOperands(Collection<String> operandsOrAddresses) {
        List<String> keys = normalizeKeys(operandsOrAddresses);
        if (keys.isEmpty()) {
            return new LinkedHashMap<>();
        }
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return new LinkedHashMap<>();
            }
            try {
                return readOperandsLocked(keys);
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                return new LinkedHashMap<>();
            }
        }
    }

    /**
     * Fetches simulation status and all subscribed operand values using the server's
     * {@code SUB}/{@code POLL} delta protocol when available (one TCP round-trip per poll),
     * falling back transparently to {@code STATUS} + {@code MGET} against older servers.
     */
    public Map<String, Double> pollStatusAndOperands(Collection<String> operandsOrAddresses) {
        List<String> keys = normalizeKeys(operandsOrAddresses);
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return Map.of();
            }
            try {
                if (pollDeltaSupported) {
                    if (!subscriptionActive || !subscribedKeys.equals(keys)) {
                        StringBuilder subCmd = new StringBuilder("SUB");
                        for (String k : keys) {
                            subCmd.append(' ').append(encode(k));
                        }
                        String subResp = sendCommandLocked(subCmd.toString());
                        if (subResp != null && subResp.startsWith("OK")) {
                            subscribedKeys.clear();
                            subscribedKeys.addAll(keys);
                            subscriptionActive = true;
                        } else {
                            pollDeltaSupported = false;
                            subscriptionActive = false;
                            subscribedKeys.clear();
                        }
                    }
                    if (pollDeltaSupported && subscriptionActive) {
                        String pollResp = sendCommandLocked("POLL");
                        if (pollResp != null && pollResp.startsWith("OK")) {
                            return applyPollResponseLocked(pollResp, keys);
                        }
                        pollDeltaSupported = false;
                        subscriptionActive = false;
                        subscribedKeys.clear();
                    }
                }
                refreshStatusLocked();
                if (keys.isEmpty()) {
                    return Map.of();
                }
                return readOperandsLocked(keys);
            } catch (IOException ex) {
                closeSocketLocked(ex.getMessage());
                return Map.of();
            }
        }
    }

    private static List<String> normalizeKeys(Collection<String> operandsOrAddresses) {
        if (operandsOrAddresses == null || operandsOrAddresses.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(operandsOrAddresses.size());
        for (String op : operandsOrAddresses) {
            if (op != null && !op.isBlank()) {
                keys.add(op.trim());
            }
        }
        return keys;
    }

    private Map<String, Double> readOperandsLocked(List<String> keys) throws IOException {
        Map<String, Double> result = new LinkedHashMap<>();
        StringBuilder cmd = new StringBuilder("MGET");
        for (String k : keys) {
            cmd.append(' ').append(encode(k));
        }
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
        return result;
    }

    private Map<String, Double> applyPollResponseLocked(String resp, List<String> keys) {
        String[] tokens = resp.split("\\s+");
        int pipeIdx = -1;
        for (int i = 1; i < tokens.length; i++) {
            if ("|".equals(tokens[i])) {
                pipeIdx = i;
                break;
            }
        }
        int statusEnd = (pipeIdx >= 0) ? pipeIdx : tokens.length;
        this.lastStatus = parseStatusTokens(tokens, 1, statusEnd);
        if (pipeIdx >= 0) {
            for (int i = pipeIdx + 1; i < tokens.length; i++) {
                String tok = tokens[i];
                int eq = tok.indexOf('=');
                if (eq <= 0 || eq >= tok.length() - 1) continue;
                try {
                    int idx = Integer.parseInt(tok.substring(0, eq));
                    if (idx < 0 || idx >= subscribedKeys.size()) continue;
                    double val = Double.parseDouble(tok.substring(eq + 1));
                    cache.put(subscribedKeys.get(idx), val);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Map<String, Double> result = new LinkedHashMap<>(keys.size() * 2);
        for (String k : keys) {
            Double val = cache.get(k);
            if (val != null) {
                result.put(k, val);
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
        synchronized (ioLock) {
            if (!ensureConnectedLocked()) {
                return;
            }
            try {
                String resp = sendCommandLocked("SET " + encode(key) + " " + value);
                if (resp != null && resp.startsWith("OK")) {
                    cache.put(key, value);
                }
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
        this.subscriptionActive = false;
        this.pollDeltaSupported = true;
        this.subscribedKeys.clear();
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
        String[] tokens = resp.split("\\s+");
        return parseStatusTokens(tokens, 1, tokens.length);
    }

    private static SimStatus parseStatusTokens(String[] tokens, int startIdx, int endExclusive) {
        String state = "UNKNOWN";
        boolean estop = false;
        double speed = 1.0;
        String projectName = "";
        for (int i = startIdx; i < endExclusive; i++) {
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
