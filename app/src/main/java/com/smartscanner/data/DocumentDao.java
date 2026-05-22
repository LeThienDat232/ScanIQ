package com.smartscanner.data;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertDocument(Document document);

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY createdAt DESC")
    LiveData<List<Document>> getDocumentsInFolder(int folderId);

    @Query("SELECT * FROM documents WHERE folderId IS NULL ORDER BY createdAt DESC")
    LiveData<List<Document>> getRootDocuments();

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    LiveData<List<Document>> getAllDocuments();

    @Query("SELECT * FROM documents WHERE folderId IN (:folderIds)")
    List<Document> getDocumentsInFolders(List<Integer> folderIds);

    @Query("SELECT * FROM documents ORDER BY createdAt DESC LIMIT 10")
    LiveData<List<Document>> getRecentDocuments();

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :searchQuery || '%' OR ocrText LIKE '%' || :searchQuery || '%'")
    LiveData<List<Document>> searchDocuments(String searchQuery);

    @Delete
    int deleteDocument(Document document);

    @Query("UPDATE documents SET folderId = :newFolderId WHERE id = :documentId")
    int updateDocumentFolder(int documentId, @Nullable Integer newFolderId);

    @Query("UPDATE documents SET ocrText = :ocrText WHERE id = :documentId")
    int updateDocumentOcrText(int documentId, @Nullable String ocrText);

    @Query("UPDATE documents SET folderId = :newFolderId WHERE folderId = :oldFolderId")
    int moveDocumentsFromFolder(int oldFolderId, @Nullable Integer newFolderId);

    @Query("DELETE FROM documents WHERE folderId IN (:folderIds)")
    int deleteDocumentsInFolders(List<Integer> folderIds);
}
