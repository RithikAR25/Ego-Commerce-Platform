# EGO Frontend (`raw-ego-frontend`)

This is the React Vite frontend for the EGO E-Commerce platform. It contains a unified Single Page Application (SPA) that serves both the customer-facing storefront and the secure admin portal.

---

## 🛠️ Tech Stack

* **Core:** React 19 / TypeScript 5
* **Build Tool:** Vite 8
* **Styling:** Material-UI (MUI) v9 + Emotion + Framer Motion
* **State Management (Client):** Zustand
* **State Management (Server Cache):** TanStack Query (React Query) v5
* **Routing:** React Router DOM v7
* **Form Validation:** React Hook Form + Zod
* **Data Fetching:** Axios

---

## 🚀 Quick Start

### Prerequisites
* Node.js 20+
* EGO Backend running on `localhost:8080` (See [Backend Setup](../raw-ego/README.md))

### 1. Environment Configuration

From the **`raw-ego-frontend/` folder**, create your local environment file:

**macOS / Linux / Git Bash:**
```bash
cp .env.example .env.local
```

**Windows PowerShell:**
```powershell
Copy-Item .env.example .env.local
```

Open `.env.local` in any text editor. It should contain the following (the defaults work for local development):

```env
# URL of the backend API — do not change this for local development
VITE_API_BASE_URL=http://localhost:8080/api/v1

# Your Cloudinary cloud name — needed for product image display
# Get from: https://cloudinary.com/console (top-left of the dashboard)
VITE_CLOUDINARY_CLOUD_NAME=your-cloudinary-cloud-name

# Your Razorpay test key — needed for the checkout flow
# Get from: https://dashboard.razorpay.com/app/keys (use the Test mode key)
VITE_RAZORPAY_KEY_ID=rzp_test_your_key_id
```

> The app will run without Cloudinary and Razorpay keys, but product images and payments will not work.

### 2. Install Dependencies
```bash
npm install
```

### 3. Start the Development Server
```bash
npm run dev
```

The application will be available at:
* **Storefront:** [http://localhost:5173](http://localhost:5173)
* **Admin Portal:** [http://localhost:5173/admin](http://localhost:5173/admin)

---

## 🏗️ Project Structure

```
src/
├── api/          # Axios instance, interceptors, API client functions
├── components/   # Shared UI components (Buttons, Cards, Inputs)
├── features/     # Feature-specific components (Cart, Checkout, ProductGrid)
├── hooks/        # Custom React hooks
├── pages/        # Route-level page components (Storefront + Admin)
├── providers/    # Global Context providers (MUI Theme, Auth)
├── router/       # React Router configurations and Auth Guards
├── schemas/      # Zod validation schemas
├── store/        # Zustand state stores (authStore, cartStore)
├── theme/        # MUI Theme definitions, color palettes, typography
├── types/        # TypeScript interfaces and type definitions
└── utils/        # Helper functions, formatters, constants
```

---

## 🔐 Authentication Architecture

The frontend strictly relies on a **Stateless JWT** pattern. It never stores the Access Token in `localStorage` (mitigating XSS risks). 

1. **Login:** User logs in, backend returns short-lived Access Token in JSON payload and sets a long-lived `httpOnly` Refresh Token cookie.
2. **State:** Access Token is stored exclusively in Zustand memory (`authStore`).
3. **Silent Refresh:** When the Access Token expires, API calls return `401 Unauthorized`. The Axios interceptor automatically pauses the request queue, calls `/auth/refresh` using the secure cookie, updates the Zustand store with the new token, and replays the paused requests. This happens entirely transparently to the user.

---

## 🎨 Theme & Styling

The UI is built on a customized **Material-UI (MUI)** theme. We avoid inline styles and standard CSS files in favor of the `sx` prop and styled components for robust, dynamic theme support (including dark mode).

The theme system relies on a central palette of specific aesthetic colors (found in `src/theme/`).

---

## 📜 Scripts

* `npm run dev`: Starts the Vite development server.
* `npm run build`: Compiles TypeScript and builds the production bundle to `dist/`.
* `npm run lint`: Runs ESLint across the codebase.
* `npm run preview`: Bootstraps a local web server to preview the `dist/` production build.
