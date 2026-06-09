# Address Book

## What

User-managed shipping address book. Supports up to 5 active addresses per user. One address is designated as the default (pre-selected at checkout). All free-text fields are sanitized against XSS at the service layer.

## Why

- **5-address limit:** Prevents data bloat and keeps the checkout address picker list manageable.
- **Default address:** Reduces checkout friction — most customers ship to the same address repeatedly.
- **HtmlSanitizer:** Address fields (especially `landmark` and `addressLine2`) are free-text and displayed in order confirmation emails. Stored XSS via address fields is a real attack vector — sanitization at write time ensures the DB only ever contains clean content.

## Backend

**Module:** `com.ego.raw_ego.address`

| File | Responsibility |
|---|---|
| `AddressService.java` | Full business logic — 5-address limit, default management, XSS sanitization |
| `UserAddress.java` | Entity — all address fields, `isDefault`, `isActive`, `addressType` enum |
| `AddressController.java` | 5 REST endpoints |
| `UserAddressRepository.java` | Ownership-scoped queries, `clearDefaultForUser()` |

**Address types (source-verified from `UserAddress.AddressType`):** `HOME`, `WORK`, `OTHER`

**Business rules enforced in `AddressService.java` (source-verified):**

| Rule | Enforcement |
|---|---|
| Max 5 active addresses | `countByUserIdAndIsActiveTrue(userId) >= 5` → `409` |
| First address auto-set as default | `activeCount == 0` at create time |
| Default cannot be deleted | `address.isDefault()` check → `409` before soft-delete |
| Set default clears all others atomically | `clearDefaultForUser(userId)` then `setDefault(true)` in same `@Transactional` |
| All mutations ownership-scoped | `findByIdAndUserIdAndIsActiveTrue(id, userId)` → `404` if not found |
| Delete is soft | `isActive = false` — never hard-deleted |

**XSS sanitization (source-verified, applied to every field on create AND update):**
```java
.fullName(HtmlSanitizer.sanitize(request.getFullName().trim(), 100))
.phone(HtmlSanitizer.sanitize(request.getPhone().trim(), 20))
.addressLine1(HtmlSanitizer.sanitize(request.getAddressLine1().trim(), 255))
.addressLine2(request.getAddressLine2() != null
    ? HtmlSanitizer.sanitize(request.getAddressLine2().trim(), 255) : null)
.landmark(request.getLandmark() != null
    ? HtmlSanitizer.sanitize(request.getLandmark().trim(), 255) : null)
.city(HtmlSanitizer.sanitize(request.getCity().trim(), 100))
.state(HtmlSanitizer.sanitize(request.getState().trim(), 100))
.pinCode(HtmlSanitizer.sanitize(request.getPinCode().trim(), 20))
.country(HtmlSanitizer.sanitize(request.getCountry().trim(), 100))  // default: "India"
```
This fix was applied in **BUG-004 (June 6, 2026)** — before the fix, raw HTML was persisted. All 13 XSS test cases now pass.

**Checkout integration:**
- `AddressService.getForCheckout(userId, addressId)` — called by `OrderService.checkout()` to validate address ownership
- `AddressService.getDefaultAddress(userId)` — pre-selects default on checkout page load

## Database

**Table: `user_addresses`**

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `user_id` | BIGINT UNSIGNED | FK → users.id |
| `full_name` | VARCHAR(100) | Sanitized. Recipient name |
| `phone` | VARCHAR(20) | Sanitized. Contact for delivery |
| `address_line_1` | VARCHAR(255) | Sanitized. Street address |
| `address_line_2` | VARCHAR(255) | Sanitized. Apt/floor (nullable) |
| `landmark` | VARCHAR(255) | Sanitized. Nearby landmark (nullable) |
| `city` | VARCHAR(100) | Sanitized |
| `state` | VARCHAR(100) | Sanitized |
| `pin_code` | VARCHAR(20) | Sanitized. Indian PIN code |
| `country` | VARCHAR(100) | Sanitized. Default: "India" |
| `address_type` | ENUM | `HOME`, `WORK`, `OTHER` |
| `is_default` | BOOLEAN | Only one true per user (atomically managed) |
| `is_active` | BOOLEAN | Soft-delete flag |
| `created_at` | DATETIME | Immutable |
| `updated_at` | DATETIME | Last mutation |

## API

### All endpoints require JWT (Customer)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/addresses` | List all active addresses (default-first) |
| `GET` | `/api/v1/addresses/{id}` | Get single address |
| `POST` | `/api/v1/addresses` | Create new address |
| `PUT` | `/api/v1/addresses/{id}` | Full update (all fields replaced) |
| `DELETE` | `/api/v1/addresses/{id}` | Soft-delete (cannot delete default) |
| `PATCH` | `/api/v1/addresses/{id}/default` | Set as default address |

**Create / Update request:**
```json
{
  "fullName": "Rithik Agarwal",
  "phone": "9876543210",
  "addressLine1": "12B, Prestige Towers",
  "addressLine2": "Koramangala 4th Block",
  "landmark": "Near Forum Mall",
  "city": "Bengaluru",
  "state": "Karnataka",
  "pinCode": "560034",
  "country": "India",
  "addressType": "HOME",
  "setAsDefault": true
}
```

## Validation Rules

| Field | Rule |
|---|---|
| `fullName` | Required, max 100 chars, `@Pattern(^[A-Za-z\s.'\\-]+$)` — blocks HTML |
| `phone` | Required, max 20, `@Pattern(^[+]?[0-9]{7,15}$)` — digits only |
| `addressLine1` | Required, max 255 chars |
| `city` | Required, max 100 chars |
| `state` | Required, max 100 chars |
| `pinCode` | Required, max 20 chars |
| `addressLine2`, `landmark` | Optional (nullable) |
| `country` | Optional — defaults to `"India"` if null |
| `addressType` | Optional — defaults to `HOME` if null |

Two-layer defence:
1. Bean Validation `@Pattern` on DTO (blocks HTML in structured fields at controller layer)
2. `HtmlSanitizer.sanitize()` in service layer (strips any residual HTML before DB persist)

## Security

- All endpoints scoped to the authenticated user — `findByIdAndUserIdAndIsActiveTrue()` ensures no cross-user access
- XSS sanitized at write time — DB never contains raw HTML
- `isActive = false` soft delete — address history retained for order snapshot integrity

## Known Limitations

- No address validation against postal database (pin codes are not validated against India Post data)
- No international address format support (assumes Indian address structure)
- Address snapshots in orders are taken at checkout time — changing or deleting an address does not affect historical orders

## Source References

- `raw-ego/src/main/java/com/ego/raw_ego/address/service/AddressService.java`
- `raw-ego/src/main/java/com/ego/raw_ego/address/entity/UserAddress.java`
- BUG-004 fix: XSS sanitization added to `buildAddress()` and `updateAddress()` (June 6, 2026)
