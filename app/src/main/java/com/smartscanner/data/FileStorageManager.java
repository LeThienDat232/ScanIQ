package com.smartscanner.data;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

public final class FileStorageManager {
    private static final String TAG = "FileStorageManager";

    private FileStorageManager() {
    }

    private static String getUniqueFileName(File directory, String fileName) {
        String name = fileName;
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex != -1) {
            name = fileName.substring(0, lastDotIndex);
            extension = fileName.substring(lastDotIndex);
        }

        String uniqueName = fileName;
        int counter = 1;
        while (new File(directory, uniqueName).exists()) {
            uniqueName = name + "(" + counter + ")" + extension;
            counter++;
        }
        return uniqueName;
    }

    @Nullable
    public static String saveImageToInternalStorage(Context context, Bitmap bitmap, String fileName) {
        File directory = context.getFilesDir();
        String lowerName = fileName.toLowerCase(Locale.US);
        String baseName = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ? fileName : fileName + ".jpg";
        String uniqueName = getUniqueFileName(directory, baseName);
        File file = new File(directory, uniqueName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error saving image", e);
            return null;
        }
    }

    @Nullable
    public static String saveFileFromUri(Context context, Uri uri) {
        return saveFileFromUri(context, uri, null);
    }

    @Nullable
    public static String saveFileFromUri(Context context, Uri uri, @Nullable String preferredFileName) {
        String originalName = preferredFileName;
        if (originalName == null || originalName.trim().isEmpty()) {
            originalName = getFileName(context, uri);
        }
        if (originalName == null || originalName.trim().isEmpty()) {
            originalName = "imported_" + System.currentTimeMillis();
        }
        originalName = sanitizeFileName(originalName);

        String uniqueName = getUniqueFileName(context.getFilesDir(), originalName);
        File file = new File(context.getFilesDir(), uniqueName);

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(file)) {
            if (inputStream == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error saving file from Uri", e);
            return null;
        }
    }

    private static String sanitizeFileName(String fileName) {
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.isEmpty()) {
            return "imported_" + System.currentTimeMillis();
        }
        return sanitized;
    }

    @Nullable
    public static String getFileName(Context context, Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        name = cursor.getString(index);
                    }
                }
            }
        }
        if (name == null) {
            name = uri.getPath();
            int cut = name != null ? name.lastIndexOf('/') : -1;
            if (cut != -1) {
                name = name.substring(cut + 1);
            }
        }
        return name;
    }

    public static boolean deletePhysicalFile(String filePath) {
        try {
            File file = new File(filePath);
            return file.exists() && file.delete();
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public static String renamePhysicalFile(Context context, String currentPath, String newName) {
        try {
            File oldFile = new File(currentPath);
            if (!oldFile.exists()) {
                return null;
            }

            File parentDir = oldFile.getParentFile() != null ? oldFile.getParentFile() : context.getFilesDir();
            String uniqueNewName = getUniqueFileName(parentDir, newName);
            File newFile = new File(parentDir, uniqueNewName);

            if (oldFile.renameTo(newFile)) {
                scanFile(context, oldFile.getAbsolutePath());
                scanFile(context, newFile.getAbsolutePath());
                return newFile.getAbsolutePath();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void scanFile(Context context, String filePath) {
        MediaScannerConnection.scanFile(context, new String[]{filePath}, null, null);
    }

    @Nullable
    public static Uri getContentUriFromPath(Context context, String filePath) {
        Uri uri = MediaStore.Files.getContentUri("external");
        String[] projection = {MediaStore.Files.FileColumns._ID};
        String selection = MediaStore.Files.FileColumns.DATA + "=?";
        String[] selectionArgs = {filePath};

        try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID));
                return ContentUris.withAppendedId(uri, id);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error querying MediaStore", e);
            return null;
        }
    }

    @Nullable
    public static String convertImagesToPdf(Context context, List<Uri> imageUris, String outputFileName) {
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();

        try {
            int pageNumber = 1;
            for (Uri uri : imageUris) {
                try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    if (bitmap != null) {
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                                bitmap.getWidth(),
                                bitmap.getHeight(),
                                pageNumber++
                        ).create();
                        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                        Canvas canvas = page.getCanvas();
                        canvas.drawBitmap(bitmap, 0f, 0f, paint);
                        pdfDocument.finishPage(page);
                        bitmap.recycle();
                    }
                }
            }

            String lowerName = outputFileName.toLowerCase(Locale.US);
            String baseName = lowerName.endsWith(".pdf") ? outputFileName : outputFileName + ".pdf";
            String uniqueName = getUniqueFileName(context.getFilesDir(), baseName);
            File file = new File(context.getFilesDir(), uniqueName);
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                pdfDocument.writeTo(outputStream);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error creating PDF", e);
            return null;
        } finally {
            pdfDocument.close();
        }
    }
}
