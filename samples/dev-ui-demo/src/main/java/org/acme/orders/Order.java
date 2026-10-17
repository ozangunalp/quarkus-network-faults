package org.acme.orders;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * An order that was placed: the datasource behind it is the second target the Dev UI lists.
 */
@Entity
@Table(name = "orders")
public class Order extends PanacheEntity {

    public String item;

    public Instant placedAt;
}
