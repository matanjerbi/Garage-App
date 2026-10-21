# Build: Vehicle Maintenance Tracker (personal Android app)

Build a personal Android app for tracking maintenance of multiple vehicles (cars and motorcycles). Single user, all data stored locally on the device, no backend, no accounts. Guiding principle: **keep it simple** — prefer the simplest solution that works, avoid extra libraries, abstractions and features not listed here.

## Tech stack
- Kotlin, Jetpack Compose, Material 3
- Room for the database
- WorkManager for the daily notification check
- Simple MVVM (ViewModel + StateFlow), manual dependency wiring (no Hilt/Koin)
- Single app module, minSdk 26
- UI language: **Hebrew, RTL**. Follow system light/dark theme.
- Units: km, ILS (₪), dates as dd/MM/yyyy

## Core concept
Each vehicle has a list of **maintenance items**, each with an interval in km and/or months — **whichever comes first**. The app computes the status of each item from the last time it was done and the current odometer, and tells the user what is coming up.

Item status:
- **Overdue** (red): km since last done ≥ interval km, OR days since last done ≥ interval days
- **Due soon** (orange): within the "due soon" threshold (default 500 km or 14 days)
- **OK** (green): otherwise

"Last done" = the most recent service record that includes the item. If none exists, use the baseline km/date entered when the item was created (default: vehicle's odometer and date when added to the app).

**Estimated due date** for km-based items: average km/day from odometer readings of the last 60 days (fall back to all readings; if fewer than 2 readings, don't estimate). Show e.g. "in ~1,200 km · around 15/12/2026". The final due date is the earlier of the km-based estimate and the time-based date.

## Data model (Room entities)
- **Vehicle**: id, name/nickname, type (CAR / MOTORCYCLE), make, model, year, licensePlate, photoPath?, trackFuel (bool), archived (bool), createdAt
- **MaintenanceItem**: id, vehicleId, name, category (ENGINE, DRIVETRAIN, BRAKES, TIRES, ELECTRICAL, FLUIDS, OTHER), intervalKm?, intervalMonths?, baselineKm, baselineDate, notes (free text — parts, oil type, tools, etc.), active (bool)
- **ServiceRecord**: id, vehicleId, date, odometerKm, type (MAINTENANCE / REPAIR), performedBy (SELF / GARAGE), garageId?, partsCost, laborCost, notes
- **ServiceRecordItem**: serviceRecordId, maintenanceItemId (many-to-many; one service can cover several items; a REPAIR may have zero items)
- **OdometerReading**: id, vehicleId, date, km, source (MANUAL / SERVICE / FUEL)
- **FuelEntry**: id, vehicleId, date, odometerKm, liters, totalCost, fullTank (bool)
- **VehicleDocument**: id, vehicleId, type (TEST, COMPULSORY_INSURANCE, COMPREHENSIVE_INSURANCE, VEHICLE_LICENSE, OTHER), expiryDate, cost?, notes
- **Garage**: id, name, phone?, address?
- **Attachment**: id, ownerType (SERVICE_RECORD / DOCUMENT), ownerId, filePath, mimeType — images and PDFs, copied into app internal storage

Rules:
- Saving a service record or fuel entry also inserts an OdometerReading.
- Current odometer of a vehicle = max km across its readings.
- Reject an odometer value lower than the previous reading (show a clear error, allow override with confirmation for typo fixes).

## Built-in templates
When adding a vehicle, offer a ready template by type (user can edit/delete/add items afterwards):
- **Motorcycle**: oil + oil filter, chain clean & lube, chain & sprockets, air filter, spark plugs, brake fluid, brake pads (front/rear), front tire, rear tire, coolant, valve clearance check, battery
- **Car**: oil + oil filter, air filter, cabin filter, spark plugs, brake fluid, brake pads, tires, coolant, timing belt, battery, wipers

Put reasonable placeholder intervals and clearly tell the user (on the template screen) to adjust them according to the vehicle's owner's manual.

## Screens
1. **Home** — a card per active vehicle: photo/icon, name, current km, count of overdue/due-soon items, and the single most urgent item. Also expiring documents. FAB: add vehicle. Quick action on each card: "Update km".
2. **Vehicle screen** — tabs:
   - **Maintenance**: items sorted by urgency, each with status color, progress bar (km and/or time), remaining km/days and estimated date. Tap → item detail with its history.
   - **History**: timeline of service records (maintenance + repairs), filter by item.
   - **Fuel** (only if trackFuel): entries list + consumption (km/L, computed only between full-tank fills).
   - **Documents**: test, insurances, license with expiry date and attachments.
   - **Costs**: totals per month and per year, cost per km, breakdown by category (maintenance, repairs, fuel, documents/insurance). One simple bar chart (draw with Compose Canvas, no chart library).
3. **Add service record** — fast form: date (default today), km (prefilled with current odometer), type, multi-select items, self/garage (garage picked from list or added inline), costs, notes, attach photos/PDF (camera or file picker).
4. **Add fuel entry**, **Update km** — minimal dialogs.
5. **Garages** — simple list; tap phone to dial.
6. **Settings** — notification time (default 19:00), due-soon thresholds (default 500 km / 14 days), document reminder lead time (default 30 days), odometer-update reminder (default 10 days), export/import backup, archived vehicles (restore/delete).

Vehicle actions: edit, archive ("sold") — archived vehicles are hidden from home and notifications but keep their history.

## Notifications (push)
- Request POST_NOTIFICATIONS permission on Android 13+.
- One WorkManager periodic job, once a day at the configured time.
- Send a notification when:
  - an item becomes due soon (once per item per cycle)
  - an item is overdue (repeat every 3 days until done)
  - a document expires within the lead time (once), and on the expiry day
  - no odometer update for a vehicle in X days
- Group into one notification per vehicle when there are several. Tapping opens that vehicle's screen.
- Store "last notified" state so the same notification isn't sent daily.

## Backup
- **Export**: a single .zip containing a JSON dump of all tables + the attachments folder, saved via the system file picker (Storage Access Framework).
- **Import**: pick a .zip, confirm that it replaces all current data, restore.
- No automatic or cloud backup.

## Out of scope (do not build)
Accounts, sync, cloud, widgets, OBD, odometer OCR, parts inventory, PDF reports, multiple currencies, engine hours.

## How to work
Build in phases, and after each phase make sure the project compiles and the app runs:
1. Project setup, theme (RTL Hebrew, dark/light), Room schema + DAOs, status/due-date calculation logic with **unit tests** (this is the core logic — test edge cases: km-only, time-only, both, never done, overdue).
2. Vehicles + templates + maintenance items + home screen.
3. Service records, odometer readings, history, garages, attachments.
4. Documents, fuel, costs tab.
5. Notifications.
6. Backup export/import, settings, archive.

Keep the code readable and small; don't add libraries beyond what's listed unless truly necessary, and tell me if you do. At the end, give me the command to build a debug APK.
