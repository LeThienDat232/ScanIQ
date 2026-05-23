package com.smartscanner.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DocumentRepository {
    private static final String TAG = "DocumentRepository";

    public interface LongCallback {
        void onResult(long value);
    }

    public interface MoveCallback {
        void onResult(int movedCount);
    }

    public static final ExecutorService DATABASE_EXECUTOR = Executors.newFixedThreadPool(4);

    @Nullable
    private final AppDatabase database;
    private final DocumentDao documentDao;
    private final FolderDao folderDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DocumentRepository(DocumentDao documentDao, FolderDao folderDao) {
        this(null, documentDao, folderDao);
    }

    public DocumentRepository(@Nullable AppDatabase database, DocumentDao documentDao, FolderDao folderDao) {
        this.database = database;
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

    public void updateDocumentOcrText(int documentId, @Nullable String ocrText) {
        DATABASE_EXECUTOR.execute(() -> documentDao.updateDocumentOcrText(documentId, ocrText));
    }

    @Nullable
    public String getDocumentOcrTextSnapshot(int documentId) {
        return documentDao.getDocumentOcrText(documentId);
    }

    public void updateFolder(Folder folder) {
        DATABASE_EXECUTOR.execute(() -> folderDao.updateFolder(folder));
    }

    public void moveDocumentToFolder(int documentId, @Nullable Integer newFolderId) {
        DATABASE_EXECUTOR.execute(() -> documentDao.updateDocumentFolder(documentId, newFolderId));
    }

    public void moveItemsToFolder(List<Document> documents,
                                  List<Folder> folders,
                                  @Nullable Integer targetFolderId,
                                  @Nullable MoveCallback callback) {
        DATABASE_EXECUTOR.execute(() -> {
            int[] movedCount = {0};
            Runnable moveTask = () -> {
                for (Document document : documents) {
                    if (!Objects.equals(document.folderId, targetFolderId)) {
                        movedCount[0] += documentDao.updateDocumentFolder(document.id, targetFolderId);
                    }
                }
                for (Folder folder : folders) {
                    if (!Objects.equals(folder.parentFolderId, targetFolderId)) {
                        movedCount[0] += folderDao.updateFolder(folder.copyWithParent(targetFolderId));
                    }
                }
            };

            if (database != null) {
                database.runInTransaction(moveTask);
            } else {
                moveTask.run();
            }

            if (callback != null) {
                mainHandler.post(() -> callback.onResult(movedCount[0]));
            }
        });
    }

    public void unfoldFolders(List<Folder> foldersToUnfold, List<Folder> allFolders) {
        DATABASE_EXECUTOR.execute(() -> {
            List<Folder> orderedFolders = new ArrayList<>(foldersToUnfold);
            orderedFolders.sort((left, right) -> Integer.compare(
                    folderDepth(right, allFolders),
                    folderDepth(left, allFolders)
            ));

            for (Folder folder : orderedFolders) {
                documentDao.moveDocumentsFromFolder(folder.id, folder.parentFolderId);
                folderDao.moveChildFolders(folder.id, folder.parentFolderId);
                folderDao.deleteFolder(folder);
            }
        });
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
        DATABASE_EXECUTOR.execute(() -> {
            List<Integer> folderIds = collectFolderTreeIds(folder.id, folderDao.getAllFoldersSnapshot());
            if (folderIds.isEmpty()) {
                return;
            }

            List<Document> documents = documentDao.getDocumentsInFolders(folderIds);
            if (!deletePhysicalFiles(documents)) {
                return;
            }

            Runnable deleteTask = () -> {
                documentDao.deleteDocumentsInFolders(folderIds);
                folderDao.deleteFoldersByIds(folderIds);
            };

            if (database != null) {
                database.runInTransaction(deleteTask);
            } else {
                deleteTask.run();
            }
        });
    }

    private List<Integer> collectFolderTreeIds(int rootFolderId, List<Folder> allFolders) {
        List<Integer> folderIds = new ArrayList<>();
        Set<Integer> visitedFolderIds = new HashSet<>();
        if (!visitedFolderIds.add(rootFolderId)) {
            return folderIds;
        }

        folderIds.add(rootFolderId);
        for (int i = 0; i < folderIds.size(); i++) {
            int parentId = folderIds.get(i);
            for (Folder folder : allFolders) {
                if (Objects.equals(folder.parentFolderId, parentId) && visitedFolderIds.add(folder.id)) {
                    folderIds.add(folder.id);
                }
            }
        }
        return folderIds;
    }

    private boolean deletePhysicalFiles(List<Document> documents) {
        boolean allDeleted = true;
        for (Document document : documents) {
            File file = new File(document.filePath);
            if (!file.exists()) {
                continue;
            }

            boolean deleted = FileStorageManager.deletePhysicalFile(document.filePath);
            if (!deleted && file.exists()) {
                allDeleted = false;
                Log.w(TAG, "Could not delete file: " + document.filePath);
            }
        }
        return allDeleted;
    }

    private int folderDepth(Folder folder, List<Folder> allFolders) {
        int depth = 0;
        Integer parentId = folder.parentFolderId;
        Set<Integer> visitedParentIds = new HashSet<>();
        while (parentId != null && visitedParentIds.add(parentId)) {
            Folder parent = findFolder(parentId, allFolders);
            if (parent == null) {
                break;
            }
            depth++;
            parentId = parent.parentFolderId;
        }
        return depth;
    }

    @Nullable
    private Folder findFolder(int folderId, List<Folder> allFolders) {
        for (Folder folder : allFolders) {
            if (Objects.equals(folder.id, folderId)) {
                return folder;
            }
        }
        return null;
    }
}
