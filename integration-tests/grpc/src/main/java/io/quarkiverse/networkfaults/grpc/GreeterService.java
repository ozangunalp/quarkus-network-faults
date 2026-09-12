package io.quarkiverse.networkfaults.grpc;

import io.quarkiverse.networkfaults.grpc.proto.Greeter;
import io.quarkiverse.networkfaults.grpc.proto.HelloReply;
import io.quarkiverse.networkfaults.grpc.proto.HelloRequest;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;

/**
 * The gRPC service the client calls, served by this same application on its HTTP port.
 */
@GrpcService
public class GreeterService implements Greeter {

    @Override
    public Uni<HelloReply> sayHello(HelloRequest request) {
        return Uni.createFrom().item(HelloReply.newBuilder().setMessage("hello " + request.getName()).build());
    }
}
