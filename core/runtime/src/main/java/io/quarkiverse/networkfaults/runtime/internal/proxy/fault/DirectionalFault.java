package io.quarkiverse.networkfaults.runtime.internal.proxy.fault;

import io.quarkiverse.networkfaults.Advanced;
import io.quarkiverse.networkfaults.runtime.internal.proxy.Direction;
import io.quarkiverse.networkfaults.runtime.internal.proxy.Fault;

abstract class DirectionalFault implements Fault {

    private final Advanced.Direction direction;

    DirectionalFault(Advanced.Direction direction) {
        this.direction = direction;
    }

    boolean applies(Direction d) {
        return switch (direction) {
            case BOTH -> true;
            case UPSTREAM -> d == Direction.UPSTREAM;
            case DOWNSTREAM -> d == Direction.DOWNSTREAM;
        };
    }

    String directionSuffix() {
        return direction == Advanced.Direction.BOTH ? "" : " " + direction.name().toLowerCase();
    }
}
