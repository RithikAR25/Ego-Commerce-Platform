package com.ego.raw_ego.address.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity for the {@code user_addresses} table.
 *
 * <p>Represents a named, reusable shipping address in a customer's address book.
 * Follows the Amazon/Flipkart address book model:
 * <ul>
 *   <li>Max 5 <em>active</em> addresses per user (enforced in {@link com.ego.raw_ego.address.service.AddressService}).</li>
 *   <li>At most one address may be {@code isDefault=true} per user at a time.</li>
 *   <li>Soft-delete via {@code isActive=false} — the row is retained for order snapshot history.</li>
 * </ul>
 *
 * <p><b>Address snapshot:</b> At checkout, the selected address fields are serialised into
 * {@code Order.addressSnapshot} (JSON). This means future edits to this address entity
 * do NOT retroactively change order history.
 */
@Entity
@Table(
    name = "user_addresses",
    indexes = {
        @Index(name = "idx_addr_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Raw FK to {@code users.id}. Kept as a raw column to avoid cross-module JPA coupling. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Recipient full name — 2–100 characters, no HTML allowed. */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** 10-digit Indian mobile number, validated as /^[6-9]\d{9}$/. */
    @Column(nullable = false, length = 15)
    private String phone;

    /** First line: house/flat/building + street. Max 200 characters. */
    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    /** Second line: area/locality/taluka. Optional. */
    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    /** Nearby landmark (optional) for delivery guidance. */
    @Column(length = 150)
    private String landmark;

    /** City, e.g. "Mumbai". Max 100 characters. */
    @Column(nullable = false, length = 100)
    private String city;

    /** State, e.g. "Maharashtra". Max 100 characters. */
    @Column(nullable = false, length = 100)
    private String state;

    /** 6-digit Indian PIN code, validated as /^\d{6}$/. */
    @Column(name = "pin_code", nullable = false, length = 6)
    private String pinCode;

    /** Country — defaults to "India". */
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String country = "India";

    /**
     * Address type for UI display and quick filtering.
     * HOME / WORK / OTHER.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 10)
    @Builder.Default
    private AddressType addressType = AddressType.HOME;

    /**
     * Whether this is the user's default shipping address.
     * Only one address per user may be {@code true} at a time.
     * Enforced in {@link com.ego.raw_ego.address.service.AddressService#setDefault}.
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    /**
     * Soft-delete flag. When {@code false}, this address is excluded from
     * the address book UI and cannot be selected at checkout.
     * Defaults to {@code true} on creation.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Convenience: renders the address as a single-line string for order snapshot. */
    public String toSnapshotLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(fullName).append(", ");
        sb.append(addressLine1);
        if (addressLine2 != null && !addressLine2.isBlank()) {
            sb.append(", ").append(addressLine2);
        }
        if (landmark != null && !landmark.isBlank()) {
            sb.append(" (near ").append(landmark).append(")");
        }
        sb.append(", ").append(city)
          .append(", ").append(state)
          .append(" - ").append(pinCode)
          .append(", ").append(country)
          .append(" | ").append(phone);
        return sb.toString();
    }

    public enum AddressType {
        HOME, WORK, OTHER
    }
}
