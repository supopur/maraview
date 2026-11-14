package cz.maramat.scada.tags;

import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.ScadaProject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaraflowSimClientTest {

    @Test
    void clientMonitorsAndControlsSimulatorOverTcp() throws Exception {
        Map<String, Double> mem = new ConcurrentHashMap<>();
        mem.put("DI0", 0.0);
        mem.put("DO0", 1.0);
        mem.put("TankLevel", 64.5);
        AtomicReference<String> state = new AtomicReference<>("STOPPED");
        AtomicBoolean estop = new AtomicBoolean(false);
        AtomicBoolean running = new AtomicBoolean(true);

        try (ServerSocket ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            int port = ss.getLocalPort();
            Thread serverThread = Thread.ofVirtual().start(() -> {
                while (running.get() && !ss.isClosed()) {
                    try (Socket s = ss.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                         PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            String[] parts = line.trim().split("\\s+");
                            String cmd = parts[0];
                            switch (cmd) {
                                case "PING" -> out.println("OK PONG");
                                case "STATUS" -> out.println("OK state=" + state.get()
                                        + " estop=" + (estop.get() ? "1" : "0")
                                        + " speed=1.0 project=Test+Plant");
                                case "START" -> {
                                    state.set("RUNNING");
                                    out.println("OK state=RUNNING");
                                }
                                case "PAUSE" -> {
                                    state.set("PAUSED");
                                    out.println("OK state=PAUSED");
                                }
                                case "STOP" -> {
                                    state.set("STOPPED");
                                    out.println("OK state=STOPPED");
                                }
                                case "ESTOP" -> {
                                    estop.set("1".equals(parts[1]));
                                    out.println("OK estop=" + (estop.get() ? "1" : "0"));
                                }
                                case "ESTOP_RESET" -> {
                                    estop.set(false);
                                    out.println("OK estop=0");
                                }
                                case "GET" -> {
                                    String op = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                                    out.println("OK " + mem.getOrDefault(op, 0.0));
                                }
                                case "SET" -> {
                                    String op = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                                    double val = Double.parseDouble(parts[2]);
                                    mem.put(op, val);
                                    out.println("OK");
                                }
                                case "MGET" -> {
                                    StringBuilder sb = new StringBuilder("OK");
                                    for (int i = 1; i < parts.length; i++) {
                                        String op = URLDecoder.decode(parts[i], StandardCharsets.UTF_8);
                                        sb.append(' ').append(mem.getOrDefault(op, 0.0));
                                    }
                                    out.println(sb);
                                }
                                default -> out.println("ERR unknown");
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            });

            try (MaraflowSimClient client = new MaraflowSimClient("127.0.0.1", port)) {
                assertTrue(client.connectNow());
                assertTrue(client.ping());

                MaraflowSimClient.SimStatus st = client.refreshStatus();
                assertTrue(st.online());
                assertEquals("STOPPED", st.simState());
                assertEquals("Test Plant", st.projectName());

                assertTrue(client.startSimulation());
                assertEquals("RUNNING", client.getLastStatus().simState());

                ScadaProject project = new ScadaProject();
                ScadaTag di0 = new ScadaTag("StartBtn", DataType.BOOL, TagSourceType.MFL_LINK);
                di0.setAddressOrRef("DI0");
                ScadaTag do0 = new ScadaTag("PumpRun", DataType.BOOL, TagSourceType.MFL_LINK);
                do0.setAddressOrRef("DO0");
                ScadaTag lvl = new ScadaTag("TankLevel", DataType.FLOAT, TagSourceType.MFL_LINK);
                lvl.setAddressOrRef("TankLevel");
                project.putTag(di0);
                project.putTag(do0);
                project.putTag(lvl);

                MaraflowBridgeTagProvider provider = new MaraflowBridgeTagProvider(client);
                provider.syncAll(project);
                assertEquals(0.0, di0.getRuntimeValue());
                assertEquals(1.0, do0.getRuntimeValue());
                assertEquals(64.5, lvl.getRuntimeValue());
                assertEquals(TagQuality.GOOD, do0.getRuntimeQuality());

                provider.writeTag(project, "StartBtn", 1.0);
                assertEquals(1.0, mem.get("DI0"));

                assertTrue(client.pauseSimulation());
                assertEquals("PAUSED", client.getLastStatus().simState());

                assertTrue(client.stopSimulation());
                assertEquals("STOPPED", client.getLastStatus().simState());
            } finally {
                running.set(false);
                serverThread.interrupt();
            }
        }

        MaraflowSimClient.SimStatus offlineProbe = MaraflowSimClient.testConnection("127.0.0.1", 1);
        assertFalse(offlineProbe.online());
    }

    @Test
    void pollStatusAndOperandsUsesDeltaProtocolAndFallsBackOnErr() throws Exception {
        // 1. Server supporting SUB + POLL
        AtomicInteger subCount = new AtomicInteger(0);
        AtomicInteger pollCount = new AtomicInteger(0);
        AtomicBoolean runDeltaServer = new AtomicBoolean(true);
        try (ServerSocket ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            int port = ss.getLocalPort();
            Thread t = Thread.ofVirtual().start(() -> {
                while (runDeltaServer.get() && !ss.isClosed()) {
                    try (Socket s = ss.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                         PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.startsWith("SUB")) {
                                subCount.incrementAndGet();
                                out.println("OK 2");
                            } else if ("POLL".equals(line.trim())) {
                                int n = pollCount.incrementAndGet();
                                if (n == 1) {
                                    out.println("OK state=RUNNING estop=0 speed=1.0 project=Plant | 0=1.0 1=22.5");
                                } else if (n == 2) {
                                    out.println("OK state=RUNNING estop=0 speed=1.0 project=Plant |");
                                } else {
                                    out.println("OK state=RUNNING estop=0 speed=1.0 project=Plant | 1=23.5");
                                }
                            } else {
                                out.println("ERR Unexpected");
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            });

            try (MaraflowSimClient client = new MaraflowSimClient("127.0.0.1", port)) {
                Map<String, Double> r1 = client.pollStatusAndOperands(List.of("Run", "Temp"));
                assertEquals(1.0, r1.get("Run"));
                assertEquals(22.5, r1.get("Temp"));
                assertEquals("RUNNING", client.getLastStatus().simState());

                // Empty delta keeps full cached view without re-subscribing
                Map<String, Double> r2 = client.pollStatusAndOperands(List.of("Run", "Temp"));
                assertEquals(1.0, r2.get("Run"));
                assertEquals(22.5, r2.get("Temp"));

                // Partial delta updates only key 1
                Map<String, Double> r3 = client.pollStatusAndOperands(List.of("Run", "Temp"));
                assertEquals(1.0, r3.get("Run"));
                assertEquals(23.5, r3.get("Temp"));
                assertEquals(1, subCount.get(), "SUB must only be sent once when key list is unchanged");
                assertEquals(3, pollCount.get());
            } finally {
                runDeltaServer.set(false);
                t.interrupt();
            }
        }

        // 2. Legacy server without SUB/POLL -> automatic fallback to STATUS + MGET
        AtomicBoolean runLegacyServer = new AtomicBoolean(true);
        try (ServerSocket ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            int port = ss.getLocalPort();
            Thread t = Thread.ofVirtual().start(() -> {
                while (runLegacyServer.get() && !ss.isClosed()) {
                    try (Socket s = ss.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                         PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.startsWith("SUB") || line.startsWith("POLL")) {
                                out.println("ERR Unknown command");
                            } else if ("STATUS".equals(line.trim())) {
                                out.println("OK state=PAUSED estop=0 speed=1.0 project=Legacy");
                            } else if (line.startsWith("MGET")) {
                                out.println("OK 0.0 41.2");
                            } else {
                                out.println("ERR");
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            });

            try (MaraflowSimClient client = new MaraflowSimClient("127.0.0.1", port)) {
                Map<String, Double> res = client.pollStatusAndOperands(List.of("A", "B"));
                assertEquals(0.0, res.get("A"));
                assertEquals(41.2, res.get("B"));
                assertEquals("PAUSED", client.getLastStatus().simState());
            } finally {
                runLegacyServer.set(false);
                t.interrupt();
            }
        }
    }
}
