/**
 * constants.ts
 *
 * App-wide layout constants.
 * Import from here so all consumers stay in sync.
 */

/** Height of the fixed AppBar / Navbar in pixels. */
export const NAVBAR_HEIGHT = 64;

/** Height of the AnnouncementBar (above the Navbar) in pixels. */
export const ANNOUNCEMENT_HEIGHT = 36;

/** Total top offset for content below both bars. */
export const CONTENT_TOP_OFFSET = NAVBAR_HEIGHT + ANNOUNCEMENT_HEIGHT;
