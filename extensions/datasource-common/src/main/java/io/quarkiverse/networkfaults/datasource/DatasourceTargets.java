package io.quarkiverse.networkfaults.datasource;

import io.quarkiverse.networkfaults.spi.TargetName;

/**
 * The names of the two connections of a datasource, {@code datasource:<name>:jdbc} and
 * {@code datasource:<name>:reactive}. The Agroal and reactive datasource modules declare these targets and the
 * Hibernate modules resolve persistence units and repositories to them, so every one of them spells the names here.
 */
public final class DatasourceTargets {

    private static final String FAMILY = "datasource";

    private DatasourceTargets() {
    }

    public static TargetName jdbc(String datasource) {
        return TargetName.of(FAMILY, datasource, "jdbc");
    }

    public static TargetName reactive(String datasource) {
        return TargetName.of(FAMILY, datasource, "reactive");
    }

    /**
     * The reactive or the JDBC connection of a datasource.
     */
    public static TargetName of(String datasource, boolean reactive) {
        return reactive ? reactive(datasource) : jdbc(datasource);
    }
}
