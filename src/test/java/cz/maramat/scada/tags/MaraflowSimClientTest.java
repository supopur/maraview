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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
}
