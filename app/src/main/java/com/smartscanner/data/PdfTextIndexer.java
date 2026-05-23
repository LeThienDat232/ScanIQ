package com.smartscanner.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.smartscanner.util.SearchablePdfExporter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PdfTextIndexer {
    private static final String TAG = "PdfTextIndexer";
    private static final Set<Integer> IN_PROGRESS_DOCUMENT_IDS = Collections.synchronizedSet(new HashSet<>());

    private PdfTextIndexer() {
    }

    public static void indexIfNeeded(@NonNull Context context,
                                     @NonNull DocumentRepository repository,
                                     @NonNull Document document) {
        if (document.id <= 0 || document.ocrText != null || !isPdfDocument(document.fileType, document.filePath)) {
            return;
        }
        if (!IN_PROGRESS_DOCUMENT_IDS.add(document.id)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        DocumentRepository.DATABASE_EXECUTOR.execute(() -> {
            try {
                SearchablePdfExporter.ensureInitialized(appContext);
                File file = new File(document.filePath);
                if (!file.exists()) {
                    return;
                }

                String extracted;
                try (PDDocument pdf = PDDocument.load(file)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    extracted = stripper.getText(pdf);
                }
                String trimmed = extracted == null ? "" : extracted.trim();
                repository.updateDocumentOcrText(document.id, trimmed);
            } catch (Exception e) {
                Log.w(TAG, "PDF text extract failed for " + document.filePath, e);
            } finally {
                IN_PROGRESS_DOCUMENT_IDS.remove(document.id);
            }
        });
    }

    public static boolean isPdfDocument(String fileType, String filePath) {
        if (fileType != null && fileType.toLowerCase(Locale.US).contains("pdf")) {
            return true;
        }
        return filePath != null && filePath.toLowerCase(Locale.US).endsWith(".pdf");
    }
}
