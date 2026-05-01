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
        if (isFileDeleted || !java.io.File(document.filePath).exists()) {
            documentDao.deleteDocument(document)
        }
    }

    suspend fun deleteFolder(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(folder)
    }

    suspend fun updateDocument(document: Document) = withContext(Dispatchers.IO) {
        documentDao.insertDocument(document) // Room's OnConflictStrategy.REPLACE handles updates
    }

    suspend fun moveDocumentToFolder(documentId: Int, newFolderId: Int) = withContext(Dispatchers.IO) {
        documentDao.updateDocumentFolder(documentId, newFolderId)
    }

    suspend fun insertFolder(name: String, parentFolderId: Int? = null): Long = withContext(Dispatchers.IO) {
        folderDao.insertFolder(Folder(name = name, parentFolderId = parentFolderId))
    }

    suspend fun insertDocument(document: Document) = withContext(Dispatchers.IO) {
        documentDao.insertDocument(document)
    }

    suspend fun updateFolderName(folderId: Int, newName: String) = withContext(Dispatchers.IO) {
        // Lấy folder hiện tại, đổi tên và update
        // (Cách nhanh: Dùng Query Update trong Dao, nhưng ở đây dùng hàm Update cơ bản)
        // Lưu ý: Repository nên phối hợp với FolderDao
    }

    suspend fun updateFolder(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.updateFolder(folder)
    }
}
