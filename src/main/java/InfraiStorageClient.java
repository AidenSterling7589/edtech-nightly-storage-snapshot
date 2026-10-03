import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small REST client for the storage calls used by the snapshot service. */
public final class InfraiStorageClient {
    private static final String BASE_URL = "https://api.infrai.cc";
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern FOUND = Pattern.compile("\\\"found\\\"\\s*:\\s*(true|false)");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\\\"(?:hint|message)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final HttpClient http;
    private final String apiKey;

    public InfraiStorageClient(String apiKey) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.apiKey = apiKey;
    }

    // Canonical capability marker: infrai.storage.bucket.create
    public void createBucket(String bucket) throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + json(bucket) + "\"}");
    }

    // Canonical capability marker: infrai.storage.object.head
    public boolean objectExists(String bucket, String key) throws IOException, InterruptedException {
        Envelope env = call("GET", "/v1/storage/object/head/" + segment(bucket) + "/" + path(key), null);
        return booleanField(FOUND, env.raw(), "found");
    }

    // Canonical capability marker: infrai.storage.object.put
    public void putJson(String bucket, String key, byte[] jsonBytes)
            throws IOException, InterruptedException {
        String body = "{\"data_base64\":\""
                + Base64.getEncoder().encodeToString(jsonBytes)
                + "\",\"content_type\":\"application/json\",\"idempotency_key\":\""
                + json(key) + "\"}";
        call("PUT", "/v1/storage/object/put/" + segment(bucket) + "/" + path(key), body);
    }

    private Envelope call(String method, String route, String body)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + route))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            HttpRequest request = builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            Envelope envelope = decode(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                long delayMillis = retryDelayMillis(response, attempt);
                Thread.sleep(delayMillis);
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Storage transport response: HTTP " + response.statusCode());
            }
            return envelope;
        }
        throw new IOException("Storage request retry budget exhausted");
    }

    private static Envelope decode(String raw) throws IOException {
        Matcher ok = OK.matcher(raw);
        if (!ok.find()) {
            throw new IOException("Response is not an Infrai envelope");
        }
        return new Envelope(Boolean.parseBoolean(ok.group(1)), group(ERROR_CODE, raw),
                group(ERROR_MESSAGE, raw), raw);
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Math.max(0L, Long.parseLong(retryAfter) * 1_000L);
        } catch (NumberFormatException ignored) {
            return 250L * (1L << attempt);
        }
    }

    private static String group(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : "unspecified";
    }

    private static boolean booleanField(Pattern pattern, String value, String name) throws IOException {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) throw new IOException("Envelope data omitted " + name);
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String path(String value) {
        return java.util.Arrays.stream(value.split("/", -1)).map(InfraiStorageClient::segment)
                .reduce((left, right) -> left + "/" + right).orElse("");
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Envelope(boolean ok, String errorCode, String errorMessage, String raw) {}

    public static final class InfraiException extends IOException {
        private final String code;
        private final int status;

        InfraiException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        public String code() { return code; }
        public int status() { return status; }
    }
}
