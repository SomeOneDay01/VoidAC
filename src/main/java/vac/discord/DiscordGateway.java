package vac.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vac.VAC;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordGateway extends Thread {

    private static final String GATEWAY_URL = "gateway.discord.gg";
    private static final int GATEWAY_PORT = 443;
    private static final String GATEWAY_PATH = "/?v=9&encoding=json";
    private static final String API_BASE = "https://discord.com/api/v9";

    private final VAC plugin;
    private final String token;
    private final DiscordBot bot;
    private final AtomicBoolean running;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int lastSeq;
    private int heartbeatInterval;
    private volatile boolean shouldReconnect;

    public DiscordGateway(VAC plugin, String token, DiscordBot bot) {
        super("VAC-Discord-Gateway");
        this.plugin = plugin;
        this.token = token;
        this.bot = bot;
        this.running = new AtomicBoolean(true);
        this.shouldReconnect = true;
    }

    @Override
    public void run() {
        while (running.get() && shouldReconnect) {
            try {
                connect();
            } catch (Exception e) {
                if (running.get()) {
                    plugin.getLogger().warning("Discord Gateway connection failed: " + e.getMessage());
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void connect() throws Exception {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        socket = factory.createSocket(GATEWAY_URL, GATEWAY_PORT);
        in = socket.getInputStream();
        out = socket.getOutputStream();

        byte[] key = new byte[16];
        new Random().nextBytes(key);
        String secKey = Base64.getEncoder().encodeToString(key);

        String upgrade = "GET " + GATEWAY_PATH + " HTTP/1.1\r\n"
                + "Host: " + GATEWAY_URL + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + secKey + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n";
        out.write(upgrade.getBytes(StandardCharsets.UTF_8));
        out.flush();

        String response = readHttpResponse(in);
        if (!response.contains("101")) {
            throw new IOException("WebSocket upgrade failed: " + response);
        }

        // Read Hello frame (OP 10)
        readFrame(); // Hello with heartbeat interval

        // Send Identify
        JsonObject identify = new JsonObject();
        identify.addProperty("op", 2);
        JsonObject data = new JsonObject();
        data.addProperty("token", token);
        data.addProperty("intents", 512); // GUILD_MESSAGES only
        JsonObject props = new JsonObject();
        props.addProperty("os", "windows");
        props.addProperty("browser", "java");
        props.addProperty("device", "vac");
        data.add("properties", props);
        identify.add("d", data);
        sendFrame(identify.toString());

        // Main event loop
        long lastHeartbeat = System.currentTimeMillis();
        while (running.get() && socket.isConnected()) {
            try {
                if (heartbeatInterval > 0 && System.currentTimeMillis() - lastHeartbeat > heartbeatInterval) {
                    sendHeartbeat();
                    lastHeartbeat = System.currentTimeMillis();
                }

                if (in.available() > 0) {
                    JsonObject frame = readFrame();
                    if (frame != null) {
                        handleFrame(frame);
                    }
                } else {
                    Thread.sleep(10);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Discord Gateway disconnected: " + e.getMessage());
                break;
            }
        }

        close();
    }

    private void sendHeartbeat() throws IOException {
        JsonObject hb = new JsonObject();
        hb.addProperty("op", 1);
        hb.add("d", lastSeq > 0 ? new com.google.gson.JsonPrimitive(lastSeq) : com.google.gson.JsonNull.INSTANCE);
        sendFrame(hb.toString());
    }

    private void handleFrame(JsonObject frame) {
        int op = frame.get("op").getAsInt();
        if (frame.has("s") && !frame.get("s").isJsonNull()) {
            lastSeq = frame.get("s").getAsInt();
        }
        JsonObject d = frame.has("d") && !frame.get("d").isJsonNull() ? frame.getAsJsonObject("d") : null;

        switch (op) {
            case 0: // Dispatch
                String t = frame.get("t").getAsString();
                if ("INTERACTION_CREATE".equals(t)) {
                    bot.handleInteraction(d);
                } else if ("READY".equals(t)) {
                    plugin.getLogger().info("Discord Bot connected as " +
                            d.getAsJsonObject("user").get("username").getAsString());
                    bot.setReady(true);
                }
                break;
            case 9: // Invalid Session
                plugin.getLogger().warning("Discord: Invalid session, reconnecting...");
                shouldReconnect = true;
                running.set(false);
                break;
            case 10: // Hello
                if (d != null) heartbeatInterval = d.get("heartbeat_interval").getAsInt();
                break;
            case 11: // Heartbeat ACK
                break;
        }
    }

    private String readHttpResponse(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            String s = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            if (s.endsWith("\r\n\r\n")) return s;
        }
        throw new IOException("Unexpected EOF in HTTP response");
    }

    private JsonObject readFrame() throws IOException {
        int b1 = in.read();
        if (b1 == -1) throw new IOException("WebSocket closed");
        int b2 = in.read();
        if (b2 == -1) throw new IOException("WebSocket closed");

        int opcode = b1 & 0x0F;
        boolean masked = (b2 & 0x80) != 0;
        long len = b2 & 0x7F;

        if (len == 126) {
            len = (in.read() << 8) | in.read();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | in.read();
            }
        }

        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            in.read(maskKey);
        }

        byte[] payload = new byte[(int) len];
        int read = 0;
        while (read < len) {
            int r = in.read(payload, read, (int) (len - read));
            if (r == -1) throw new IOException("Unexpected EOF");
            read += r;
        }

        if (masked && maskKey != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i & 3];
            }
        }

        switch (opcode) {
            case 0x1: // Text
                String json = new String(payload, StandardCharsets.UTF_8);
                return JsonParser.parseString(json).getAsJsonObject();
            case 0x8: // Close
                running.set(false);
                break;
            case 0x9: // Ping
                sendFrameRaw(0xA, payload);
                break;
        }
        return null;
    }

    private void sendFrame(String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        sendFrameRaw(0x1, data);
    }

    private void sendFrameRaw(int opcode, byte[] data) throws IOException {
        byte[] header;
        byte maskKey[] = new byte[4];
        new Random().nextBytes(maskKey);
        byte[] masked = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            masked[i] = (byte) (data[i] ^ maskKey[i & 3]);
        }

        if (data.length < 126) {
            header = new byte[]{ (byte) (0x80 | opcode), (byte) (0x80 | data.length) };
        } else if (data.length < 65536) {
            header = new byte[]{ (byte) (0x80 | opcode), (byte) (0x80 | 126),
                    (byte) (data.length >> 8), (byte) data.length };
        } else {
            header = new byte[]{ (byte) (0x80 | opcode), (byte) (0x80 | 127),
                    0, 0, 0, 0, 0, 0, 0, 0 };
            for (int i = 0; i < 8; i++) {
                header[8 - i + 1] = (byte) (data.length >> (i * 8));
            }
        }

        synchronized (out) {
            out.write(header);
            out.write(maskKey);
            out.write(masked);
            out.flush();
        }
    }

    public void sendMessage(String channelId, JsonObject message) {
        try {
            String json = message.toString();
            String response = httpRequest("POST", "/channels/" + channelId + "/messages", json);
        } catch (Exception e) {
            plugin.getLogger().warning("Discord send message failed: " + e.getMessage());
        }
    }

    public void editMessage(String channelId, String messageId, JsonObject message) {
        try {
            String json = message.toString();
            httpRequest("PATCH", "/channels/" + channelId + "/messages/" + messageId, json);
        } catch (Exception e) {
            plugin.getLogger().warning("Discord edit message failed: " + e.getMessage());
        }
    }

    public void respondToInteraction(String interactionId, String interactionToken, JsonObject response) {
        try {
            String json = response.toString();
            httpRequest("POST", "/interactions/" + interactionId + "/" + interactionToken + "/callback", json);
        } catch (Exception e) {
            plugin.getLogger().warning("Discord interaction response failed: " + e.getMessage());
        }
    }

    private String httpRequest(String method, String path, String body) throws Exception {
        java.net.URL url = new java.net.URL(API_BASE + path);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bot " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "DiscordBot (VAC, 1.0.0)");
        conn.setDoOutput(body != null);

        if (body != null && !body.isEmpty()) {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    public void shutdown() {
        running.set(false);
        shouldReconnect = false;
        close();
    }

    private void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }
}
