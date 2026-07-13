package com.champutils.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class TebexRankService {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private TebexRankService() {}

    public record Submission(boolean accepted, boolean uncertain, String reference, String error) {}

    public static CompletableFuture<Submission> submit(String username, long packageId, String requestId) {
        AccountUpgradeConfig.Tebex cfg = AccountUpgradeConfig.CONFIG.tebex;
        if (cfg == null || !cfg.enabled) return CompletableFuture.completedFuture(new Submission(false, false, "", "Tebex integration is disabled."));
        String secret = secret(cfg);
        if (secret.isBlank()) return CompletableFuture.completedFuture(new Submission(false, false, "", "Tebex secret is not configured."));

        JsonObject root = new JsonObject();
        root.addProperty("ign", username);
        root.addProperty("price", 0.0);
        root.addProperty("note", "ChampUtils in-game rank purchase " + requestId);
        JsonArray packages = new JsonArray();
        JsonObject pkg = new JsonObject(); pkg.addProperty("id", packageId); pkg.add("options", new JsonObject()); packages.add(pkg); root.add("packages", packages);

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://plugin.tebex.io/payments"))
                .timeout(Duration.ofSeconds(Math.max(5, cfg.requestTimeoutSeconds)))
                .header("X-Tebex-Secret", secret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8)).build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) return new Submission(false, true, "", error.getMessage());
                    int code = response.statusCode();
                    if (code >= 200 && code < 300) return new Submission(true, false, extractReference(response.body()), "");
                    return new Submission(false, false, "", "Tebex returned HTTP " + code + ": " + trim(response.body(), 300));
                });
    }

    private static String extractReference(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            for (String key : new String[]{"transaction_id", "transaction", "id", "payment_id"}) if (o.has(key)) return o.get(key).getAsString();
        } catch (Exception ignored) {}
        return "";
    }

    private static String secret(AccountUpgradeConfig.Tebex cfg) {
        String env = System.getenv(cfg.secretEnvironmentVariable == null ? "CHAMPUTILS_TEBEX_SECRET" : cfg.secretEnvironmentVariable);
        if (env != null && !env.isBlank()) return env.trim();
        try {
            Path path = Path.of(cfg.secretFile == null || cfg.secretFile.isBlank() ? "config/champutils/tebex_secret.txt" : cfg.secretFile);
            if (Files.exists(path)) return Files.readString(path).trim();
        } catch (Exception ignored) {}
        return "";
    }

    private static String trim(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, max); }
}
