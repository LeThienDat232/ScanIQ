# Privacy Policy — SmartScanner

_Last updated: 2026-05-23_

SmartScanner is an Android application that lets you capture, organize, and process documents on your device. This policy describes what data the app handles and how.

## Data we store on your device

- **Scanned images and imported files** are saved in the app's private internal storage.
- **Document metadata** (title, folder, creation date, OCR text) is stored in a local SQLite database (Room) on your device.
- **Preferences** (theme, language) are stored in Android SharedPreferences on your device.

This data **never leaves your device** except for the one case described below.

## Data sent over the network

The only network feature is the **Text Summarizer**, which sends text you explicitly enter (or extract via OCR and choose to summarize) to our backend at:

    https://smartscanner-be.onrender.com/api/documents/summarize

The backend processes the text to produce a summary and returns it. We do not log, store, or share the text you send beyond the immediate request/response. We do not require an account, and we do not collect personal identifiers.

## Permissions

- **Camera** — to capture document scans.
- **Storage / Manage external storage** — to read files you import and to surface your device's Downloads folder inside the app.
- **Internet** — only used by the Text Summarizer.
- **Notifications** — to display the status of background summarization.

You can revoke any permission at any time in Android system settings.

## Third-party services

- **Google ML Kit (on-device)** — used for text recognition (OCR) and document scanning. All processing happens on your device; no images are sent to Google by this app.
- **The summarizer backend** — see "Data sent over the network" above.

## Children

SmartScanner is not directed at children under 13.

## Changes

If this policy changes, the updated version will appear at this URL. Continued use of the app after a change constitutes acceptance of the updated policy.

## Contact

Open an issue at https://github.com/LeThienDat232/Smart_Scanner_UI/issues
