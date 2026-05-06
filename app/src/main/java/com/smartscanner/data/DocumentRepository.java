package com.smartscanner.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DocumentRepository {
    public interface LongCallback {
        void onResult(long value);
    }

    public static final ExecutorService DATABASE_EXECUTOR = Executors.newFixedThreadPool(4);

    private final DocumentDao documentDao;
    private final FolderDao folderDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DocumentRepository(DocumentDao documentDao, FolderDao folderDao) {
        this.documentDao = documentDao;
        this.folderDao = folderDao;
    }

    public LiveData<List<Folder>> getAllFolders() {
        return folderDao.getAllFolders();
    }

    public LiveData<List<Document>> getAllDocuments() {
        return documentDao.getAllDocuments();
    }

    public LiveData<List<Document>> getRecentDocuments() {
        return documentDao.getRecentDocuments();
    }

    public LiveData<List<Document>> searchDocuments(String searchQuery) {
        return documentDao.searchDocuments(searchQuery);
    }

    public void runInBackground(Runnable runnable) {
        DATABASE_EXECUTOR.execute(runnable);
    }

    public void insertDocument(Document document) {
        DATABASE_EXECUTOR.execute(() -> documentDao.insertDocument(document));
    }

    public void insertDocument(Document document, @Nullable LongCallback callback) {
        DATABASE_EXECUTOR.execute(() -> {
            long id = documentDao.insertDocument(document);
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(id));
            }
        });
    }

    public void insertFolder(String name, @Nullable Integer parentFolderId) {
        insertFolder(name, parentFolderId, null);
    }

    public void insertFolder(String name, @Nullable Integer parentFolderId, @Nullable LongCallback callback) {
        DATABASE_EXECUTOR.execute(() -> {
            long id = folderDao.insertFolder(new Folder(name, parentFolderId));
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(id));
            }
        });
    }

    public void updateDocument(Document document) {
        DATABASE_EXECUTOR.execute(() -> documentDao.insertDocument(document));
    }

    public void updateFolder(Folder folder) {
        DATABASE_EXECUTOR.execute(() -> folderDao.updateFolder(folder));
    }

    public void moveDocumentToFolder(int documentId, @Nullable Integer newFolderId) {
        DATABASE_EXECUTOR.execute(() -> documentDao.updateDocumentFolder(documentId, newFolderId));
    }

    public void deleteDocumentAndFile(Document document) {
        DATABASE_EXECUTOR.execute(() -> {
            boolean isFileDeleted = FileStorageManager.deletePhysicalFile(document.filePath);
            if (isFileDeleted || !new File(document.filePath).exists()) {
                documentDao.deleteDocument(document);
            }
        });
    }

    public void deleteFolder(Folder folder) {
        DATABASE_EXECUTOR.execute(() -> folderDao.deleteFolder(folder));
    }
}
