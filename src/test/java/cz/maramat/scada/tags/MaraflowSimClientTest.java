package cz.maramat.scada.tags;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaraflowSimClientTest {

    @Test
    void connectsAuthenticatesReadsWritesAndControlsOverMaraProt() throws Exception {
        Map<String, Double> tags = new ConcurrentHashMap<>();
        tags.put("DI0", 1.0);
        tags.put("TankLevel", 42.5);
        AtomicBoolean running = new AtomicBoolean(true);

        try (ServerSocket ss = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))) {
            int port = ss.getLocalPort();
            Thread serverThread = Thread.ofVirtual().start(() -> {
                try (Socket s = ss.accept();
                     InputStream in = s.getInputStream();
                     OutputStream out = s.getOutputStream()) {
                    while (running.get()) {
                        byte[] hdr = in.readNBytes(10);
                        if (hdr.length < 10) break;
                        int seq = (hdr[4] & 0xFF) | ((hdr[5] & 0xFF) << 8);
                        int op = hdr[6] & 0xFF;
                        int len = (hdr[8] & 0xFF) | ((hdr[9] & 0xFF) << 8);
                        byte[] payload = in.readNBytes(len);
                        in.readNBytes(2); // crc

                        byte[] resp = switch (op) {
                            case MaraflowSimClient.OP_AUTH_CHALLENGE -> new byte[17]; // authRequired=0
                            case MaraflowSimClient.OP_GET_INFO -> new byte[44];
                            case MaraflowSimClient.OP_VM_STATUS -> new byte[32];
                            case MaraflowSimClient.OP_VM_CONTROL -> new byte[]{1, 0};
                            case MaraflowSimClient.OP_OPERAND_WRITE_BATCH -> {
                                ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                                bb.getShort(); // count
                                int nLen = bb.getShort() & 0xFFFF;
                                byte[] nb = new byte[nLen];
                                bb.get(nb);
                                tags.put(new String(nb, StandardCharsets.UTF_8), bb.getDouble());
                                yield new byte[]{1, 0};
                            }
                            case MaraflowSimClient.OP_OPERAND_READ_BATCH -> {
                                ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                                int count = bb.getShort() & 0xFFFF;
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                bos.write(count & 0xFF);
                                bos.write((count >>> 8) & 0xFF);
                                for (int i = 0; i < count; i++) {
                                    int nLen = bb.getShort() & 0xFFFF;
                                    byte[] nb = new byte[nLen];
                                    bb.get(nb);
                                    double v = tags.getOrDefault(new String(nb, StandardCharsets.UTF_8), 0.0);
                                    byte[] vb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(v).array();
                                    bos.write(vb, 0, 8);
                                }
                                yield bos.toByteArray();
                            }
                            default -> new byte[0];
                        };
                        out.write(buildResp(seq, op, resp));
                        out.flush();
                    }
                } catch (IOException ignored) {
                }
            });

            try (MaraflowSimClient client = new MaraflowSimClient("127.0.0.1", port, 1, "admin")) {
                MaraflowSimClient.SimStatus st = client.refreshStatus();
                assertTrue(st.online());

                Map<String, Double> read = client.readOperands(List.of("DI0", "TankLevel"));
                assertEquals(1.0, read.get("DI0"));
                assertEquals(42.5, read.get("TankLevel"));

                client.writeOperand("TankLevel", 88.0);
                assertEquals(88.0, tags.get("TankLevel"));
            } finally {
                running.set(false);
                serverThread.interrupt();
            }
        }

        MaraflowSimClient.SimStatus offline = MaraflowSimClient.testConnection("127.0.0.1", 1, 1, "admin");
        assertFalse(offline.online());
    }

    private static byte[] buildResp(int seq, int op, byte[] payload) {
        byte[] f = new byte[12 + payload.length];
        f[0] = 0x4D;
        f[1] = 0x50;
        f[2] = 0x11;
        f[3] = 1;
        f[4] = (byte) (seq & 0xFF);
        f[5] = (byte) ((seq >>> 8) & 0xFF);
        f[6] = (byte) op;
        f[7] = 0;
        f[8] = (byte) (payload.length & 0xFF);
        f[9] = (byte) ((payload.length >>> 8) & 0xFF);
        System.arraycopy(payload, 0, f, 10, payload.length);
        int crc = 0xFFFF;
        for (int i = 0; i < 10 + payload.length; i++) {
            crc ^= (f[i] & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
            }
        }
        f[10 + payload.length] = (byte) (crc & 0xFF);
        f[11 + payload.length] = (byte) ((crc >>> 8) & 0xFF);
        return f;
    }
}
