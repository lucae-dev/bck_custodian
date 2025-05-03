package bck_custodian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final OpenAIService ai;
    private final SimpleRateLimiter limiter;

    @Autowired
    public ChatController(OpenAIService ai, SimpleRateLimiter limiter) {
        this.ai = ai;
        this.limiter = limiter;
    }


    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest req, ServerHttpRequest http) {
        String ip = http.getRemoteAddress() != null
                ? http.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        if (!limiter.isAllowed(ip)) {
            Duration wait = limiter.getTimeUntilReset(ip);
            long mins = wait.toMinutes();
            long secs = wait.minusMinutes(mins).getSeconds();

            String lockedMsg = String.format(
                    "SYSTEM LOCKDOWN: Custodian-1 has detected suspicious activity.\n" +
                            "Security protocols will reset in %d minute(s) and %d second(s).\n" +
                            "Hold your lines until then, intruder.",
                    mins, secs
            );
            log.warn("Rate limit hit for {}: lockout {}s", ip, wait.getSeconds());
            return Flux.just(lockedMsg);
        }

        log.info(">> [{}]: {}", ip, req.getMessage());
        return ai.streamChat(req.getMessage())
                .doOnNext(chunk -> log.info("<< [{}]: {}", ip, chunk.trim()));
    }
}