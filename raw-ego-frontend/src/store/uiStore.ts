/**
 * uiStore.ts
 *
 * Zustand store for UI-only state — drawers, modals, and toasts.
 *
 * Rule: This store holds NO server data. All API data lives in TanStack Query.
 * This is purely for visual state that needs to be accessible across components
 * without prop drilling (e.g. Navbar opens CartDrawer, CartDrawer reads from here).
 */

import { create } from 'zustand';
import { toast as sonnerToast } from 'sonner';

// ── Toast ──────────────────────────────────────────────────────────────────────

export type ToastSeverity = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id:       string;
  message:  string;
  severity: ToastSeverity;
}

// ── Modal types ────────────────────────────────────────────────────────────────

export type ActiveModal = 'size-guide' | 'return-request' | 'address-form' | null;

// ── State interface ────────────────────────────────────────────────────────────

interface UiState {
  // Cart drawer (right-side slide-out)
  cartDrawerOpen:  boolean;
  openCartDrawer:  () => void;
  closeCartDrawer: () => void;

  // Toast notifications
  toasts:      Toast[];
  pushToast:   (message: string, severity?: ToastSeverity) => void;
  removeToast: (id: string) => void;

  // Modal controller
  activeModal: ActiveModal;
  openModal:   (modal: ActiveModal) => void;
  closeModal:  () => void;
}

// ── Store ──────────────────────────────────────────────────────────────────────

export const useUiStore = create<UiState>()((set) => ({
  // Cart drawer
  cartDrawerOpen:  false,
  openCartDrawer:  () => set({ cartDrawerOpen: true }),
  closeCartDrawer: () => set({ cartDrawerOpen: false }),

  // Toasts
  toasts: [],
  pushToast: (message, severity = 'info') => {
    const id = `toast_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
    set((state) => ({ toasts: [...state.toasts, { id, message, severity }] }));
    // Auto-dismiss after 4 seconds
    setTimeout(() => {
      set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
    }, 4000);
  },
  removeToast: (id) =>
    set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),

  // Modal
  activeModal: null,
  openModal:   (modal) => set({ activeModal: modal }),
  closeModal:  () => set({ activeModal: null }),
}));

// ── Convenience toast helpers ──────────────────────────────────────────────────

/** Usage: toast.success('Added to cart!') */
export const toast = {
  success: (msg: string) => sonnerToast.success(msg),
  error:   (msg: string) => sonnerToast.error(msg),
  warning: (msg: string) => sonnerToast.warning(msg),
  info:    (msg: string) => sonnerToast.info(msg),
};
