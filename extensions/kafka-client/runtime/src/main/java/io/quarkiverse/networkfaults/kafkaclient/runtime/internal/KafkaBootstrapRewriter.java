package io.quarkiverse.networkfaults.kafkaclient.runtime.internal;

import io.quarkiverse.networkfaults.spi.HostPortListRewriter;

/**
 * A Kafka bootstrap list, {@code [PROTOCOL://]host[:port],...}, where a missing port means 9092.
 */
public class KafkaBootstrapRewriter extends HostPortListRewriter {

    // what a Kafka broker listens on unless told otherwise
    private static final int DEFAULT_PORT = 9092;

    @Override
    protected int defaultPort() {
        return DEFAULT_PORT;
    }
}
