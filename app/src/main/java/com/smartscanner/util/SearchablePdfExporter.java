package com.smartscanner.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class SearchablePdfExporter {
    private static volatile boolean pdfBoxInitialized = false;

    private SearchablePdfExporter() {
    }

    public static synchronized void ensureInitialized(@NonNull Context context) {
        if (!pdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.getApplicationContext());
            pdfBoxInitialized = true;
        }
    }

    @Nullable
    public static String exportFromImageUris(@NonNull Context context,
                                             @NonNull List<Uri> imageUris,
                                             @NonNull File outputFile) throws IOException {
        ensureInitialized(context);
        if (imageUris.isEmpty()) {
            return null;
        }

        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        PDDocument pdfDocument = new PDDocument();
        try {
            PDFont font = PDType1Font.HELVETICA;
            for (Uri uri : imageUris) {
                Bitmap bitmap;
                try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(stream);
                }
                if (bitmap == null) {
                    continue;
                }
                Text visionText = recognizeBlocking(recognizer, InputImage.fromBitmap(bitmap, 0));
                addPage(pdfDocument, bitmap, visionText, font);
                bitmap.recycle();
            }
            pdfDocument.save(outputFile);
            return outputFile.getAbsolutePath();
        } finally {
            recognizer.close();
            try {
                pdfDocument.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Nullable
    private static Text recognizeBlocking(@NonNull TextRecognizer recognizer, @NonNull InputImage image) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Text> ref = new AtomicReference<>();
        recognizer.process(image)
                .addOnSuccessListener(r -> {
                    ref.set(r);
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ref.get();
    }

    private static void addPage(@NonNull PDDocument pdf,
                                @NonNull Bitmap bitmap,
                                @Nullable Text text,
                                @NonNull PDFont font) throws IOException {
        PDRectangle pageSize = new PDRectangle(bitmap.getWidth(), bitmap.getHeight());
        PDPage page = new PDPage(pageSize);
        pdf.addPage(page);

        PDImageXObject image = LosslessFactory.createFromImage(pdf, bitmap);
        PDPageContentStream cs = new PDPageContentStream(pdf, page);
        try {
            cs.drawImage(image, 0, 0, bitmap.getWidth(), bitmap.getHeight());

            if (text == null) {
                return;
            }

            float pageHeight = bitmap.getHeight();
            cs.setRenderingMode(RenderingMode.NEITHER);
            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    Rect box = line.getBoundingBox();
                    if (box == null || box.width() <= 0 || box.height() <= 0) {
                        continue;
                    }
                    String content = line.getText();
                    if (content == null || content.isEmpty()) {
                        continue;
                    }
                    String safe = sanitizeForWinAnsi(content);
                    if (safe.isEmpty()) {
                        continue;
                    }
                    float fontSize = Math.max(1f, box.height() * 0.85f);
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(box.left, pageHeight - box.bottom);
                    cs.showText(safe);
                    cs.endText();
                }
            }
        } finally {
            cs.close();
        }
    }

    // PDType1Font.HELVETICA uses WinAnsi encoding and cannot render Vietnamese
    // diacritics. Strip non-ASCII so PDF saves successfully. Vietnamese search
    // will need a Unicode TTF (NotoSans) bundled as an asset and loaded via
    // PDType0Font.load — tracked as a follow-up.
    private static String sanitizeForWinAnsi(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 32 && c < 127) {
                sb.append(c);
            } else if (c == '\t') {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
