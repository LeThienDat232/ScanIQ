package com.smartscanner.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartscanner.data.Document
import com.smartscanner.data.DocumentRepository
import com.smartscanner.data.FileStorageManager
import com.smartscanner.data.Folder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class FilesViewModel(private val repository: DocumentRepository) : ViewModel() {

    val folders: StateFlow<List<Folder>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val databaseDocuments: StateFlow<List<Document>> = repository.getRecentDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _downloadFiles = MutableStateFlow<List<Document>>(emptyList())
    val downloadFiles: StateFlow<List<Document>> = _downloadFiles.asStateFlow()

    val recentDocuments: StateFlow<List<Document>> = combine(databaseDocuments, _downloadFiles) { dbDocs, dlFiles ->
        (dbDocs + dlFiles).sortedByDescending { it.createdAt }.take(20)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Document>> = combine(_searchQuery, databaseDocuments, _downloadFiles) { query, dbDocs, dlFiles ->
        if (query.isBlank()) emptyList()
        else {
            (dbDocs + dlFiles)
                .filter { it.title.contains(query, ignoreCase = true) }
                .sortedByDescending { it.createdAt }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        syncDownloads()
    }

    fun syncDownloads() {
        viewModelScope.launch {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val files = downloadDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.map { file ->
                val ext = file.extension.lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                Document(
                    id = file.absolutePath.hashCode(),
                    folderId = -1,
                    title = file.name,
                    filePath = file.absolutePath,
                    fileType = mime,
                    createdAt = file.lastModified()
                )
            }?.sortedByDescending { it.createdAt } ?: emptyList()
            _downloadFiles.value = files
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun insertDocument(folderId: Int?, title: String, filePath: String, fileType: String) {
        viewModelScope.launch {
            val newDoc = Document(
                folderId = folderId,
                title = title,
                filePath = filePath,
                fileType = fileType,
                createdAt = System.currentTimeMillis()
            )
            repository.insertDocument(newDoc)
        }
    }

    fun createFolder(name: String, parentFolderId: Int? = null) {
        viewModelScope.launch {
            // Đảm bảo tên folder cũng không trùng trong cùng 1 cấp
            val existingFolders = folders.value.filter { it.parentFolderId == parentFolderId }
            var uniqueName = name
            var counter = 1
            while (existingFolders.any { it.name.equals(uniqueName, ignoreCase = true) }) {
                uniqueName = "$name($counter)"
                counter++
            }
            repository.insertFolder(uniqueName, parentFolderId)
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            if (document.folderId != -1) {
                repository.deleteDocumentAndFile(document)
            }
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
        }
    }

    fun moveItemsToFolder(items: List<Any>, targetFolderId: Int?) {
        viewModelScope.launch {
            items.forEach { item ->
                when (item) {
                    is Document -> {
                        if (item.folderId != -1) {
                            repository.updateDocument(item.copy(folderId = targetFolderId))
                        }
                    }
                    is Folder -> {
                        if (item.id != targetFolderId) {
                            repository.updateFolder(item.copy(parentFolderId = targetFolderId))
                        }
                    }
                }
            }
        }
    }

    fun createFolderAndMoveDocuments(folderName: String, documents: List<Document>, parentFolderId: Int? = null) {
        viewModelScope.launch {
            val existingFolders = folders.value.filter { it.parentFolderId == parentFolderId }
            var uniqueName = folderName
            var counter = 1
            while (existingFolders.any { it.name.equals(uniqueName, ignoreCase = true) }) {
                uniqueName = "$folderName($counter)"
                counter++
            }
            val folderId = repository.insertFolder(uniqueName, parentFolderId).toInt()
            documents.forEach { doc ->
                if (doc.folderId != -1) {
                    repository.updateDocument(doc.copy(folderId = folderId))
                }
            }
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            val existingFolders = folders.value.filter { it.parentFolderId == folder.parentFolderId && it.id != folder.id }
            var uniqueName = newName
            var counter = 1
            while (existingFolders.any { it.name.equals(uniqueName, ignoreCase = true) }) {
                uniqueName = "$newName($counter)"
                counter++
            }
            repository.updateFolder(folder.copy(name = uniqueName))
        }
    }

    fun renameDocument(context: Context, document: Document, newTitle: String) {
        viewModelScope.launch {
            if (document.folderId == -1) {
                // Tệp ngoài (Downloads)
                val newPath = FileStorageManager.renamePhysicalFile(context, document.filePath, newTitle)
                if (newPath != null) syncDownloads()
            } else {
                // Tệp trong ứng dụng (Internal Storage)
                val newPath = FileStorageManager.renamePhysicalFile(context, document.filePath, newTitle)
                if (newPath != null) {
                    val actualName = File(newPath).name
                    repository.updateDocument(document.copy(title = actualName, filePath = newPath))
                }
            }
        }
    }
}
