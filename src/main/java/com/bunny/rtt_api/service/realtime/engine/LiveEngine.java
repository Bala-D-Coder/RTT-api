package com.bunny.rtt_api.service.realtime.engine;

import java.nio.ByteBuffer;

public interface LiveEngine extends AutoCloseable {
    void open(EngineSession s);
    void sendAudio(ByteBuffer pcm16);
    void flush();
    String name();

    @Override
    default void close() {}
}
