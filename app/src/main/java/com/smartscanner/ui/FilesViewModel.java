package com.smartscanner.ui;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartscanner.data.AppDatabase;
import com.smartscanner.data.Document;
import com.smartscanner.data.DocumentRepository;
import com.smartscanner.data.FileStorageManager;
import com.smartscanner.data.Folder;
import com.smartscanner.data.ImageTextIndexer;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FilesViewModel extends AndroidViewModel {
    private final DocumentRepository repository;
    private final LiveData<List<Folder>> folders;
    private final LiveData<List<Document>> databaseDocuments;
    private final MutableLiveData<List<Document>> downloadFiles = new MutableLiveData<>(new ArrayList<>());
    private final MediatorLiveData<List<Document>> recentDocuments = new MediatorLiveData<>();
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MediatorLiveData<List<Document>> searchResults = new MediatorLiveData<>();

    private List<Document> latestDatabaseDocuments = new ArrayList<>();
    private List<Document> latestDownloadFiles = new ArrayList<>();

    public FilesViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        repository = new DocumentRepository(database, database.documentDao(), database.folderDao());
        folders = repository.getAllFolders();
        databaseDocuments = repository.getAllDocuments();

        recentDocuments.addSource(databaseDocuments, documents -> {
            latestDatabaseDocuments = safeList(documents);
            scheduleImageTextIndexing(latestDatabaseDocuments);
            rebuildRecentDocuments();
            rebuildSearchResults();
        });
        recentDocuments.addSource(downloadFiles, documents -> {
            latestDownloadFiles = safeList(documents);
            rebuildRecentDocuments();
            rebuildSearchResults();
        });

        searchResults.addSource(databaseDocuments, documents -> {
            latestDatabaseDocuments = safeList(documents);
            scheduleImageTextIndexing(latestDatabaseDocuments);
            rebuildSearchResults();
        });
        searchResults.addSource(downloadFiles, documents -> {
            latestDownloadFiles = safeList(documents);
            rebuildSearchResults();
        });
        searchResults.addSource(searchQuery, query -> rebuildSearchResults());

        syncDownloads();
    }

    public LiveData<List<Folder>> getFolders() {
        return folders;
    }

    public LiveData<List<Document>> getDatabaseDocuments() {
        return databaseDocuments;
    }

    public LiveData<List<Document>> getDownloadFiles() {
        return downloadFiles;
    }

    public LiveData<List<Document>> getRecentDocuments() {
        return recentDocuments;
    }

    public LiveData<List<Document>> getSearchResults() {
        return searchResults;
    }

    public String getSearchQueryValue() {
        String query = searchQuery.getValue();
        return query == null ? "" : query;
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query == null ? "" : query);
    }

    public void syncDownloads() {
        repository.runInBackground(() -> {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File[] files = downloadDir.listFiles();
            List<Document> documents = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && !file.getName().startsWith(".")) {
                        String extension = getExtension(file.getName());
                        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                        if (mime == null) {
                            mime = "application/octet-stream";
                        }
                        documents.add(new Document(
                                file.getAbsolutePath().hashCode(),
                                -1,
                                file.getName(),
                                file.getAbsolutePath(),
                                mime,
                                null,
                                file.lastModified()
                        ));
                    }
                }
            }
            documents.sort((left, right) -> Long.compare(right.createdAt, left.createdAt));
            downloadFiles.postValue(documents);
        });
    }

    public void insertDocument(@Nullable Integer folderId, String title, String filePath, String fileType) {
        Document document = new Document(folderId, title, filePath, fileType, System.currentTimeMillis());
        repository.insertDocument(document, documentId -> {
            Document savedDocument = new Document((int) documentId, folderId, title, filePath, fileType, null, document.createdAt);
            ImageTextIndexer.indexIfNeeded(getApplication(), repository, savedDocument);
        });
    }

    public void insertScannedDocument(@Nullable Integer folderId,
                                      String title,
                                      String filePath,
                                      String fileType,
                                      @Nullable List<Uri> pageImageUris,
                                      @Nullable DocumentRepository.LongCallback callback) {
        Document document = new Document(folderId, title, filePath, fileType, System.currentTimeMillis());
        List<Uri> scannerPageUris = pageImageUris == null ? new ArrayList<>() : new ArrayList<>(pageImageUris);
        repository.insertDocument(document, documentId -> {
            Document savedDocument = new Document((int) documentId, folderId, title, filePath, fileType, null, document.createdAt);
            if (scannerPageUris.isEmpty()) {
                ImageTextIndexer.indexIfNeeded(getApplication(), repository, savedDocument);
            } else {
                ImageTextIndexer.indexImageUrisIntoDocument(getApplication(), repository, (int) documentId, scannerPageUris);
            }
            if (callback != null) {
                callback.onResult(documentId);
            }
        });
    }

    public void updateDocumentOcrText(int documentId, @Nullable String ocrText) {
        repository.updateDocumentOcrText(documentId, ocrText);
    }

    public void createFolder(String name, @Nullable Integer parentFolderId) {
        String uniqueName = makeUniqueFolderName(name, parentFolderId, null);
        repository.insertFolder(uniqueName, parentFolderId);
    }

    public void deleteDocument(Document document) {
        if (!Objects.equals(document.folderId, -1)) {
            repository.deleteDocumentAndFile(document);
        }
    }

    public void deleteDownloadDocument(Context context, Document document) {
        repository.runInBackground(() -> {
            FileStorageManager.deletePhysicalFile(document.filePath);
            FileStorageManager.scanFile(context, document.filePath);
            syncDownloads();
        });
    }

    public void deleteFolder(Folder folder) {
        repository.deleteFolder(folder);
    }

    public void moveItemsToFolder(List<Object> items, @Nullable Integer targetFolderId) {
        moveItemsToFolder(items, targetFolderId, null);
    }

    public void moveItemsToFolder(List<Object> items,
                                  @Nullable Integer targetFolderId,
                                  @Nullable DocumentRepository.MoveCallback callback) {
        List<Document> documents = new ArrayList<>();
        List<Folder> folders = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Document) {
                Document document = (Document) item;
                if (!Objects.equals(document.folderId, -1)) {
                    documents.add(document);
                }
            } else if (item instanceof Folder) {
                Folder folder = (Folder) item;
                if (!Objects.equals(folder.id, targetFolderId)) {
                    folders.add(folder);
                }
            }
        }
        repository.moveItemsToFolder(documents, folders, targetFolderId, callback);
    }

    public void unfoldFolders(List<Folder> foldersToUnfold) {
        if (foldersToUnfold.isEmpty()) {
            return;
        }

        List<Folder> currentFolders = folders.getValue();
        repository.unfoldFolders(
                new ArrayList<>(foldersToUnfold),
                currentFolders == null ? new ArrayList<>() : new ArrayList<>(currentFolders)
        );
    }

    public void createFolderAndMoveDocuments(String folderName,
                                             List<Document> documents,
                                             @Nullable Integer parentFolderId) {
        List<Object> items = new ArrayList<>(documents);
        createFolderAndMoveItems(folderName, items, parentFolderId);
    }

    public void createFolderAndMoveItems(String folderName,
                                         List<Object> items,
                                         @Nullable Integer parentFolderId) {
        String uniqueName = makeUniqueFolderName(folderName, parentFolderId, null);
        repository.insertFolder(uniqueName, parentFolderId, folderId -> {
            Integer targetId = (int) folderId;
            moveItemsToFolder(items, targetId);
        });
    }

    public void renameFolder(Folder folder, String newName) {
        String uniqueName = makeUniqueFolderName(newName, folder.parentFolderId, folder.id);
        repository.updateFolder(folder.copyWithName(uniqueName));
    }

    public void renameDocument(Context context, Document document, String newTitle) {
        repository.runInBackground(() -> {
            String newPath = FileStorageManager.renamePhysicalFile(context, document.filePath, newTitle);
            if (newPath == null) {
                return;
            }

            if (Objects.equals(document.folderId, -1)) {
                syncDownloads();
            } else {
                File actualFile = new File(newPath);
                repository.updateDocument(document.copyWithTitleAndPath(actualFile.getName(), newPath));
            }
        });
    }

    private void rebuildRecentDocuments() {
        List<Document> merged = new ArrayList<>();
        merged.addAll(latestDatabaseDocuments);
        merged.addAll(latestDownloadFiles);
        merged.sort((left, right) -> Long.compare(right.createdAt, left.createdAt));
        if (merged.size() > 20) {
            merged = new ArrayList<>(merged.subList(0, 20));
        }
        recentDocuments.setValue(merged);
    }

    private void rebuildSearchResults() {
        String query = getSearchQueryValue().trim().toLowerCase(Locale.US);
        if (query.isEmpty()) {
            searchResults.setValue(new ArrayList<>());
            return;
        }

        List<Document> matches = new ArrayList<>();
        List<Document> merged = new ArrayList<>();
        merged.addAll(latestDatabaseDocuments);
        merged.addAll(latestDownloadFiles);
        for (Document document : merged) {
            boolean titleMatches = document.title.toLowerCase(Locale.US).contains(query);
            boolean ocrMatches = document.ocrText != null
                    && document.ocrText.toLowerCase(Locale.US).contains(query);
            if (titleMatches || ocrMatches) {
                matches.add(document);
            }
        }
        matches.sort(Comparator.comparingLong((Document document) -> document.createdAt).reversed());
        searchResults.setValue(matches);
    }

    private void scheduleImageTextIndexing(List<Document> documents) {
        for (Document document : documents) {
            ImageTextIndexer.indexIfNeeded(getApplication(), repository, document);
        }
    }

    private String makeUniqueFolderName(String baseName,
                                        @Nullable Integer parentFolderId,
                                        @Nullable Integer ignoredFolderId) {
        List<Folder> currentFolders = folders.getValue();
        if (currentFolders == null) {
            return baseName;
        }

        String uniqueName = baseName;
        int counter = 1;
        boolean exists;
        do {
            exists = false;
            for (Folder folder : currentFolders) {
                boolean sameParent = Objects.equals(folder.parentFolderId, parentFolderId);
                boolean sameName = folder.name.equalsIgnoreCase(uniqueName);
                boolean ignored = ignoredFolderId != null && folder.id == ignoredFolderId;
                if (sameParent && sameName && !ignored) {
                    exists = true;
                    uniqueName = baseName + "(" + counter + ")";
                    counter++;
                    break;
                }
            }
        } while (exists);
        return uniqueName;
    }

    private List<Document> safeList(@Nullable List<Document> documents) {
        return documents == null ? new ArrayList<>() : new ArrayList<>(documents);
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }
}
