package com.smartscanner

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartscanner.ui.theme.SmartScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartScannerTheme(darkTheme = false, dynamicColor = false) {
                SmartScannerApp()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SmartScannerPreview() {
    SmartScannerTheme(darkTheme = false, dynamicColor = false) {
        SmartScannerApp()
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
    Folder, Png, Xls, Csv, Pdf, Doc, Ppt,
}

private data class RecentFile(
    val title: String,
    val date: String,
    val type: ExplorerType,
    val highlighted: Boolean = false,
)

private data class ExplorerItem(
    val title: String,
    val date: String,
    val type: ExplorerType,
)

private data class ToolItem(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val color: Color = AppBlue
)

@Composable
private fun SmartScannerApp() {
    var selectedTab by remember { mutableStateOf(BottomTab.Home) }

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
                BottomTab.Home -> HomeScreen()
                BottomTab.Files -> FilesScreen()
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
private fun HomeScreen() {
    val recentFiles = listOf(
        RecentFile("Final_project_final_version_v2.pdf", "13:29 22/12/2025", ExplorerType.Pdf),
        RecentFile("Dataset_2024_comprehensive.csv", "10:15 21/12/2025", ExplorerType.Csv),
        RecentFile("Business_plan_internal.doc", "16:45 20/12/2025", ExplorerType.Doc),
        RecentFile("Financial_report_Q4.xls", "09:30 19/12/2025", ExplorerType.Xls),
        RecentFile("Scanner_result_001.png", "11:05 17/12/2025", ExplorerType.Png),
        RecentFile("Meeting_notes.pdf", "14:20 16/12/2025", ExplorerType.Pdf),
        RecentFile("Icon_export.png", "08:15 15/12/2025", ExplorerType.Png),
        RecentFile("Invoice_2025.xls", "17:40 14/12/2025", ExplorerType.Xls),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        BlueHeader(showShortcuts = false)
        Text(
            text = "Recent files",
            color = Color(0xFF101010),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 6.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recentFiles.size) { index ->
                RecentFileRow(file = recentFiles[index])
            }
        }
    }
}

@Composable
private fun FilesScreen() {
    val items = listOf(
        ExplorerItem("Projects", "13:29 22/12/2025", ExplorerType.Folder),
        ExplorerItem("Documents", "13:29 22/12/2025", ExplorerType.Folder),
        ExplorerItem("resume_cv.pdf", "13:29 22/12/2025", ExplorerType.Pdf),
        ExplorerItem("budget_2025.xls", "13:29 22/12/2025", ExplorerType.Xls),
        ExplorerItem("photo_scan.png", "13:29 22/12/2025", ExplorerType.Png),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        BlueHeader(showShortcuts = true)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(items) { item ->
                ExplorerGridItem(item = item)
            }
        }
    }
}

@Composable
private fun ToolsScreen() {
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
            items(toolItems) { item -> ToolCard(item = item) }
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
private fun BlueHeader(showShortcuts: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().background(AppBlue)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 35.dp, bottom = 30.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SearchPill()
            if (showShortcuts) {
                Spacer(modifier = Modifier.height(25.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ShortcutButton(Icons.Outlined.UploadFile, "Upload file", Modifier.weight(1f))
                    Spacer(Modifier.width(14.dp))
                    ShortcutButton(Icons.Outlined.ImageSearch, "Upload image", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(14.dp))
                    ShortcutButton(Icons.Outlined.CreateNewFolder, "Create folder", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SearchPill() {
    Surface(color = Color(0xFFF6F6F6), shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = Color(0xFF171717), modifier = Modifier.size(30.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search for any file!", color = Color(0xFF171717), fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ShortcutButton(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFFF4F4F4), shape = RoundedCornerShape(22.dp), modifier = modifier.height(104.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = AppBlue, modifier = Modifier.size(44.dp))
            Text(label, color = AppBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecentFileRow(file: RecentFile) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(80.dp).scale(0.8f), contentAlignment = Alignment.Center) {
            FileOrFolderIcon(file.type)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(file.date, color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.width(20.dp))
        Box(Modifier.size(22.dp).border(1.5.dp, Color.Gray))
    }
}

@Composable
private fun ExplorerGridItem(item: ExplorerItem) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.scale(0.9f)) { FileOrFolderIcon(item.type) }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.date, color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FileOrFolderIcon(type: ExplorerType) {
    when (type) {
        ExplorerType.Folder -> FolderGlyph()
        ExplorerType.Png -> FileGlyph("PNG", Color(0xFF5FADE3), Color(0xFF3077BD), Color(0xFF2368AE))
        ExplorerType.Xls -> FileGlyph("XLS", Color(0xFF3DBA7C), Color(0xFF188D56), Color(0xFF127A48))
        ExplorerType.Csv -> FileGlyph("CSV", Color(0xFF58A8E2), Color(0xFF2A73BA), Color(0xFF2163A6))
        ExplorerType.Pdf -> FileGlyph("PDF", Color(0xFFE57373), Color(0xFFC62828), Color(0xFFB71C1C))
        ExplorerType.Doc -> FileGlyph("DOC", Color(0xFF64B5F6), Color(0xFF1976D2), Color(0xFF0D47A1))
        ExplorerType.Ppt -> FileGlyph("PPT", Color(0xFFFFB74D), Color(0xFFF57C00), Color(0xFFE65100))
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
private fun ToolCard(item: ToolItem) {
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().height(180.dp).clickable {}) {
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
    val tabs = BottomTab.values()
    val selectedIndex = tabs.indexOf(selectedTab)

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
                .clickable { },
            contentAlignment = Alignment.Center
        ) { 
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(36.dp)) 
        }
    }
}

@Composable
private fun BottomNavItem(tab: BottomTab, icon: ImageVector, selected: Boolean, onTabSelected: (BottomTab) -> Unit, modifier: Modifier = Modifier) {
    val animatedContentColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Black.copy(alpha = 0.6f),
        animationSpec = tween(300),
        label = "contentColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onTabSelected(tab) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = animatedContentColor, 
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = tab.label, 
                color = animatedContentColor, 
                fontSize = 11.sp, 
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
