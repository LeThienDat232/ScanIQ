package com.smartscanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.smartscanner.data.*
import com.smartscanner.ui.CameraCaptureActivity
import com.smartscanner.ui.FilesViewModel
import com.smartscanner.ui.TextSummarizerActivity
import com.smartscanner.ui.theme.SmartScannerTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val repository = DocumentRepository(database.documentDao(), database.folderDao())
        val viewModelFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return FilesViewModel(repository) as T
            }
        }

        requestStoragePermission()

        enableEdgeToEdge()
        setContent {
            SmartScannerTheme(darkTheme = false, dynamicColor = false) {
                val filesViewModel: FilesViewModel = viewModel(factory = viewModelFactory)
                
                DisposableEffect(Unit) {
                    filesViewModel.syncDownloads()
                    onDispose {}
                }
                
                SmartScannerApp(filesViewModel)
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        }
    }

    fun handleFileImport(uri: Uri, repository: DocumentRepository) {
        lifecycleScope.launch {
            val filePath = FileStorageManager.saveFileFromUri(this@MainActivity, uri)
            if (filePath != null) {
                val fileName = FileStorageManager.getFileName(this@MainActivity, uri) ?: "Imported File"
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                
                val newDoc = Document(
                    folderId = null,
                    title = fileName,
                    filePath = filePath,
                    fileType = mimeType,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertDocument(newDoc)
                Toast.makeText(this@MainActivity, "Imported: $fileName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openFile(filePath: String, fileType: String? = null) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                applicationContext,
                "com.smartscanner.fileprovider",
                file
            )

            val mimeType = fileType ?: getMimeType(filePath)

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension?.lowercase()) ?: "application/octet-stream"
    }
}

private val AppBlue = Color(0xFF3367EF)
private val PageBackground = Color(0xFFF1F1F1)

private enum class BottomTab(val label: String) {
    Home("Home"),
    Files("Files"),
    Tools("Tools"),
    Options("Options"),
}

private enum class ExplorerType {
    Folder, Png, Xls, Csv, Pdf, Doc, Ppt, GenericFile
}

private data class RecentFile(
    val title: String,
    val date: String,
    val type: ExplorerType,
    val highlighted: Boolean = false,
    val filePath: String? = null
)

private data class ExplorerItem(
    val title: String,
    val date: String,
    val type: ExplorerType,
    val originalItem: Any? = null
)

private data class ToolItem(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val color: Color = AppBlue
)

@Composable
private fun SmartScannerApp(viewModel: FilesViewModel) {
    var selectedTab by remember { mutableStateOf(BottomTab.Home) }
    val context = LocalContext.current as MainActivity
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { DocumentRepository(database.documentDao(), database.folderDao()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { context.handleFileImport(it, repository) }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { context.handleFileImport(it, repository) }
    }

    // Launcher for OS Trash Request
    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.syncDownloads()
            Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "pageTransition"
        ) { targetTab ->
            when (targetTab) {
                BottomTab.Home -> HomeScreen(viewModel)
                BottomTab.Files -> FilesScreen(
                    viewModel = viewModel,
                    onUploadFile = { filePickerLauncher.launch("*/*") },
                    onUploadImage = { imagePickerLauncher.launch("image/*") },
                    trashLauncher = trashLauncher
                )
                BottomTab.Tools -> ToolsScreen()
                BottomTab.Options -> OptionsScreen()
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            BottomNavDock(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun HomeScreen(viewModel: FilesViewModel) {
    val documents by viewModel.recentDocuments.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()) }
    val context = LocalContext.current as MainActivity

    Column(modifier = Modifier.fillMaxSize()) {
        BlueHeader(showShortcuts = false, viewModel = viewModel)
        Text(
            text = "Recent files",
            color = Color(0xFF101010),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 6.dp)
        )
        if (documents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 140.dp), contentAlignment = Alignment.Center) {
                Text("No recent files", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(documents.size) { index ->
                    val doc = documents[index]
                    RecentFileRow(
                        file = RecentFile(
                            title = doc.title,
                            date = dateFormat.format(Date(doc.createdAt)),
                            type = mapFileType(doc.fileType),
                            filePath = doc.filePath
                        ),
                        onClick = { context.openFile(doc.filePath, doc.fileType) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesScreen(
    viewModel: FilesViewModel,
    onUploadFile: () -> Unit,
    onUploadImage: () -> Unit,
    trashLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
) {
    val context = LocalContext.current as MainActivity
    val folders by viewModel.folders.collectAsState()
    val dbDocuments by viewModel.databaseDocuments.collectAsState()
    val downloadFiles by viewModel.downloadFiles.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()) }

    var showingDownloads by remember { mutableStateOf(false) }
    var openedFolder by remember { mutableStateOf<Folder?>(null) }
    var selectedItems by remember { mutableStateOf(setOf<Any>()) }
    val isSelectionMode = selectedItems.isNotEmpty()

    // Drag and Drop State
    val folderTargets = remember { mutableStateMapOf<Int, Rect>() }
    var backTarget by remember { mutableStateOf<Rect?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }

    var itemToRename by remember { mutableStateOf<Any?>(null) }
    var renameValue by remember { mutableStateOf("") }

    if (itemToRename != null) {
        val originalName = when (val item = itemToRename) {
            is Folder -> item.name
            is Document -> item.title.substringBeforeLast(".", item.title)
            else -> ""
        }
        val extension = if (itemToRename is Document && (itemToRename as Document).title.contains(".")) {
            "." + (itemToRename as Document).title.substringAfterLast(".")
        } else ""

        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename") },
            text = {
                TextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    suffix = { Text(extension) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalName = renameValue + extension
                    context.lifecycleScope.launch {
                        when (val item = itemToRename) {
                            is Folder -> viewModel.renameFolder(item, finalName)
                            is Document -> {
                                if (item.folderId == -1) {
                                    FileStorageManager.renamePhysicalFile(context, item.filePath, finalName)
                                    viewModel.syncDownloads()
                                } else {
                                    viewModel.renameDocument(item, finalName)
                                }
                            }
                        }
                        itemToRename = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("Cancel") }
            }
        )
    }

    BackHandler(enabled = isSelectionMode || showingDownloads || openedFolder != null) {
        if (isSelectionMode) {
            selectedItems = emptySet()
        } else if (showingDownloads) {
            showingDownloads = false
        } else if (openedFolder != null) {
            val parentId = openedFolder?.parentFolderId
            openedFolder = if (parentId == null) null else folders.find { it.id == parentId }
        }
    }

    val explorerItems = remember(folders, dbDocuments, downloadFiles, showingDownloads, openedFolder) {
        val items = mutableListOf<ExplorerItem>()
        if (showingDownloads) {
            downloadFiles.forEach { doc ->
                items.add(ExplorerItem(doc.title, dateFormat.format(Date(doc.createdAt)), mapFileType(doc.fileType), doc))
            }
        } else if (openedFolder != null) {
            folders.filter { it.parentFolderId == openedFolder!!.id }.forEach { folder ->
                items.add(ExplorerItem(folder.name, dateFormat.format(Date(folder.createdAt)), ExplorerType.Folder, folder))
            }
            dbDocuments.filter { it.folderId == openedFolder!!.id }.forEach { doc ->
                items.add(ExplorerItem(doc.title, dateFormat.format(Date(doc.createdAt)), mapFileType(doc.fileType), doc))
            }
        } else {
            items.add(ExplorerItem("Downloads", "${downloadFiles.size} files synced", ExplorerType.Folder, "VIRTUAL_DOWNLOADS"))
            folders.filter { it.parentFolderId == null }.forEach { folder ->
                items.add(ExplorerItem(folder.name, dateFormat.format(Date(folder.createdAt)), ExplorerType.Folder, folder))
            }
            dbDocuments.filter { it.folderId == null }.forEach { doc ->
                items.add(ExplorerItem(doc.title, dateFormat.format(Date(doc.createdAt)), mapFileType(doc.fileType), doc))
            }
        }
        items
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            BlueHeader(
                showShortcuts = true,
                isSelectionMode = isSelectionMode,
                viewModel = viewModel,
                onUploadFile = onUploadFile,
                onUploadImage = onUploadImage,
                onCreateFolder = { viewModel.createFolder("New Folder", openedFolder?.id) },
                onDeleteSelected = {
                    val itemsList = selectedItems.toList()
                    val docsToTrash = mutableListOf<Uri>()
                    context.lifecycleScope.launch {
                        itemsList.forEach { item ->
                            when (item) {
                                is Document -> {
                                    if (item.folderId == -1) {
                                        FileStorageManager.getContentUriFromPath(context, item.filePath)?.let { uri ->
                                            docsToTrash.add(uri)
                                        }
                                    } else {
                                        viewModel.deleteDocument(item)
                                    }
                                }
                                is Folder -> viewModel.deleteFolder(item)
                            }
                        }
                        if (docsToTrash.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, docsToTrash, true)
                                trashLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                            } catch (e: Exception) {
                                itemsList.filterIsInstance<Document>().filter { it.folderId == -1 }.forEach {
                                    FileStorageManager.deletePhysicalFile(it.filePath)
                                }
                                viewModel.syncDownloads()
                            }
                        } else if (itemsList.any { it is Document && it.folderId == -1 }) {
                            viewModel.syncDownloads()
                        }
                    }
                    selectedItems = emptySet()
                },
                onShareSelected = { /* Handle share */ },
                onMoveToNewFolder = {
                    val selectedDocs = selectedItems.filterIsInstance<Document>()
                    if (selectedDocs.isNotEmpty()) {
                        val count = selectedDocs.size
                        viewModel.createFolderAndMoveDocuments("New Grouped Folder", selectedDocs, openedFolder?.id)
                        Toast.makeText(context, "Moved $count files", Toast.LENGTH_SHORT).show()
                    }
                    selectedItems = emptySet()
                }
            )
            
            if (showingDownloads || openedFolder != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .onGloballyPositioned { backTarget = it.boundsInRoot() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack, 
                        null, 
                        modifier = Modifier.clickable { 
                            if (showingDownloads) {
                                showingDownloads = false
                            } else {
                                val parentId = openedFolder?.parentFolderId
                                openedFolder = if (parentId == null) null else folders.find { it.id == parentId }
                            }
                        }.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (showingDownloads) "Downloads" else openedFolder?.name ?: "Folder", 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (explorerItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 140.dp), contentAlignment = Alignment.Center) {
                    Text("Your storage is empty", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(
                        items = explorerItems,
                        key = {
                            when (val original = it.originalItem) {
                                is Folder -> "folder_${original.id}"
                                is Document -> "doc_${original.id}"
                                else -> it.title
                            }
                        }
                    ) { item ->
                        val isSelected = remember(item.originalItem, selectedItems) {
                            item.originalItem?.let { selectedItems.contains(it) } ?: false
                        }
                        ExplorerGridItem(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode && item.originalItem != null && item.originalItem != "VIRTUAL_DOWNLOADS") {
                                    val current = selectedItems.toMutableSet()
                                    if (isSelected) current.remove(item.originalItem)
                                    else current.add(item.originalItem)
                                    selectedItems = current
                                } else {
                                    when (val original = item.originalItem) {
                                        "VIRTUAL_DOWNLOADS" -> showingDownloads = true
                                        is Folder -> openedFolder = original
                                        is Document -> {
                                            context.openFile(original.filePath, original.fileType)
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode && item.originalItem != null && item.originalItem != "VIRTUAL_DOWNLOADS") {
                                    selectedItems = setOf(item.originalItem)
                                }
                            },
                            onDoubleClick = {
                                if (item.originalItem != null && item.originalItem != "VIRTUAL_DOWNLOADS") {
                                    itemToRename = item.originalItem
                                    renameValue = when (val original = item.originalItem) {
                                        is Folder -> original.name
                                        is Document -> original.title.substringBeforeLast(".", original.title)
                                        else -> ""
                                    }
                                }
                            },
                            onDragStarted = { rootOffset ->
                                dragPosition = rootOffset
                            },
                            onDragMoved = { amount ->
                                dragPosition = dragPosition?.plus(amount)
                            },
                            onDragEnded = {
                                dragPosition?.let { pos ->
                                    val isOverBack = backTarget?.contains(pos) == true
                                    if (isOverBack) {
                                        val parentId = if (showingDownloads) null else openedFolder?.parentFolderId
                                        val count = selectedItems.size
                                        viewModel.moveItemsToFolder(selectedItems.toList(), parentId)
                                        Toast.makeText(context, "Moved $count files", Toast.LENGTH_SHORT).show()
                                        selectedItems = emptySet()
                                    } else {
                                        val targetFolderId = folderTargets.entries.find { it.value.contains(pos) }?.key
                                        if (targetFolderId != null) {
                                            val count = selectedItems.size
                                            viewModel.moveItemsToFolder(selectedItems.toList(), targetFolderId)
                                            Toast.makeText(context, "Moved $count files", Toast.LENGTH_SHORT).show()
                                            selectedItems = emptySet()
                                        }
                                    }
                                }
                                dragPosition = null
                            },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val original = item.originalItem
                                if (original is Folder) {
                                    folderTargets[original.id] = coords.boundsInRoot()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Drag Shadow UI
        dragPosition?.let { pos ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                    .zIndex(100f)
                    .size(80.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .background(AppBlue.copy(alpha = 0.9f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ContentCopy, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("${selectedItems.size}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun mapFileType(mimeType: String): ExplorerType {
    return when {
        mimeType.contains("pdf", ignoreCase = true) -> ExplorerType.Pdf
        mimeType.contains("image", ignoreCase = true) -> ExplorerType.Png
        mimeType.contains("excel", ignoreCase = true) || mimeType.contains("spreadsheet", ignoreCase = true) -> ExplorerType.Xls
        mimeType.contains("csv", ignoreCase = true) -> ExplorerType.Csv
        mimeType.contains("word", ignoreCase = true) || mimeType.contains("officedocument.wordprocessingml", ignoreCase = true) -> ExplorerType.Doc
        mimeType.contains("presentation", ignoreCase = true) -> ExplorerType.Ppt
        else -> ExplorerType.GenericFile
    }
}

@Composable
private fun ToolsScreen() {
    val context = LocalContext.current
    val toolItems = listOf(
        ToolItem("Text Extract", Icons.Outlined.Description, "Convert images to editable text"),
        ToolItem("Text Summarization", Icons.Outlined.Summarize, "AI-powered doc summary", color = Color(0xFFEF5350)),
        ToolItem("Image Edit", Icons.Outlined.Edit, "Crop and filter images"),
        ToolItem("PDF Export", Icons.Outlined.PictureAsPdf, "Save as high quality PDF"),
        ToolItem("File Cleaner", Icons.Outlined.CleaningServices, "Optimize storage"),
        ToolItem("Smart Search", Icons.Outlined.AutoAwesome, "Find text in images"),
    )

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 20.dp, bottom = 110.dp)) {
        Text(
            text = "Magic Tools",
            color = Color(0xFF101010),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(toolItems) { item ->
                ToolCard(item = item) {
                    if (item.title == "Text Summarization") {
                        context.startActivity(Intent(context, TextSummarizerActivity::class.java))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsScreen() {
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
        Text(text = "Options", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BlueHeader(
    showShortcuts: Boolean,
    viewModel: FilesViewModel,
    isSelectionMode: Boolean = false,
    onUploadFile: () -> Unit = {},
    onUploadImage: () -> Unit = {},
    onCreateFolder: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onShareSelected: () -> Unit = {},
    onMoveToNewFolder: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxWidth().background(AppBlue)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 35.dp, bottom = 30.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SearchPill(viewModel)
            if (showShortcuts) {
                Spacer(modifier = Modifier.height(25.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (isSelectionMode) {
                        ShortcutButton(Icons.Outlined.Delete, "Delete", Modifier.weight(1f), onClick = onDeleteSelected)
                        Spacer(Modifier.width(14.dp))
                        ShortcutButton(Icons.Outlined.Share, "Share", Modifier.weight(1f), onClick = onShareSelected)
                        Spacer(Modifier.width(14.dp))
                        ShortcutButton(Icons.AutoMirrored.Outlined.DriveFileMove, "To Folder", Modifier.weight(1f), onClick = onMoveToNewFolder)
                    } else {
                        ShortcutButton(Icons.Outlined.UploadFile, "Upload file", Modifier.weight(1f), onClick = onUploadFile)
                        Spacer(Modifier.width(14.dp))
                        ShortcutButton(Icons.Outlined.ImageSearch, "Upload image", Modifier.weight(1f), onClick = onUploadImage)
                        Spacer(Modifier.width(14.dp))
                        ShortcutButton(Icons.Outlined.CreateNewFolder, "Create folder", Modifier.weight(1f), onClick = onCreateFolder)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPill(viewModel: FilesViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val context = LocalContext.current as MainActivity
    val dateFormat = remember { SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFFF6F6F6), 
            shape = RoundedCornerShape(30.dp), 
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = Color(0xFF171717), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                TextField(
                    value = query,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search for any file!", color = Color.Gray, fontSize = 16.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (query.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, 160),
                onDismissRequest = { viewModel.setSearchQuery("") }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 400.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (results.isEmpty()) {
                        Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No files found", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(results) { doc ->
                                RecentFileRow(
                                    file = RecentFile(
                                        title = doc.title,
                                        date = dateFormat.format(Date(doc.createdAt)),
                                        type = mapFileType(doc.fileType),
                                        filePath = doc.filePath
                                    ),
                                    onClick = { 
                                        viewModel.setSearchQuery("")
                                        context.openFile(doc.filePath, doc.fileType) 
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(
        color = Color(0xFFF4F4F4),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.height(104.dp).clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = AppBlue, modifier = Modifier.size(44.dp))
            Text(label, color = AppBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecentFileRow(file: RecentFile, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(80.dp).scale(0.8f), contentAlignment = Alignment.Center) {
            FileOrFolderIcon(file.type, file.filePath)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(file.date, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerGridItem(
    item: ExplorerItem, 
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false, 
    onClick: () -> Unit = {}, 
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    onDragStarted: (Offset) -> Unit = {},
    onDragMoved: (Offset) -> Unit = {},
    onDragEnded: () -> Unit = {}
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { layoutCoordinates = it }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) AppBlue else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(item, isSelectionMode, isSelected) {
                if (isSelectionMode && isSelected) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val rootOffset = layoutCoordinates?.localToRoot(offset) ?: offset
                            onDragStarted(rootOffset)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragMoved(dragAmount)
                        },
                        onDragEnd = { onDragEnded() },
                        onDragCancel = { onDragEnded() }
                    )
                } else {
                    detectTapGestures(
                        onTap = { currentOnClick() },
                        onDoubleTap = { currentOnDoubleClick() },
                        onLongPress = { currentOnLongClick() }
                    )
                }
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val doc = item.originalItem as? Document
            Box(Modifier.scale(0.9f)) { FileOrFolderIcon(item.type, doc?.filePath) }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.date, color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FileOrFolderIcon(type: ExplorerType, filePath: String? = null) {
    if (type == ExplorerType.Png && filePath != null) {
        AsyncImage(
            model = File(filePath),
            contentDescription = null,
            modifier = Modifier
                .width(80.dp)
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        when (type) {
            ExplorerType.Folder -> FolderGlyph()
            ExplorerType.Png -> FileGlyph("IMG", Color(0xFF5FADE3), Color(0xFF3077BD), Color(0xFF2368AE))
            ExplorerType.Xls -> FileGlyph("XLS", Color(0xFF3DBA7C), Color(0xFF188D56), Color(0xFF127A48))
            ExplorerType.Csv -> FileGlyph("CSV", Color(0xFF58A8E2), Color(0xFF2A73BA), Color(0xFF2163A6))
            ExplorerType.Pdf -> FileGlyph("PDF", Color(0xFFE57373), Color(0xFFC62828), Color(0xFFB71C1C))
            ExplorerType.Doc -> FileGlyph("DOC", Color(0xFF64B5F6), Color(0xFF1976D2), Color(0xFF0D47A1))
            ExplorerType.Ppt -> FileGlyph("PPT", Color(0xFFFFB74D), Color(0xFFF57C00), Color(0xFFE65100))
            ExplorerType.GenericFile -> FileGlyph("FILE", Color(0xFF9E9E9E), Color(0xFF616161), Color(0xFF424242))
        }
    }
}

@Composable
private fun FolderGlyph() {
    Box(Modifier.width(115.dp).height(90.dp)) {
        Box(Modifier.width(45.dp).height(16.dp).offset(12.dp, 4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF4AE21)))
        Box(Modifier.fillMaxWidth().height(76.dp).align(Alignment.BottomStart).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEFCB55)))
    }
}

@Composable
private fun FileGlyph(label: String, bodyColor: Color, foldColor: Color, stripColor: Color) {
    Box(Modifier.width(80.dp).height(100.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(bodyColor))
        Box(Modifier.size(20.dp).align(Alignment.TopEnd).clip(RoundedCornerShape(bottomStart = 4.dp)).background(foldColor))
        Box(Modifier.align(Alignment.Center).width(72.dp).height(28.dp).clip(RoundedCornerShape(4.dp)).background(stripColor), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToolCard(item: ToolItem, onClick: () -> Unit) {
    Surface(
        color = Color.White, 
        shape = RoundedCornerShape(24.dp), 
        shadowElevation = 2.dp, 
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(item.color.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(32.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(item.description, color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun BottomNavDock(selectedTab: BottomTab, onTabSelected: (BottomTab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = BottomTab.entries.toTypedArray()
    val selectedIndex = tabs.indexOf(selectedTab)
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(32.dp))
                .border(0.5.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
        ) {
            val totalWidth = maxWidth
            val spacerWidth = 64.dp
            val tabWidth = (totalWidth - spacerWidth) / 4
            
            val indicatorOffset by animateDpAsState(
                targetValue = when (selectedIndex) {
                    0 -> 0.dp
                    1 -> tabWidth
                    2 -> tabWidth * 2 + spacerWidth
                    3 -> tabWidth * 3 + spacerWidth
                    else -> 0.dp
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "indicatorOffset"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .background(AppBlue, RoundedCornerShape(32.dp))
            )

            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                BottomNavItem(BottomTab.Home, Icons.Outlined.Home, selectedTab == BottomTab.Home, onTabSelected, Modifier.weight(1f))
                BottomNavItem(BottomTab.Files, Icons.Outlined.Folder, selectedTab == BottomTab.Files, onTabSelected, Modifier.weight(1f))
                
                Spacer(modifier = Modifier.width(64.dp))

                BottomNavItem(BottomTab.Tools, Icons.Outlined.Build, selectedTab == BottomTab.Tools, onTabSelected, Modifier.weight(1f))
                BottomNavItem(BottomTab.Options, Icons.Outlined.Settings, selectedTab == BottomTab.Options, onTabSelected, Modifier.weight(1f))
            }
        }
        
        Box(
            modifier = Modifier
                .size(64.dp)
                .offset(y = (-32).dp)
                .shadow(elevation = 10.dp, shape = CircleShape)
                .background(AppBlue, CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { 
                    context.startActivity(Intent(context, CameraCaptureActivity::class.java))
                },
            contentAlignment = Alignment.Center
        ) { 
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(36.dp)) 
        }
    }
}

@Composable
private fun BottomNavItem(tab: BottomTab, icon: ImageVector, isSelected: Boolean, onTabSelected: (BottomTab) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTabSelected(tab) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = tab.label,
            tint = if (isSelected) Color.White else Color.Gray,
            modifier = Modifier.size(26.dp)
        )
    }
}
