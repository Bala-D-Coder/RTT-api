package spikes.gemini;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spike 0a: Gemini Live as a transcriber over raw WebSocket in Java 21.
 * Target: wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent
 * 
 * Exit criteria:
 * 1. Code-switched words arrive in serverContent deltas.
 * 2. First-delta latency is measured and recorded.
 * 3. GEMINI_API_KEY unset exits with clean error (not hung socket).
 */
public class GeminiLiveJavaSpike {

    private static final String MODEL_NAME = "models/gemini-3.5-transcribe-live";
    private static final int CHUNK_BYTES = 3200; // 100 ms of 16 kHz 16-bit mono PCM
    private static final int SAMPLE_RATE = 16000;

    public static void main(String[] args) {
        System.out.println("=== Gemini Live Java Spike (0a) ===");
        System.out.println("Target: JDK java.net.http.WebSocket -> Gemini v1beta BidiGenerateContent");

        // 1. Check GEMINI_API_KEY
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            // Check args or local .env file as fallback
            if (args.length > 0 && !args[0].isBlank()) {
                apiKey = args[0];
            } else {
                apiKey = loadFromDotEnv();
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[CLEAN_EXIT_CHECK] GEMINI_API_KEY is not set.");
            System.err.println("[SUCCESS] Spike 0a exit criterion #4 verified: fails as clean error, not a hung socket.");
            System.err.println("To run full live streaming test, export GEMINI_API_KEY or provide key as first argument.");
            System.exit(1);
        }

        // 2. Locate PCM fixture
        Path pcmPath = Paths.get("spikes", "fixtures", "hinglish_30s.pcm");
        if (!Files.exists(pcmPath)) {
            pcmPath = Paths.get("..", "fixtures", "hinglish_30s.pcm");
        }
        if (!Files.exists(pcmPath)) {
            pcmPath = Paths.get("RTT-api", "spikes", "fixtures", "hinglish_30s.pcm");
        }

        if (!Files.exists(pcmPath)) {
            System.err.println("[ERROR] Audio fixture not found at " + pcmPath.toAbsolutePath());
            System.exit(2);
        }

        System.out.println("Audio fixture: " + pcmPath.toAbsolutePath());
        System.out.println("Model: " + MODEL_NAME);

        try {
            byte[] pcmData = Files.readAllBytes(pcmPath);
            double durationSec = (double) pcmData.length / (SAMPLE_RATE * 2);
            System.out.printf("Fixture duration: %.2f seconds (%d bytes)%n", durationSec, pcmData.length);

            runStreamingSpike(apiKey, pcmData);
        } catch (Exception e) {
            System.err.println("[ERROR] Exception during spike execution: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }

    private static void runStreamingSpike(String apiKey, byte[] pcmData) throws Exception {
        String wsUri = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + apiKey;

        CountDownLatch completionLatch = new CountDownLatch(1);
        Instant startTime = Instant.now();
        Instant[] firstDeltaTime = new Instant[1];
        int[] deltaCount = new int[1];

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        System.out.println("Opening WebSocket connection...");
        CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                .buildAsync(URI.create(wsUri), new WebSocket.Listener() {
                    private final StringBuilder messageBuffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("[WS] Connected to Gemini Live socket.");
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messageBuffer.append(data);
                        if (last) {
                            String message = messageBuffer.toString();
                            messageBuffer.setLength(0);
                            handleServerMessage(message, startTime, firstDeltaTime, deltaCount);
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        System.out.printf("[WS] Socket closed by server: %d - %s%n", statusCode, reason);
                        completionLatch.countDown();
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.err.println("[WS] Error: " + error.getMessage());
                        completionLatch.countDown();
                    }
                });

        WebSocket webSocket = wsFuture.get(10, TimeUnit.SECONDS);

        // Send Setup Frame
        String setupPayload = """
            {
              "setup": {
                "model": "%s",
                "generationConfig": {
                  "responseModalities": ["TEXT"]
                }
              }
            }
            """.formatted(MODEL_NAME);

        System.out.println("[WS] Sending setup frame...");
        webSocket.sendText(setupPayload, true).get(5, TimeUnit.SECONDS);

        // Stream PCM Audio chunks in 100 ms intervals (simulating real-time mic)
        System.out.println("[WS] Streaming audio chunks (100 ms pacing)...");
        int offset = 0;
        int chunkIndex = 0;
        while (offset < pcmData.length) {
            int len = Math.min(CHUNK_BYTES, pcmData.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(pcmData, offset, chunk, 0, len);
            offset += len;
            chunkIndex++;

            String base64Audio = Base64.getEncoder().encodeToString(chunk);
            String audioPayload = """
                {
                  "realtimeInput": {
                    "mediaChunks": [
                      {
                        "mimeType": "audio/pcm;rate=%d",
                        "data": "%s"
                      }
                    ]
                  }
                }
                """.formatted(SAMPLE_RATE, base64Audio);

            webSocket.sendText(audioPayload, true).get(2, TimeUnit.SECONDS);

            if (chunkIndex % 20 == 0) {
                double sentSec = (double) offset / (SAMPLE_RATE * 2);
                System.out.printf("  [STREAM] Streamed %.1f seconds of audio...%n", sentSec);
            }

            // Real-time pacing: 100 ms per 3200 bytes
            Thread.sleep(100);
        }

        System.out.println("[WS] Audio stream finished. Awaiting final transcripts...");
        // Wait up to 15 seconds for trailing server content
        completionLatch.await(15, TimeUnit.SECONDS);

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Spike completed").get(5, TimeUnit.SECONDS);

        Duration totalTime = Duration.between(startTime, Instant.now());
        System.out.println("\n=== Spike 0a Summary ===");
        System.out.println("Total elapsed time: " + totalTime.toMillis() + " ms");
        System.out.println("Total deltas received: " + deltaCount[0]);
        if (firstDeltaTime[0] != null) {
            long firstDeltaLatency = Duration.between(startTime, firstDeltaTime[0]).toMillis();
            System.out.println("First-delta latency: " + firstDeltaLatency + " ms");
        }
    }

    private static void handleServerMessage(String json, Instant startTime, Instant[] firstDeltaTime, int[] deltaCount) {
        long elapsedMs = Duration.between(startTime, Instant.now()).toMillis();

        // Check for text deltas in serverContent
        Pattern textPattern = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = textPattern.matcher(json);
        while (matcher.find()) {
            String text = matcher.group(1);
            if (firstDeltaTime[0] == null) {
                firstDeltaTime[0] = Instant.now();
                System.out.printf("  [FIRST DELTA @ +%d ms] %s%n", elapsedMs, text);
            } else {
                System.out.printf("  [DELTA @ +%d ms] %s%n", elapsedMs, text);
            }
            deltaCount[0]++;
        }

        // Print error if any
        if (json.contains("\"error\"")) {
            System.err.printf("  [SERVER ERROR @ +%d ms] %s%n", elapsedMs, json);
        }
    }

    private static String loadFromDotEnv() {
        try {
            Path envPath = Paths.get(".env");
            if (!Files.exists(envPath)) {
                envPath = Paths.get("RTT-api", ".env");
            }
            if (Files.exists(envPath)) {
                for (String line : Files.readAllLines(envPath)) {
                    if (line.startsWith("GEMINI_API_KEY=")) {
                        return line.substring("GEMINI_API_KEY=".length()).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
