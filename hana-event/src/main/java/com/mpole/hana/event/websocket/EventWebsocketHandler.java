package com.mpole.hana.event.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventWebsocketHandler implements WebSocketHandler {

    private final WebsocketBroadcastService broadcaster;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Mono<Void> input =  session.receive()
                .doOnSubscribe(s->log.info("Websocket connected. sessionId={}",session.getId()))
                .doOnNext(message -> log.info("Client message: {}", message.getPayloadAsText()))
                .doOnComplete(()-> log.info("Websocket disconnected. sessionId={}",session.getId()))
                .doOnError(e->log.info("Websocket error {}",e.getMessage()))
                .then();
//                .map(message->{
//                   return session.textMessage("hello");
//                });



        //Mono<Void> output = session.send(Mono.just(session.textMessage("hello")));
        Mono<Void> output = session.send(
                broadcaster.streamJson()
                        .map(session::textMessage)
                        .onErrorResume(e -> {
                            log.error("WebSocket send error. sessionId={}", session.getId(), e);
                            return Mono.empty();
                        })
        );


        return output.and(input);
    }
}
