package com.smartscanner.data;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class ImageTextIndexer {
    private static final String TAG = "ImageTextIndexer";
    private static final Set<Integer> IN_PROGRESS_DOCUMENT_IDS = Collections.synchronizedSet(new HashSet<>());

    private ImageTextIndexer() {
    }

    public static void indexIfNeeded(@NonNull Context context,
                                     @NonNull DocumentRepository repository,
                                     @NonNull Document document) {
        if (document.id <= 0 || document.ocrText != null || !isImageDocument(document.fileType, document.filePath)) {
            return;
        }
        if (!IN_PROGRESS_DOCUMENT_IDS.add(document.id)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        DocumentRepository.DATABASE_EXECUTOR.execute(() -> {
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            try {
                File file = new File(document.filePath);
                if (!file.exists()) {
                    recognizer.close();
                    IN_PROGRESS_DOCUMENT_IDS.remove(document.id);
                    return;
                }

                InputImage image = InputImage.fromFilePath(appContext, Uri.fromFile(file));
                recognizer.process(image)
                        .addOnSuccessListener(visionText -> {
                            String text = visionText.getText();
                            repository.updateDocumentOcrText(document.id, text == null ? "" : text.trim());
                        })
                        .addOnFailureListener(e -> Log.w(TAG, "Image OCR failed for " + document.filePath, e))
                        .addOnCompleteListener(task -> {
                            recognizer.close();
                            IN_PROGRESS_DOCUMENT_IDS.remove(document.id);
                        });
            } catch (Exception e) {
                recognizer.close();
                IN_PROGRESS_DOCUMENT_IDS.remove(document.id);
                Log.w(TAG, "Could not start image OCR for " + document.filePath, e);
            }
        });
    }

    public static void indexImageUrisIntoDocument(@NonNull Context context,
                                                  @NonNull DocumentRepository repository,
                                                  int documentId,
                                                  @NonNull List<Uri> imageUris) {
        if (documentId <= 0 || imageUris.isEmpty()) {
            return;
        }
        if (!IN_PROGRESS_DOCUMENT_IDS.add(documentId)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        List<Uri> uris = new ArrayList<>(imageUris);
        DocumentRepository.DATABASE_EXECUTOR.execute(() -> {
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            StringBuilder combinedText = new StringBuilder();
            AtomicInteger pendingCount = new AtomicInteger(uris.size());

            for (int i = 0; i < uris.size(); i++) {
                Uri uri = uris.get(i);
                int pageNumber = i + 1;
                try {
                    InputImage image = InputImage.fromFilePath(appContext, uri);
                    recognizer.process(image)
                            .addOnSuccessListener(visionText -> {
                                String text = visionText.getText();
                                if (text != null && !text.trim().isEmpty()) {
                                    appendPageText(combinedText, pageNumber, text.trim());
                                }
                            })
                            .addOnFailureListener(e -> Log.w(TAG, "Image OCR failed for scanner page " + pageNumber, e))
                            .addOnCompleteListener(task -> finishPageIndexing(
                                    recognizer,
                                    repository,
                                    documentId,
                                    combinedText,
                                    pendingCount
                            ));
                } catch (Exception e) {
                    Log.w(TAG, "Could not start OCR for scanner page " + pageNumber, e);
                    finishPageIndexing(recognizer, repository, documentId, combinedText, pendingCount);
                }
            }
        });
    }

    private static void appendPageText(StringBuilder combinedText, int pageNumber, String text) {
        synchronized (combinedText) {
            if (combinedText.length() > 0) {
                combinedText.append("\n\n");
            }
            combinedText.append("Page ")
                    .append(pageNumber)
                    .append("\n")
                    .append(text);
        }
    }

    private static void finishPageIndexing(@NonNull TextRecognizer recognizer,
                                           @NonNull DocumentRepository repository,
                                           int documentId,
                                           @NonNull StringBuilder combinedText,
                                           @NonNull AtomicInteger pendingCount) {
        if (pendingCount.decrementAndGet() != 0) {
            return;
        }

        String text;
        synchronized (combinedText) {
            text = combinedText.toString().trim();
        }
        repository.updateDocumentOcrText(documentId, text);
        recognizer.close();
        IN_PROGRESS_DOCUMENT_IDS.remove(documentId);
    }

    public static boolean isImageDocument(@Nullable String fileType, @Nullable String filePath) {
        if (fileType != null && fileType.toLowerCase(Locale.US).startsWith("image/")) {
            return true;
        }
        if (filePath == null) {
            return false;
        }

        String lowerPath = filePath.toLowerCase(Locale.US);
        return lowerPath.endsWith(".jpg")
                || lowerPath.endsWith(".jpeg")
                || lowerPath.endsWith(".png")
                || lowerPath.endsWith(".webp")
                || lowerPath.endsWith(".bmp");
    }
}
