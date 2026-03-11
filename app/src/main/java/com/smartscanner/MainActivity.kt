package com.smartscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
private val BottomDockColor = Color(0xFFE7E7E7)

private enum class BottomTab(val label: String) {
    Home("Home"),
    Files("Files"),
    Tools("Tools"),
    Options("Options"),
}

private enum class ExplorerType {
    Folder,
    Png,
    Xls,
    Csv,
}

private data class RecentFile(
    val title: String,
    val date: String,
    val highlighted: Boolean,
)

private data class ExplorerItem(
    val title: String,
    val date: String,
    val type: ExplorerType,
)

private data class ToolItem(
    val title: String,
    val highlighted: Boolean = false,
)

@Composable
private fun SmartScannerApp() {
    var selectedTab by remember { mutableStateOf(BottomTab.Home) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        when (selectedTab) {
            BottomTab.Home -> HomeScreen()
            BottomTab.Files -> FilesScreen()
            BottomTab.Tools -> ToolsScreen()
            BottomTab.Options -> OptionsScreen()
        }

        BottomNavDock(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun HomeScreen() {
    val files = List(9) { index ->
        RecentFile(
            title = "Database design",
            date = "13:29 22/12/2025",
            highlighted = index == 0
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files.size) { index ->
                    RecentFileRow(file = files[index])
                }
            }
        }

    }
}

@Composable
private fun FilesScreen() {
    val items = listOf(
        ExplorerItem("Database design", "13:29 22/12/2025", ExplorerType.Folder),
        ExplorerItem("Data mining", "13:29 22/12/2025", ExplorerType.Folder),
        ExplorerItem("Data structure\nand algorithm", "13:29 22/12/2025", ExplorerType.Folder),
        ExplorerItem("Database\nimage.png", "13:29 22/12/2025", ExplorerType.Png),
        ExplorerItem("Database.xls", "13:29 22/12/2025", ExplorerType.Xls),
        ExplorerItem("Database.csv", "13:29 22/12/2025", ExplorerType.Csv),
        ExplorerItem("Database design", "13:29 22/12/2025", ExplorerType.Folder),
        ExplorerItem("Database design", "13:29 22/12/2025", ExplorerType.Folder),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        BlueHeader(showShortcuts = true)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 28.dp,
                end = 28.dp,
                top = 24.dp,
                bottom = 110.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
        ToolItem("Text extract", highlighted = true),
        ToolItem("Image edit"),
        ToolItem("PDF export"),
        ToolItem("File cleaner"),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, bottom = 110.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .height(310.dp)
                    .padding(horizontal = 72.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                items(toolItems) { item ->
                    ToolCard(item = item)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }

    }
}

@Composable
private fun OptionsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Options",
            color = Color(0xFF202020),
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BlueHeader(showShortcuts: Boolean) {
    val headerHeight = if (showShortcuts) 230.dp else 150.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(AppBlue)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SearchPill()
            if (showShortcuts) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ShortcutButton(
                        icon = Icons.Outlined.UploadFile,
                        label = "Upload file",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    ShortcutButton(
                        icon = Icons.Outlined.ImageSearch,
                        label = "Upload image",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    ShortcutButton(
                        icon = Icons.Outlined.CreateNewFolder,
                        label = "Create folder",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPill() {
    Surface(
        color = Color(0xFFF6F6F6),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = Color(0xFF171717),
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Search for any file!",
                color = Color(0xFF171717),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ShortcutButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xFFF4F4F4),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.height(104.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AppBlue,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = label,
                color = AppBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RecentFileRow(file: RecentFile) {
    val borderModifier = if (file.highlighted) {
        Modifier.border(2.dp, AppBlue, RoundedCornerShape(1.dp))
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color(0xFFD7D7D7))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.title,
                color = Color(0xFF1A1A1A),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = file.date,
                color = Color(0xFF242424),
                fontSize = 12.sp
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(1.5.dp, Color(0xFF494949))
        )
    }
}

@Composable
private fun ExplorerGridItem(item: ExplorerItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (item.type) {
            ExplorerType.Folder -> FolderGlyph()
            ExplorerType.Png -> FileGlyph("PNG", Color(0xFF5FADE3), Color(0xFF3077BD), Color(0xFF2368AE))
            ExplorerType.Xls -> FileGlyph("XLS", Color(0xFF3DBA7C), Color(0xFF188D56), Color(0xFF127A48))
            ExplorerType.Csv -> FileGlyph("CSV", Color(0xFF58A8E2), Color(0xFF2A73BA), Color(0xFF2163A6))
        }
        Text(
            text = item.title,
            color = Color(0xFF1A1A1A),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.date,
            color = Color(0xFF252525),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun FolderGlyph() {
    Box(
        modifier = Modifier
            .width(122.dp)
            .height(88.dp)
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(14.dp)
                .offset(x = 14.dp, y = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF4AE21))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEFCB55))
        )
    }
}

@Composable
private fun FileGlyph(
    label: String,
    bodyColor: Color,
    foldColor: Color,
    stripColor: Color,
) {
    Box(
        modifier = Modifier
            .width(92.dp)
            .height(108.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(bodyColor)
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(bottomStart = 4.dp))
                .background(foldColor)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(84.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(stripColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ToolCard(item: ToolItem) {
    val borderModifier = if (item.highlighted) {
        Modifier.border(2.dp, AppBlue)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .then(borderModifier)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AppBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.UploadFile,
                contentDescription = item.title,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BottomNavDock(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = BottomDockColor,
            shape = RoundedCornerShape(34.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BottomNavItem(
                    tab = BottomTab.Home,
                    icon = Icons.Outlined.Home,
                    selected = selectedTab == BottomTab.Home,
                    onTabSelected = onTabSelected
                )
                BottomNavItem(
                    tab = BottomTab.Files,
                    icon = Icons.Outlined.Folder,
                    selected = selectedTab == BottomTab.Files,
                    onTabSelected = onTabSelected
                )
                Spacer(modifier = Modifier.width(56.dp))
                BottomNavItem(
                    tab = BottomTab.Tools,
                    icon = Icons.Outlined.Build,
                    selected = selectedTab == BottomTab.Tools,
                    onTabSelected = onTabSelected
                )
                BottomNavItem(
                    tab = BottomTab.Options,
                    icon = Icons.Outlined.Settings,
                    selected = selectedTab == BottomTab.Options,
                    onTabSelected = onTabSelected
                )
            }
        }

        Surface(
            color = AppBlue,
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(60.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
                .clickable { }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: BottomTab,
    icon: ImageVector,
    selected: Boolean,
    onTabSelected: (BottomTab) -> Unit,
) {
    val background = if (selected) AppBlue else Color.Transparent
    val iconColor = if (selected) Color.White else Color(0xFF2B2B2B)
    val textColor = if (selected) Color.White else AppBlue

    Box(
        modifier = Modifier
            .width(if (selected) 84.dp else 68.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(background)
            .clickable { onTabSelected(tab) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tab.label,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = tab.label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
