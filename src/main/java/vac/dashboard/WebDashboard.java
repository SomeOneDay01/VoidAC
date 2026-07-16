package vac.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import vac.VAC;
import vac.models.PlayerData;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class WebDashboard {

    private final VAC plugin;
    private final Gson gson;
    private HttpServer server;
    private boolean enabled;

    public WebDashboard(VAC plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void start() {
        int port = plugin.getConfigManager().getDashboardPort();
        if (port <= 0) {
            plugin.getLogger().info("Web Dashboard disabled (port <= 0).");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/api/player/", new PlayerDetailHandler());
            server.createContext("/api/bans", new BansHandler());
            server.createContext("/", new StaticHandler());
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            enabled = true;
            plugin.getLogger().info("Web Dashboard started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start Web Dashboard: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    private String getAuthToken() {
        return plugin.getConfigManager().getDashboardPassword();
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String token = getAuthToken();
        if (token == null || token.isEmpty()) return true;
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return false;
        Map<String, String> params = parseQuery(query);
        return token.equals(params.get("token"));
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        try {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    map.put(URLDecoder.decode(kv[0], "UTF-8"),
                            URLDecoder.decode(kv[1], "UTF-8"));
                }
            }
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported
        }
        return map;
    }

    private void sendJson(HttpExchange exchange, int code, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        sendJson(exchange, code, error);
    }

    class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendError(exchange, 401, "Unauthorized"); return; }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("server_name", Bukkit.getName());
            stats.put("online_players", Bukkit.getOnlinePlayers().size());
            stats.put("max_players", Bukkit.getMaxPlayers());
            stats.put("tracked_players", plugin.getPlayerDataManager().getTrackedPlayerCount());
            stats.put("total_bans", plugin.getPlayerDataManager().getTotalBans());
            stats.put("version", plugin.getDescription().getVersion());
            stats.put("tps", Arrays.stream(Bukkit.getTPS()).mapToObj(v -> String.format("%.2f", v)).collect(Collectors.toList()));

            List<Map<String, Object>> topConfidence = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData d = plugin.getPlayerDataManager().get(p);
                if (d != null && d.getConfidence() > 0) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", p.getName());
                    entry.put("confidence", d.getConfidence());
                    entry.put("violations", d.getTotalViolations());
                    topConfidence.add(entry);
                }
            }
            topConfidence.sort((a, b) -> Double.compare((double) b.get("confidence"), (double) a.get("confidence")));
            stats.put("top_confidence", topConfidence.size() > 10 ? topConfidence.subList(0, 10) : topConfidence);

            sendJson(exchange, 200, stats);
        }
    }

    class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendError(exchange, 401, "Unauthorized"); return; }

            List<Map<String, Object>> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData d = plugin.getPlayerDataManager().get(p);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", p.getName());
                entry.put("uuid", p.getUniqueId().toString());
                entry.put("confidence", d != null ? d.getConfidence() : 0);
                entry.put("violations", d != null ? d.getTotalViolations() : 0);
                entry.put("ping", p.spigot().getPing());
                entry.put("health", (int) p.getHealth());
                players.add(entry);
            }

            sendJson(exchange, 200, players);
        }
    }

    class PlayerDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendError(exchange, 401, "Unauthorized"); return; }

            String path = exchange.getRequestURI().getPath();
            String playerName = path.substring("/api/player/".length());
            if (playerName.isEmpty()) { sendError(exchange, 400, "Player name required"); return; }

            Player player = Bukkit.getPlayerExact(playerName);
            if (player == null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
                if (!offline.hasPlayedBefore()) {
                    sendError(exchange, 404, "Player not found");
                    return;
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("name", offline.getName());
                data.put("uuid", offline.getUniqueId().toString());
                data.put("online", false);
                data.put("first_played", offline.getFirstPlayed());
                data.put("last_played", offline.getLastPlayed());
                sendJson(exchange, 200, data);
                return;
            }

            PlayerData pd = plugin.getPlayerDataManager().getOrCreate(player);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", player.getName());
            data.put("uuid", player.getUniqueId().toString());
            data.put("online", true);
            data.put("confidence", pd.getConfidence());
            data.put("violations", pd.getTotalViolations());
            data.put("violations_detail", pd.getViolations());
            data.put("ping", player.spigot().getPing());
            data.put("health", (int) player.getHealth());
            data.put("food", player.getFoodLevel());
            data.put("location", player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ());
            data.put("world", player.getWorld().getName());

            if (plugin.getKillAuraAnalyzer() != null) {
                data.put("cps", plugin.getKillAuraAnalyzer().getCurrentCPS(player));
                data.put("reach", plugin.getKillAuraAnalyzer().getAverageReach(player));
            }

            sendJson(exchange, 200, data);
        }
    }

    class BansHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendError(exchange, 401, "Unauthorized"); return; }
            Map<String, Object> bans = new LinkedHashMap<>();
            bans.put("total", plugin.getPlayerDataManager().getTotalBans());
            sendJson(exchange, 200, bans);
        }
    }

    class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/dashboard") || path.equals("/dashboard/")) {
                path = "/dashboard/index.html";
            }

            String resourcePath = "dashboard" + path;
            InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (in == null) {
                String html = "<html><body><h2>VAC Dashboard</h2><p>File not found: " + path +
                        "</p><p><a href='/dashboard/index.html?token=" +
                        (getAuthToken() != null ? getAuthToken() : "") + "'>Dashboard</a></p></body></html>";
                sendHtml(exchange, html);
                return;
            }

            String content = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            if (path.endsWith(".css")) exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
            else if (path.endsWith(".js")) exchange.getResponseHeaders().set("Content-Type", "application/javascript; charset=UTF-8");
            else if (path.endsWith(".html")) exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            else if (path.endsWith(".png")) exchange.getResponseHeaders().set("Content-Type", "image/png");
            else if (path.endsWith(".json")) exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        }
    }
}
