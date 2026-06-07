# ScanIQ

ScanIQ is an Android document scanner focused on turning paper documents, images, and PDFs into organized, searchable files. The app lets users scan documents, import files, extract text with OCR, create searchable PDFs, organize documents in folders, and summarize extracted text.

## Features

- Scan documents with Google ML Kit Document Scanner.
- Capture images with CameraX when the document scanner is unavailable.
- Import images, PDFs, and other files into the app library.
- Organize documents with folders, nested folders, rename, move, delete, and share actions.
- Search files by title and OCR-extracted text.
- Sync and browse files from the device Downloads folder.
- Extract text from images using ML Kit Text Recognition.
- Extract text from PDFs using PDFBox-Android.
- Merge selected images into a searchable PDF.
- Apply image filters such as grayscale, black and white, and shadow removal.
- Summarize long text or OCR text through the backend summarizer API.
- Switch between light/dark themes and English/Vietnamese UI text.

## Tech Stack

- Android native app written in Java.
- XML layouts with ViewBinding.
- Room for local document and folder storage.
- CameraX for camera capture.
- Google ML Kit for document scanning and OCR.
- PDFBox-Android for PDF text extraction and searchable PDF export.
- Retrofit and Gson for backend API calls.
- Material Components for Android UI elements.

## Project Structure

```text
app/src/main/java/com/smartscanner/
+-- data/       # Room database, DAOs, repositories, file storage, OCR indexing
+-- network/    # Retrofit API models and service definitions
+-- service/    # Background text summarization service
+-- ui/         # Camera, file list, folder, and summarizer UI logic
+-- util/       # Image filters and searchable PDF export helpers
+-- MainActivity.java

app/src/main/res/
+-- drawable/   # App icons and action icons
+-- layout/     # XML screen and item layouts
+-- values/     # Colors, strings, and themes
+-- xml/        # Backup, file provider, and data extraction configuration
```

## Requirements

- Android Studio.
- JDK 11 or newer.
- Android SDK with compile SDK 35.
- Android device or emulator running Android 7.0 (API 24) or newer.
- Google Play services on the device for ML Kit Document Scanner.

## Getting Started

1. Clone the repository.

   ```bash
   git clone https://github.com/LeThienDat232/ScanIQ.git
   cd ScanIQ
   ```

2. Open the project in Android Studio.

3. Let Gradle sync the project dependencies.

4. Run the app on a device or emulator.

You can also build from the command line:

```bash
./gradlew assembleDebug
```

## Permissions

ScanIQ requests these Android permissions:

- Camera: capture document scans.
- Storage or manage external storage: import files and show the Downloads folder.
- Internet: call the text summarization backend.
- Notifications: show background summarization status.

Most document data is stored locally on the device. The summarizer sends only the text that the user chooses to summarize to the configured backend API.

## Backend Summarizer

The app uses Retrofit to call the summarizer endpoint:

```text
https://smartscanner-be.onrender.com/api/documents/summarize
```

The mobile app can still scan, import, organize, OCR, and export documents without using the summarizer. Summarization requires network access and an available backend.

## Documentation

- Privacy policy: [docs/privacy.md](docs/privacy.md)

## Team Focus

ScanIQ is now centered on the scanner workflow: fast capture, clean file organization, searchable text, and useful document export tools. AI summarization remains a supporting feature for helping users understand long scanned content more quickly.
