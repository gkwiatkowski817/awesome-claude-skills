# HomeVault — Home Inventory & Smart Pantry

A self-contained **Progressive Web App (PWA)** for your phone (built and tested for a Samsung Galaxy S21, works on any modern Android/iOS browser). It does two jobs:

1. **Home inventory for insurance** — catalogue everything you own (furniture, computers, TVs, appliances…) with prices, purchase dates, serial numbers, **photos and invoice/receipt scans**, and a **virtual map of your apartment** showing which room each item lives in and what it's worth.
2. **Smart pantry** — track groceries across **Fridge, Freezer, Pantry and Long-term storage**, add products by **scanning the barcode with your phone camera** (or photographing them), record **expiration dates with alerts**, and get **recipe suggestions based on what you currently have**.

Everything is stored **locally on the device** (IndexedDB) — no account, no server, works fully offline. There's a one-tap encrypted-free JSON backup/restore so you never lose your data.

---

## Get the installable `.apk` (native Android build)

The app can also be wrapped as a real Android APK (a thin WebView shell in
[`android/`](./android) that bundles the web app, so it works fully offline and
keeps camera + IndexedDB + notifications). The APK is built automatically by
GitHub Actions because the Android SDK can't be installed in every environment.

**To get the file:**
1. In the GitHub repo, open the **Actions** tab → **Build HomeVault APK** → run it
   (it also runs automatically on every push to this app).
2. When it finishes, download **`HomeVault-apk`** from the run's **Artifacts**,
   or grab `HomeVault.apk` from the auto-created **`homevault-latest`** prerelease.
3. Copy it to your Galaxy S21, tap it, allow **"Install unknown apps"** for your
   browser/file manager when prompted, and install.

This is a **debug-signed** APK — perfect for personal use. For Play Store
distribution you'd swap in a release signing key.

To build it yourself locally you need the Android SDK + JDK 17, then:
`cd android && ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Install as a PWA on your Samsung S21

You don't go through the Play Store — a PWA installs straight from the browser.

1. Put the app files on something the phone can open over **HTTPS** (required for camera + install). Easiest options:
   - **GitHub Pages / Netlify / Vercel** — drop this folder in and deploy (it's just static files).
   - **Quick local test:** on a computer run `python3 -m http.server 8080` inside this folder, then open `http://<computer-ip>:8080` on the phone (camera/notifications need HTTPS or `localhost`, so for full features use one of the hosts above).
2. Open the page in **Chrome** on your S21.
3. Tap the **⋮ menu → "Add to Home screen"** (or the **⚙︎ → Install app** button inside HomeVault).
4. Launch it from your home screen — it now runs full-screen like a native app.

> Camera scanning, install, and notifications require the page to be served over **HTTPS** (or `localhost`). Opening the `.html` file directly with `file://` will work for data entry but not for the camera.

---

## Features

### Inventory tab
- Add items with **name, category, room, price, purchase date, brand/model, serial number, notes**.
- Attach **multiple item photos** and **multiple invoice/receipt photos** (taken with the camera or chosen from gallery; auto-compressed so thousands fit on the device).
- Live **summary**: total item count, **total declared value**, number of rooms.
- Filter by room; search across all items.
- **Export inventory as CSV** for your insurer, or a full **JSON backup** (includes photos).

### Map tab
- Draw your apartment: tap to drop **rooms**, drag them to arrange a floor plan, long-press to rename/remove.
- Each room shows its **item count and total value**.
- **"Place item"** assigns belongings to rooms; tap a room to see everything in it.

### Pantry tab
- Four storage areas: **Fridge ❄️ · Freezer · Pantry · Long-term 📦**, each with a live count.
- **Scan a barcode** with the camera (uses the browser's built-in `BarcodeDetector`). When online it auto-fills the product name from the free [Open Food Facts](https://world.openfoodfacts.org) database; offline you just type it.
- Record **quantity, expiration date, and a photo** per product.
- Items are colour-coded by freshness and sorted **soonest-to-expire first**.
- **"Expiring soon"** view and **push notifications** for anything within your chosen window (default 3 days).

### Recipes tab
- Suggests recipes from a built-in cookbook, ranked by **how much you can already make** from what's in your fridge/freezer/pantry.
- Filter to **Ready to cook** or **Almost there** (missing ≤ 2 ingredients), and tap any recipe to see which ingredients you have vs. need to buy.
- Add your own recipes by editing `data/recipes.json`.

### Settings (⚙︎)
- Currency, expiration-alert window, enable notifications, install the app, and **backup / restore / CSV export**.

---

## Files

| File | Purpose |
|------|---------|
| `index.html` | App shell and tab layout |
| `styles.css` | Mobile-first dark UI |
| `app.js` | All logic (IndexedDB storage, inventory, map, pantry, scanner, recipes, notifications) |
| `service-worker.js` | Offline caching + notifications |
| `manifest.webmanifest` | PWA install metadata |
| `data/recipes.json` | Editable recipe cookbook |
| `icons/` | App icons |

No build step, no dependencies — open `index.html` and it runs.

## Privacy

All data (including photos) stays in the browser's IndexedDB on your phone. Nothing is uploaded. The only network call is the optional barcode→name lookup to Open Food Facts, and only when you scan a code while online.
