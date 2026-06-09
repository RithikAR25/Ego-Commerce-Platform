# EGO Platform — Test Results Template

> **Version:** 2.1 — Updated for 3-level category architecture (ROOT → GROUP → LEAF) + Show Password micro-interaction (Jun 5, 2026)
> **Environment:** `dev` / `staging` / `prod`
> **Date Executed:** YYYY-MM-DD
> **QA Engineer:** ____________________
> **Backend Base URL:** `http://localhost:8080`
> **Frontend Base URL:** `http://localhost:5173`

Duplicate this file for each test execution cycle. Mark each test as `PASS`, `FAIL`, or `BLOCKED` (with a reason).

---

## 1. Authentication (Section 1)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| AUTH-001 | Successful Registration | PASS | 201 / accessToken + refreshToken returned |
| AUTH-002 | Duplicate Email Registration Rejected | PASS | 409 |
| AUTH-003 | Rate Limit on Registration | PASS | 429 |
| AUTH-004 | Successful Login | PASS | 200 / accessToken + refreshToken returned |
| AUTH-005 | Login with Wrong Password | PASS | 401 / enumeration-safe message |
| AUTH-006 | Rate Limit on Login | PASS | 429 |
| AUTH-007 | Token Refresh | PASS | 200 / new tokens returned |
| AUTH-008 | Refresh Token Theft Detection | PASS | 401 / family revoked |
| AUTH-009 | Logout Blocklists Access Token | PASS | 401 on reuse |
| AUTH-010 | Get Current User Profile | PASS | 200 |
| AUTH-011 | Deactivated Account Cannot Login | PASS | 401 |
| AUTH-012 | Expired Access Token Rejected | PASS | 401 |
| AUTH-013 | Password Changed At Invalidates Old JWT | PASS | 401 |
| AUTH-014 | Admin Route Blocked for Customer | PASS | 403 |
| AUTH-015 | Unauthenticated Access to Protected Route | PASS | 401 |

---

## 2. Email Verification (Section 2)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| EMAIL-001 | Verification Email Sent on Registration | PASS | |
| EMAIL-002 | Email Verification via Token | PASS | |
| EMAIL-003 | Expired Email Verification Token Rejected | PASS | |
| EMAIL-004 | Idempotent Verification | PASS | |
| EMAIL-005 | Resend Verification Email | PASS | |
| EMAIL-006 | Resend on Already-Verified Account | PASS | |

---

## 3. Password Reset (Section 3)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| PWD-001 | Forgot Password Sends Reset Email |PASS |200/ message : If that email is registered, a password reset link has been sent. |
| PWD-002 | Forgot Password for Unregistered Email |PASS |200 |
| PWD-003 | Reset Password with Valid Token | PASS|200 |
| PWD-004 | Reset Password with Expired Token Rejected |PASS |401 / message: Invalid or expired password reset link. Please request a new one. |
| PWD-005 | Reset Password with Wrong Token Type | PASS| |
| PWD-006 | New Password Complexity Enforcement | PASS| |

---

## 4. Address Book (Section 4)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| ADDR-001 | Add Address (Authenticated) |PASS |201 |
| ADDR-002 | Cannot Add More Than 5 Addresses | PASS |409 / message : Address book is full. You can save a maximum of 5 addresses. Please delete an existing address before adding a new one. |
| ADDR-003 | Set Default Clears Other Defaults | PASS |200 |
| ADDR-004 | Soft-Delete Address | PASS |200 /message: Address deleted successfully. |
| ADDR-005 | Cannot Access Another User's Address | PASS |404 / message : Address not found: id=6|

---

## 5. Category Architecture — 3-Level Hierarchy (Section 5-CAT)

> **Architecture Status:** ✅ MIGRATED — ROOT → GROUP → LEAF (Jun 4, 2026)
> **DB:** 133 categories, 130 hierarchy links seeded
> **API Base:** `http://localhost:8080/api/v1/categories`

### 5a. Navigation Tree (Public)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CAT-NAV-001 | GET /categories returns 5 ROOT categories | PASS | Men, Women, Kids, Home, Gen Z |
| CAT-NAV-002 | Each ROOT has groups[] (GROUP categories) | PASS | Men has 7 groups |
| CAT-NAV-003 | Each GROUP has leafCategories[] (LEAF categories) | PASS | Men/Topwear has 10 leaves |
| CAT-NAV-004 | Cross-listed category appears under multiple parents | PASS | genz-hoodies under genz-streetwear (primary) + genz-oversized (secondary) |
| CAT-NAV-005 | Cross-listed leaf has primary=false on secondary link | PASS | primary=false confirmed via API |
| CAT-NAV-006 | ROOT categories sorted by displayOrder | PASS | |
| CAT-NAV-007 | GROUP categories sorted by displayOrder per ROOT | PASS | |
| CAT-NAV-008 | LEAF categories sorted by displayOrder per GROUP | PASS | |
| CAT-NAV-009 | Empty Groups have leafCategories=[] not null | PASS | Gen Z Accessories |

### 5b. Leaf Categories (Public)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CAT-LEAF-001 | GET /categories/leaves returns 99 LEAF categories | PASS | |
| CAT-LEAF-002 | All returned categories have level="LEAF" | PASS | |
| CAT-LEAF-003 | ROOT and GROUP categories excluded from /leaves | PASS | |
| CAT-LEAF-004 | Each LEAF has parent{id, name, slug} (GROUP parent) | PASS | |

### 5c. Category By Slug (Public)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CAT-SLUG-001 | GET /categories/men returns ROOT with level="ROOT" | PASS | |
| CAT-SLUG-002 | GET /categories/men-topwear returns GROUP with level="GROUP" | PASS | |
| CAT-SLUG-003 | GET /categories/t-shirts returns LEAF with level="LEAF" | PASS | |
| CAT-SLUG-004 | Inactive category returns 404 | PASS | |

### 5d. Breadcrumbs (Public)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CAT-BRD-001 | ROOT breadcrumb returns 1 item [Men] | PASS | |
| CAT-BRD-002 | GROUP breadcrumb returns 2 items [Men, Topwear] | PASS | |
| CAT-BRD-003 | LEAF breadcrumb returns 3 items [Men, Topwear, T-Shirts] | PASS | |

### 5e. Depth Validation (Admin)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CAT-DEPTH-001 | Create category with no parent → ROOT created | PASS | Validated |
| CAT-DEPTH-002 | Create with ROOT parent → GROUP created | PASS | Validated |
| CAT-DEPTH-003 | Create with GROUP parent → LEAF created | PASS | Validated |
| CAT-DEPTH-004 | Create with LEAF parent → 400 (max depth exceeded) | PASS | Returns 400 |
| CAT-DEPTH-005 | Product assigned to ROOT → 400 validation error | PASS | Returns 400 |
| CAT-DEPTH-006 | Product assigned to GROUP → 400 validation error | PASS | Returns 400 |
| CAT-DEPTH-007 | Product assigned to LEAF → 201 success | PASS | Returns 201 |

### 5f. Navigation UI and Breadcrumbs (Frontend)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| NAV-UI-001 | Mega Menu properly nested (ROOT -> GROUP -> LEAF) | PASS | Verified |
| BRD-UI-001 | Breadcrumbs exist on PLP | PASS | Verified |
| BRD-UI-002 | Breadcrumbs complete on PDP | PASS | Verified |

---

## 6. Catalog — Products (Section 6)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CAT-001 | Browse Product Listing | PASS | Returns data |
| CAT-002 | Get Product by Slug | PASS | Returns specific product |
| CAT-003 | Product Not Found Returns 404 | PASS | Handles invalid slugs gracefully |
| CAT-004 | Soft-Deleted Product Not Visible | | |
| CAT-006 | Out-of-Stock Variant Visible but Not Purchasable | | |
| CAT-007 | Admin: Create Product (LEAF category required) | PASS | |
| CAT-008 | Admin: Soft-Delete Product | | |
| CAT-009 | Stock Urgency: Low-Stock Message | | |
| CAT-010 | Stock Urgency: Well-Stocked Returns Null Message | | |

---

## 7. Search — Elasticsearch (Section 7)

> **Note:** categoryId / categoryIds params deprecated. Use categorySlug.

### 7a. Basic Search
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| SRCH-001 | Keyword Search Returns Results | PASS | Verified via API |
| SRCH-002 | Autocomplete Returns Suggestions | | |
| SRCH-003 | Autocomplete Rejects Single Character | | |

### 7b. Category Filtering (New Architecture)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| SRCH-CAT-001 | Filter by LEAF slug returns exact LEAF products | | e.g. ?categorySlug=t-shirts |
| SRCH-CAT-002 | Filter by GROUP slug returns all LEAF products under it | | e.g. ?categorySlug=men-topwear |
| SRCH-CAT-003 | Filter by ROOT slug returns all products under ROOT | PASS | Verified via API (?categorySlug=gen-z) |
| SRCH-CAT-004 | Old categoryId param no longer accepted | | Should return 400 or be ignored |
| SRCH-CAT-005 | categorySlugPath array correct on indexed products | | Check ES doc directly |

### 7c. Other Filters & Sort
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| SRCH-004 | Filter by Price Range | | |
| SRCH-005 | Filter by Attributes (Color/Size) | | |
| SRCH-006 | Sort by Price/Date | | |
| SRCH-007 | ES Circuit-Breaker Fallback to MySQL | | |
| SRCH-008 | Admin: Manual Reindex | | POST /admin/search/reindex-all |
| SRCH-009 | Admin: View ES Outbox DLQ | | |
| SRCH-010 | Outbox DLQ Retry Cap | | |

---

## 8. Cart (Section 8)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CART-001 | Add Item to Cart | PASS | Verified via API |
| CART-002 | Maximum 10 Units Per Line | PASS | Returns 400 when > 10 |
| CART-003 | Cart Persists in Redis (7-day TTL) | | |
| CART-004 | Remove Item from Cart | PASS | Verified via API |
| CART-005 | Update Cart Quantity | PASS | Verified via API |
| CART-006 | Out-of-Stock Warning in Cart | | |
| CART-007 | Guest Cannot Add to Cart | PASS | Returns 401 |
| CART-008 | Anonymous Cart Merge on Login | | |

---

## 9. Coupons (Section 9)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CPN-001 | Validate Valid FLAT Coupon | | |
| CPN-002 | Validate PERCENTAGE Coupon with Max Cap | | |
| CPN-003 | Expired Coupon Rejected | | |
| CPN-004 | Coupon with Minimum Order Amount | | |
| CPN-005 | Coupon Usage Limit Exhausted | | |
| CPN-006 | Invalid Coupon Code | | |

---

## 10. Checkout (Section 10)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| CHK-001 | Complete Checkout Flow | PASS | Verified via API |
| CHK-002 | Email Verification Required | PASS | Returns 409 if unverified |
| CHK-003 | Checkout Fails if Cart Empty | PASS | 400 |
| CHK-004 | Checkout Fails if Item Out of Stock | PASS | 400 |
| CHK-005 | Checkout Without Address Rejected | PASS | 400 |
| CHK-006 | Double-Submit Prevention | PASS | 409 |
| CHK-007 | Valid Coupon Applied | PASS | 200 |
| CHK-008 | Checkout with Free-Text Address | PASS | 201 |
| CHK-009 | Order Items Snapshot Immutability | PASS | Verified |

---

## 12. Payment & Webhooks (Section 12)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| PAY-001 | Create Razorpay Order | PASS | Verified via API |
| PAY-002 | Verify Razorpay Signature (Valid) | PASS | Verified via API |
| PAY-003 | Verify Razorpay Signature (Invalid) | PASS | Verified via API (400 returned) |
| PAY-004 | Webhook Status Update | PASS | Updates order to CONFIRMED |
| PAY-005 | Webhook Idempotency | PASS | Duplicate events handled safely |
| PAY-006 | Webhook Ignoring Non-Captured Events | PASS | Ignored safely |
| PAY-007 | UI: Razorpay Checkout Modal Opens | PASS | Verified |
| PAY-008 | Payment Verification Page | PASS | Success state redirected |

---

## 12. Orders (Section 12)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| ORD-001 | Customer Views Order History | PASS | Verified via API |
| ORD-002 | View Order Detail (Ownership Enforced) | PASS | Verified via API |
| ORD-003 | Cancel PENDING_PAYMENT Order | | |
| ORD-004 | Cannot Cancel Confirmed Order | | |
| ORD-005 | Admin: View All Orders | PASS | Verified via API |
| ORD-006 | Admin: Full Order Status State Machine | PASS | Verified via API |
| ORD-007 | Admin: Invalid Status Transition Rejected | | |
| ORD-008 | Admin: Cancel CONFIRMED Order | | |

---

## 13. Returns & Refunds (Section 13)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| RET-001 | Initiate Return (Happy Path) | PASS | Verified via API |
| RET-002 | Cannot Return Non-Delivered Order | PASS | Returns 409 |
| RET-003 | Return Window Expired (>7 Days) | PASS | Fixed BUG-005 — now uses OrderStatusHistory.DELIVERED.createdAt |
| RET-004 | Duplicate Return Rejected | PASS | Returns 409 |
| RET-005 | View Return Status | PASS | Verified via API |
| RET-006 | Admin: Approve Return and Initiate Refund | PASS | Simulated refund in test mode |
| RET-007 | Admin: Reject Return | PASS | Verified via API |

---

## 14. Reviews (Section 14)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| REV-001 | Verified Purchaser Can Review | PASS | Verified via API |
| REV-002 | Non-Purchaser Cannot Review | PASS | Returns 403 |
| REV-003 | Duplicate Review Rejected | PASS | Returns 409 |
| REV-004 | Review Rating Bounds Enforcement | PASS | Returns 400 |
| REV-005 | HtmlSanitizer Strips XSS | PASS | Verified via API |
| REV-006 | Admin Deletes Review | PASS | Verified via API |

---

## 15. Wishlist (Section 15)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| WISH-001 | Add to Wishlist | PASS | Verified via API |
| WISH-002 | Duplicate Add is Idempotent | PASS | Verified via API |
| WISH-003 | Remove from Wishlist | PASS | Verified via API |
| WISH-004 | Get Wishlist Items | PASS | Verified via API |
| WISH-005 | Stock Availability Flag | PASS | Verified via API |

---

## 16. Frontend — Mega Menu & Navigation (Section 16)

> **Status:** Implemented June 4, 2026

| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| NAV-UI-001 | Desktop mega-menu opens on ROOT hover | | |
| NAV-UI-002 | Mega-menu shows GROUP columns | | |
| NAV-UI-003 | LEAF categories listed under each GROUP column | | |
| NAV-UI-004 | Clicking LEAF navigates to /products?category=<slug> | | |
| NAV-UI-005 | Clicking GROUP navigates to /products?category=<slug> | | |
| NAV-UI-006 | Mobile accordion: ROOT → GROUP → LEAF expands | | |
| NAV-UI-007 | Mega-menu closes on mouse leave | | |
| NAV-UI-008 | Mega-menu renders all 5 ROOT sections | | |

---

## 17. Frontend — Category Breadcrumb (Section 17)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| BRD-UI-001 | ProductListingPage shows breadcrumb for LEAF category | | |
| BRD-UI-002 | Breadcrumb shows ROOT > GROUP > LEAF | | |
| BRD-UI-003 | Clicking GROUP in breadcrumb navigates to GROUP listing | | |
| BRD-UI-004 | Clicking ROOT in breadcrumb navigates to ROOT listing | | |

---

## 18. Admin Portal — Category Management (Section 18)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| ADM-CAT-001 | Admin categories page shows level badges (ROOT/GROUP/LEAF) | | |
| ADM-CAT-002 | Create category with no parent → ROOT | | |
| ADM-CAT-003 | Create category with ROOT parent → GROUP | | |
| ADM-CAT-004 | Create category with GROUP parent → LEAF | | |
| ADM-CAT-005 | Edit category updates name/description/displayOrder | | |
| ADM-CAT-006 | Deactivate hides category from storefront | | |
| ADM-CAT-007 | Reactivate restores category to storefront | | |
| ADM-CAT-008 | Hard delete succeeds for inactive empty category | | |
| ADM-CAT-009 | Manage parents dialog shows current hierarchy links | | |
| ADM-CAT-010 | Add cross-listing parent adds new hierarchy link | | |
| ADM-CAT-011 | Set canonical parent updates primary=true | | |
| ADM-CAT-012 | Remove parent removes hierarchy link | | |

---

## 19. Admin Portal — Advanced & Security (Section 19)
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| ADM-VAR-001 | Admin Creates Variant | | |
| ADM-VAR-002 | Admin Updates Variant | | |
| ADM-ATTR-001 | Admin Adds EAV Attribute | | |
| ADM-IMG-001 | Admin Uploads Product Image | | |
| ADM-IMG-002 | Admin Uploads Variant Image | | |
| ADM-USR-001 | Admin Views Users List | | |
| ADM-USR-002 | Admin Suspends User | | |
| EDG-CHK-001 | Checkout Idempotency (Double Tap) | | |
| EDG-CHK-002 | Checkout Lock Expiry | | |
| EDG-SES-001 | Per-User Session Limit Eviction | | |
| EDG-ADR-001 | Soft-Deleted Address Checkout | | |
| EDG-INV-001 | Inventory Race Condition | | |
| SEC-RZN-001 | Webhook Invalid Signature | PASS | |
| SEC-XSS-001 | XSS Review Sanitization | PASS | |
| SEC-XSS-002 | XSS Address Sanitization | PASS | Fixed BUG-004 — HtmlSanitizer applied in AddressService |
| SEC-JWT-001 | JWT Tampering | PASS | |
| SEC-RAT-001 | Rate Limit Resend Verification | PASS | |
| ES-ADM-001 | Manual Reindex Job | PASS | POST /admin/search/reindex |
| ES-DLQ-001 | Outbox Dead Letter Queue | PASS | |
| ES-DLQ-002 | View Outbox DLQ | PASS | |
| ES-FCT-001 | Faceted Search Params | PASS | |
| ES-CB-001 | Circuit Breaker Fallback | PASS | Verified container stop |

---

## 20. Frontend — Show Password & EGO Logo Micro-Interaction (Section 26)

> **Status:** Implemented June 5, 2026
> **Files:** `LoginForm.tsx`, `LoginPage.tsx`, `RegisterForm.tsx`, `RegisterPage.tsx`

### 20a. Login Page — Show/Hide Password
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| PWD-VIS-001 | Login: Show Password Toggle Renders | PASS | Eye icon visible in password field |
| PWD-VIS-002 | Login: Toggle to Show Password | PASS | type="text", aria-label="Hide Password" |
| PWD-VIS-003 | Login: Toggle Back to Hide Password | PASS | type="password", aria-label="Show Password" |
| PWD-VIS-004 | Login: EGO Logo Animates on Show | PASS | Visually verified rotation/scaling |
| PWD-VIS-005 | Login: EGO Logo Returns on Hide | PASS | Visually verified |
| PWD-VIS-006 | Login: Keyboard Accessible Toggle | PASS | Tab focus + Enter/Space |
| PWD-VIS-007 | Login: Toggle Does Not Steal Focus | PASS | onMouseDown preventDefault |

### 20b. Register Page — Show/Hide Password
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| PWD-VIS-008 | Register: Show Password Toggle Renders | | Helper text preserved |
| PWD-VIS-009 | Register: Toggle Show/Hide Works | | Identical to login behavior |
| PWD-VIS-010 | Register: EGO Logo Animates on Show | | Same animation spec |

### 20c. Responsive Verification
| ID | Test Name | Status | Notes / Bug Ticket |
|---|---|---|---|
| PWD-VIS-011 | Mobile: Login Show Password (< 768px) | BLOCKED | Requires manual verification |
| PWD-VIS-012 | Mobile: Register Show Password (< 768px) | BLOCKED | Requires manual verification |
| PWD-VIS-013 | Desktop: Only Left Panel Logo Animates | PASS | Verified visually |

---

## Migration Verification Checklist (Category Architecture v2)

> Complete this checklist after every DB reset / reindex cycle.

| Check | Status | Command / URL |
|-------|--------|---------------|
| Backend compiles | ✅ | `./mvnw compile` → BUILD SUCCESS |
| DB seeded: 133 categories | ✅ | `SELECT COUNT(*) FROM categories;` → 133 |
| DB seeded: 130 links | ✅ | `SELECT COUNT(*) FROM category_hierarchy_links;` → 130 |
| Tree API returns 5 ROOTs | ✅ | `GET /api/v1/categories` |
| Leaves API returns 99 LEAFs | ✅ | `GET /api/v1/categories/leaves` |
| Product→LEAF validation active | | `POST /admin/products` with GROUP categoryId → 400 |
| ES index has categorySlugPath | | Check product doc in Kibana/curl |
| Mega-menu renders in browser | | `http://localhost:5173` |
| Breadcrumb shows 3 levels | | Navigate to a LEAF category |

---
*QA Cycle Completion Signature: __________________*
*Category Architecture Version: 2.0 (ROOT → GROUP → LEAF, migrated June 4, 2026)*
*Show Password Micro-Interaction: v1.0 (Login + Register, added June 5, 2026)*
