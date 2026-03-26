package com.smartscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao
) {

    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()

    fun getRecentDocuments(): Flow<List<Document>> = documentDao.getRecentDocuments()

    suspend fun deleteDocumentAndFile(document: Document) = withContext(Dispatchers.IO) {
        // 1. Delete the physical file first
        val isFileDeleted = FileStorageManager.deletePhysicalFile(document.filePath)
        
        // 2. If file deleted successfully (or didn't exist), delete from DB
        // Note: Even if deletion fails, you might still want to delete from DB 
        // if the file is truly gone. Here we follow your requirement.
        if (isFileDeleted || !java.io.File(document.filePath).exists()) {
            documentDao.deleteDocument(document)
        }
    }

    suspend fun moveDocumentToFolder(documentId: Int, newFolderId: Int) = withContext(Dispatchers.IO) {
        documentDao.updateDocumentFolder(documentId, newFolderId)
    }

    suspend fun insertFolder(name: String) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(Folder(name = name))
    }

    suspend fun insertDocument(document: Document) = withContext(Dispatchers.IO) {
        documentDao.insertDocument(document)
    }
}
