package com.smartscanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getDocumentsInFolder(folderId: Int): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE folderId IS NULL ORDER BY createdAt DESC")
    fun getRootDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC LIMIT 10")
    fun getRecentDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :searchQuery || '%' OR ocrText LIKE '%' || :searchQuery || '%'")
    fun searchDocuments(searchQuery: String): Flow<List<Document>>

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("UPDATE documents SET folderId = :newFolderId WHERE id = :documentId")
    suspend fun updateDocumentFolder(documentId: Int, newFolderId: Int)
}
