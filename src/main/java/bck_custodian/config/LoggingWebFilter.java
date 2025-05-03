package bck_custodian.config;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class LoggingWebFilter implements WebFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // Buffer and log request body
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(buffer -> {
                    byte[] bodyBytes = new byte[buffer.readableByteCount()];
                    buffer.read(bodyBytes);
                    DataBufferUtils.release(buffer);

                    String bodyString = new String(bodyBytes, StandardCharsets.UTF_8);
                    logRequest(exchange, bodyString);

                    // Decorate the request with the cached body
                    ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(bodyBytes));
                        }
                    };

                    // Decorate the response to capture and log the body
                    ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
                        @Override
                        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                            if (body instanceof Flux) {
                                Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                                return super.writeWith(fluxBody
                                        .buffer() // collect all buffers
                                        .map(dataBuffers -> {
                                            // Join all the DataBuffers
                                            DataBuffer joined = exchange.getResponse().bufferFactory().join(dataBuffers);
                                            byte[] content = new byte[joined.readableByteCount()];
                                            joined.read(content);
                                            DataBufferUtils.release(joined);

                                            String responseBody = new String(content, StandardCharsets.UTF_8);
                                            logResponse(this.getStatusCode(), responseBody);
                                            return exchange.getResponse().bufferFactory().wrap(content);
                                        }));
                            }
                            return super.writeWith(body);
                        }

                        @Override
                        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                            return writeWith(Flux.from(body).flatMapSequential(p -> p));
                        }
                    };

                    // Continue the chain with modified request and response
                    return chain.filter(exchange.mutate()
                            .request(decoratedRequest)
                            .response(decoratedResponse)
                            .build());
                });
    }

    private void logRequest(ServerWebExchange exchange, String body) {
        var request = exchange.getRequest();
        LOGGER.info("REQUEST: method={}, path={}, headers={}, body={}",
                request.getMethod(),
                request.getURI().getPath(),
                request.getHeaders(),
                body);
    }

    private void logResponse(HttpStatusCode status, String body) {
        LOGGER.info("RESPONSE: status={}, body={}", status, body);
    }
}
