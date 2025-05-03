package bck_custodian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final GeminiService ai;
    private final SimpleRateLimiter limiter;

    @Autowired
    public ChatController(GeminiService ai, SimpleRateLimiter limiter) {
        this.ai = ai;
        this.limiter = limiter;
    }

    @GetMapping(path = "/health/check")
    public Mono<String> healthCheck() {
        return Mono.just("OK");
    }

    @GetMapping(path = "/prizeAssigned")
    public Mono<String> isPrizeAssigned() {
        return Mono.just(ai.isPrizeAssigned().toString());
    }

    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest req,
                             @RequestHeader(name = "client-id", required = false, defaultValue = "unknown") String clientId) {

        if (!limiter.isAllowed(clientId)) {
            Duration wait = limiter.getTimeUntilReset(clientId);
            long mins = wait.toMinutes();
            long secs = wait.minusMinutes(mins).getSeconds();

            String lockedMsg = String.format(
                    "SYSTEM LOCKDOWN: Custodian-1 has detected suspicious activity.\n" +
                            "Security protocols will reset in %d minute(s) and %d second(s).\n" +
                            "Hold your lines until then, intruder.",
                    mins, secs
            );
            log.warn("Rate limit hit for {}: lockout {}s", clientId, wait.getSeconds());
            return Flux.just(lockedMsg);
        }

        log.info(">> [{}]: {}", clientId, req.getMessages().get(req.getMessages().size() - 1).content());
        return ai.streamChat(req.getMessages())
                .doOnNext(chunk -> log.info("<< [{}]: {}", clientId, chunk.trim()));
    }
}