package com.smartscanner.ui

import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartscanner.data.Document
import com.smartscanner.data.DocumentRepository
import com.smartscanner.data.Folder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class FilesViewModel(private val repository: DocumentRepository) : ViewModel() {

    // Database Folders
    val folders: StateFlow<List<Folder>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Database Documents
    val databaseDocuments: StateFlow<List<Document>> = repository.getRecentDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Synced Files from Downloads folder
    private val _downloadFiles = MutableStateFlow<List<Document>>(emptyList())
    val downloadFiles: StateFlow<List<Document>> = _downloadFiles.asStateFlow()

    // Combined Recent Files (DB + Downloads)
    val recentDocuments: StateFlow<List<Document>> = combine(databaseDocuments, _downloadFiles) { dbDocs, dlFiles ->
        (dbDocs + dlFiles).sortedByDescending { it.createdAt }.take(20)
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
                    folderId = -1, // Mark as "in Downloads"
                    title = file.name,
                    filePath = file.absolutePath,
                    fileType = mime,
                    createdAt = file.lastModified()
                )
            }?.sortedByDescending { it.createdAt } ?: emptyList()
            _downloadFiles.value = files
        }
    }

    fun createFolder(name: String, parentFolderId: Int? = null) {
        viewModelScope.launch {
            repository.insertFolder(name, parentFolderId)
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

    fun moveDocumentsToFolder(documents: List<Document>, folderId: Int) {
        viewModelScope.launch {
            documents.forEach { doc ->
                repository.updateDocument(doc.copy(folderId = folderId))
            }
        }
    }

    fun createFolderAndMoveDocuments(folderName: String, documents: List<Document>, parentFolderId: Int? = null) {
        viewModelScope.launch {
            val folderId = repository.insertFolder(folderName, parentFolderId).toInt()
            moveDocumentsToFolder(documents, folderId)
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            repository.updateFolder(folder.copy(name = newName))
        }
    }

    fun renameDocument(document: Document, newTitle: String) {
        viewModelScope.launch {
            repository.updateDocument(document.copy(title = newTitle))
        }
    }
}
