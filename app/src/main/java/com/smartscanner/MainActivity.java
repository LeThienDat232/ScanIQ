package com.smartscanner;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.smartscanner.data.Document;
import com.smartscanner.data.DocumentRepository;
import com.smartscanner.data.FileStorageManager;
import com.smartscanner.data.Folder;
import com.smartscanner.ui.CameraCaptureActivity;
import com.smartscanner.ui.FilesViewModel;
import com.smartscanner.ui.TextSummarizerActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "smart_scanner_options";
    private static final String PREF_THEME = "theme";
    private static final String PREF_LANGUAGE = "language";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String LANGUAGE_EN = "en";
    private static final String LANGUAGE_VI = "vi";

    private static final int APP_BLUE = Color.rgb(51, 103, 239);
    private static final int PAGE_BACKGROUND = Color.rgb(247, 248, 251);
    private static final int CARD_BACKGROUND = Color.WHITE;
    private static final int CARD_STROKE = Color.rgb(226, 232, 240);
    private static final int TEXT_DARK = Color.rgb(32, 33, 36);
    private static final int TEXT_MUTED = Color.rgb(107, 114, 128);
    private static final String DRAG_LABEL = "smartscanner-selection-drag";
    private static final String DOCUMENT_KEY_PREFIX = "DOC:";
    private static final String DOWNLOAD_KEY_PREFIX = "DOWNLOAD:";
    private static final String FOLDER_KEY_PREFIX = "FOLDER:";
    private static final int DOCUMENT_SCANNER_PAGE_LIMIT = 25;

    private FilesViewModel viewModel;
    private FrameLayout root;
    private LinearLayout contentContainer;
    private FrameLayout bottomNavDock;
    private LinearLayout bottomNavItems;
    private View navIndicator;
    private FloatingActionButton cameraButton;

    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<IntentSenderRequest> documentScannerLauncher;

    private BottomTab selectedTab = BottomTab.HOME;
    private boolean showingDownloads = false;
    @Nullable
    private Folder openedFolder = null;
    private final Set<String> selectedItemKeys = new HashSet<>();
    private final Map<String, MaterialCardView> explorerCardsByKey = new HashMap<>();

    private List<Folder> cachedFolders = new ArrayList<>();
    private List<Document> cachedDatabaseDocuments = new ArrayList<>();
    private List<Document> cachedDownloadFiles = new ArrayList<>();
    private List<Document> cachedRecentDocuments = new ArrayList<>();
    private List<Document> cachedSearchResults = new ArrayList<>();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
    private final List<TextView> tabButtons = new ArrayList<>();
    @Nullable
    private BottomTab renderedTab = null;
    @Nullable
    private BottomTab indicatorTab = null;
    private PopupWindow searchPopup;
    private LinearLayout searchPopupContent;
    private EditText searchAnchor;
    private SharedPreferences optionsPrefs;
    private String selectedTheme = THEME_LIGHT;
    private String selectedLanguage = LANGUAGE_EN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        optionsPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedTheme = optionsPrefs.getString(PREF_THEME, THEME_LIGHT);
        selectedLanguage = optionsPrefs.getString(PREF_LANGUAGE, LANGUAGE_EN);
        AppCompatDelegate.setDefaultNightMode(
                isDarkTheme() ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(FilesViewModel.class);

        requestStoragePermission();
        setupActivityResultLaunchers();
        buildRootUi();
        observeViewModel();
        renderCurrentTab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (contentContainer != null && selectedTab == BottomTab.OPTIONS) {
            renderCurrentTab();
        }
    }

    private void setupActivityResultLaunchers() {
        documentScannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleDocumentScannerResult(GmsDocumentScanningResult.fromActivityResultIntent(result.getData()));
                    }
                }
        );

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleFileImport(uri);
                    }
                }
        );

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleFileImport(uri);
                    }
                }
        );
    }

    private void launchDocumentScanner() {
        if (documentScannerLauncher == null) {
            openBasicCameraFallback(null);
            return;
        }

        setCameraButtonBusy(true);
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(DOCUMENT_SCANNER_PAGE_LIMIT)
                .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        scanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {
                    setCameraButtonBusy(false);
                    try {
                        documentScannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build());
                    } catch (Exception e) {
                        openBasicCameraFallback(e);
                    }
                })
                .addOnFailureListener(e -> {
                    setCameraButtonBusy(false);
                    openBasicCameraFallback(e);
                });
    }

    private void handleDocumentScannerResult(@Nullable GmsDocumentScanningResult scannerResult) {
        if (scannerResult == null) {
            Toast.makeText(this, tr("Scan failed", "Quét thất bại"), Toast.LENGTH_SHORT).show();
            return;
        }

        Integer targetFolderId = currentFolderId();
        DocumentRepository.DATABASE_EXECUTOR.execute(() -> saveDocumentScannerResult(scannerResult, targetFolderId));
    }

    private void saveDocumentScannerResult(GmsDocumentScanningResult scannerResult, @Nullable Integer targetFolderId) {
        List<Uri> pageImageUris = getScannerPageUris(scannerResult);
        GmsDocumentScanningResult.Pdf pdf = scannerResult.getPdf();
        long timestamp = System.currentTimeMillis();
        int pageCount = pdf != null && pdf.getPageCount() > 0 ? pdf.getPageCount() : pageImageUris.size();

        String filePath = null;
        String mimeType = "application/pdf";
        if (pdf != null && pdf.getUri() != null) {
            filePath = FileStorageManager.saveFileFromUri(
                    this,
                    pdf.getUri(),
                    buildScanFileName(timestamp, pageCount, ".pdf")
            );
        } else if (!pageImageUris.isEmpty()) {
            mimeType = "image/jpeg";
            filePath = FileStorageManager.saveFileFromUri(
                    this,
                    pageImageUris.get(0),
                    buildScanFileName(timestamp, pageCount, ".jpg")
            );
        }

        if (filePath == null) {
            runOnUiThread(() -> Toast.makeText(this, tr("Scan save failed", "Lưu bản quét thất bại"), Toast.LENGTH_SHORT).show());
            return;
        }

        File savedFile = new File(filePath);
        String title = savedFile.getName();
        String finalFilePath = filePath;
        String finalMimeType = mimeType;
        runOnUiThread(() -> viewModel.insertScannedDocument(
                targetFolderId,
                title,
                finalFilePath,
                finalMimeType,
                pageImageUris,
                documentId -> {
                    String message = pageCount > 1
                            ? tr("Scanned ", "Đã quét ") + pageCount + tr(" pages: ", " trang: ") + title
                            : tr("Scanned: ", "Đã quét: ") + title;
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    if (selectedTab == BottomTab.FILES) {
                        renderCurrentTab();
                    }
                }
        ));
    }

    private List<Uri> getScannerPageUris(GmsDocumentScanningResult scannerResult) {
        List<Uri> pageUris = new ArrayList<>();
        List<GmsDocumentScanningResult.Page> pages = scannerResult.getPages();
        if (pages == null) {
            return pageUris;
        }
        for (GmsDocumentScanningResult.Page page : pages) {
            if (page != null && page.getImageUri() != null) {
                pageUris.add(page.getImageUri());
            }
        }
        return pageUris;
    }

    private String buildScanFileName(long timestamp, int pageCount, String extension) {
        String pageSuffix = pageCount > 1 ? "_" + pageCount + "_pages" : "";
        return "SCAN_" + timestamp + pageSuffix + extension;
    }

    private void openBasicCameraFallback(@Nullable Exception exception) {
        if (exception != null) {
            Log.w(TAG, "Document scanner unavailable; opening basic camera", exception);
            Toast.makeText(
                    this,
                    tr("Document scanner unavailable. Opening basic camera.", "Máy quét tài liệu không khả dụng. Đang mở camera cơ bản."),
                    Toast.LENGTH_SHORT
            ).show();
        }
        startActivity(new Intent(this, CameraCaptureActivity.class));
    }

    private void setCameraButtonBusy(boolean busy) {
        if (cameraButton == null) {
            return;
        }
        cameraButton.setEnabled(!busy);
        cameraButton.setAlpha(busy ? 0.55f : 1f);
    }

    private void observeViewModel() {
        viewModel.getFolders().observe(this, folders -> {
            cachedFolders = safeFolderList(folders);
            pruneSelectedItems();
            renderCurrentTab();
        });
        viewModel.getDatabaseDocuments().observe(this, documents -> {
            cachedDatabaseDocuments = safeDocumentList(documents);
            pruneSelectedItems();
            renderCurrentTab();
        });
        viewModel.getDownloadFiles().observe(this, documents -> {
            cachedDownloadFiles = safeDocumentList(documents);
            pruneSelectedItems();
            renderCurrentTab();
        });
        viewModel.getRecentDocuments().observe(this, documents -> {
            cachedRecentDocuments = safeDocumentList(documents);
            if (selectedTab == BottomTab.HOME) {
                renderCurrentTab();
            }
        });
        viewModel.getSearchResults().observe(this, documents -> {
            cachedSearchResults = safeDocumentList(documents);
            updateSearchPopup();
        });
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }

    private void buildRootUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(pageBackground());

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        bottomNavDock = createBottomNavDock();
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(74),
                Gravity.BOTTOM
        );
        dockParams.setMargins(dp(20), 0, dp(20), getNavigationBarHeight() + dp(16));
        root.addView(bottomNavDock, dockParams);

        cameraButton = new FloatingActionButton(this);
        cameraButton.setImageResource(R.drawable.ic_camera);
        cameraButton.setColorFilter(Color.WHITE);
        cameraButton.setBackgroundTintList(ColorStateList.valueOf(appBlue()));
        cameraButton.setCompatElevation(dp(18));
        cameraButton.setElevation(dp(18));
        cameraButton.setTranslationZ(dp(18));
        cameraButton.setOnClickListener(v -> launchDocumentScanner());
        FrameLayout.LayoutParams cameraParams = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cameraParams.setMargins(0, 0, 0, getNavigationBarHeight() + dp(61));
        root.addView(cameraButton, cameraParams);
        cameraButton.bringToFront();

        setContentView(root);
    }

    private FrameLayout createBottomNavDock() {
        FrameLayout dock = new FrameLayout(this);
        dock.setPadding(0, 0, 0, 0);
        dock.setBackground(createGlassBackground());
        dock.setElevation(dp(8));
        dock.setTranslationZ(dp(8));

        navIndicator = new View(this);
        navIndicator.setBackground(createRoundedBackground(navIndicatorColor(), dp(30)));
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                dp(1),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START | Gravity.CENTER_VERTICAL
        );
        dock.addView(navIndicator, indicatorParams);

        bottomNavItems = new LinearLayout(this);
        bottomNavItems.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavItems.setGravity(Gravity.CENTER);

        tabButtons.clear();
        for (BottomTab tab : BottomTab.values()) {
            TextView button = new TextView(this);
            button.setText(tabLabel(tab));
            button.setGravity(Gravity.CENTER);
            button.setTextSize(16);
            button.setIncludeFontPadding(false);
            button.setSingleLine(true);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setOnClickListener(v -> {
                selectedTab = tab;
                selectedItemKeys.clear();
                renderCurrentTab();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            bottomNavItems.addView(button, params);
            tabButtons.add(button);
        }
        dock.addView(bottomNavItems, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return dock;
    }

    private void updateBottomNav() {
        BottomTab[] tabs = BottomTab.values();
        for (int i = 0; i < tabButtons.size(); i++) {
            boolean isSelected = tabs[i] == selectedTab;
            TextView button = tabButtons.get(i);
            button.setText(tabLabel(tabs[i]));
            button.setTextColor(isSelected ? appBlue() : navTextColor());
            button.setTypeface(Typeface.DEFAULT, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            button.setBackgroundColor(Color.TRANSPARENT);
        }

        boolean animate = indicatorTab != null && indicatorTab != selectedTab;
        indicatorTab = selectedTab;
        bottomNavDock.post(() -> moveNavIndicator(animate));
    }

    private void moveNavIndicator(boolean animate) {
        if (navIndicator == null || bottomNavDock == null || bottomNavDock.getWidth() == 0) {
            return;
        }

        int tabCount = BottomTab.values().length;
        float tabWidth = bottomNavDock.getWidth() / (float) tabCount;
        if (tabWidth <= 0f) {
            return;
        }

        int tabIndex = selectedTab.ordinal();
        int target = Math.round(tabIndex * tabWidth);
        int indicatorWidth = tabIndex == tabCount - 1
                ? bottomNavDock.getWidth() - target
                : Math.round(tabWidth);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) navIndicator.getLayoutParams();
        params.width = indicatorWidth;
        params.height = bottomNavDock.getHeight();
        params.leftMargin = 0;
        navIndicator.setLayoutParams(params);

        if (animate) {
            navIndicator.animate()
                    .translationX(target)
                    .setDuration(240)
                    .setInterpolator(android.view.animation.AnimationUtils.loadInterpolator(
                            this,
                            android.R.interpolator.fast_out_slow_in
                    ))
                    .start();
        } else {
            navIndicator.setTranslationX(target);
        }
    }

    private void renderCurrentTab() {
        if (contentContainer == null) {
            return;
        }

        boolean tabChanged = renderedTab != null && selectedTab != renderedTab;
        int slideDirection = renderedTab == null
                ? 0
                : Integer.compare(selectedTab.ordinal(), renderedTab.ordinal());

        updateBottomNav();
        updateSystemBars();

        if (tabChanged) {
            renderCurrentTabWithTransition(slideDirection);
        } else {
            contentContainer.animate().cancel();
            contentContainer.removeAllViews();
            renderSelectedTab();
            contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
            contentContainer.setAlpha(1f);
            contentContainer.setTranslationX(0f);
        }
        renderedTab = selectedTab;
    }

    private void renderSelectedTab() {
        switch (selectedTab) {
            case HOME:
                renderHomeScreen();
                break;
            case FILES:
                renderFilesScreen();
                break;
            case TOOLS:
                renderToolsScreen();
                break;
            case OPTIONS:
                renderOptionsScreen();
                break;
        }
    }

    private void renderCurrentTabWithTransition(int direction) {
        LinearLayout oldContainer = contentContainer;
        oldContainer.animate().cancel();

        LinearLayout newContainer = new LinearLayout(this);
        newContainer.setOrientation(LinearLayout.VERTICAL);
        newContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        root.addView(newContainer, 1, params);

        contentContainer = newContainer;
        renderSelectedTab();
        animatePageTransition(oldContainer, newContainer, direction);
    }

    private void animatePageTransition(LinearLayout oldContainer, LinearLayout newContainer, int direction) {
        float width = root != null && root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        float sign = direction >= 0 ? 1f : -1f;
        float newStartX = width * sign;
        float oldEndX = -width * sign;

        oldContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        oldContainer.setAlpha(1f);
        oldContainer.setTranslationX(0f);
        newContainer.setAlpha(1f);
        newContainer.setTranslationX(newStartX);

        android.view.animation.Interpolator interpolator =
                android.view.animation.AnimationUtils.loadInterpolator(this, android.R.interpolator.fast_out_slow_in);

        oldContainer.animate()
                .translationX(oldEndX)
                .alpha(1f)
                .setDuration(340)
                .setInterpolator(interpolator)
                .setListener(null)
                .start();

        newContainer.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(340)
                .setInterpolator(interpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        oldContainer.animate().setListener(null);
                        root.removeView(oldContainer);
                        newContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                        newContainer.setTranslationX(0f);
                        newContainer.setAlpha(1f);
                        bottomNavDock.bringToFront();
                        cameraButton.bringToFront();
                    }
                })
                .start();
    }

    private void updateSystemBars() {
        getWindow().setNavigationBarColor(pageBackground());

        int flags = 0;
        if (selectedTab == BottomTab.HOME || selectedTab == BottomTab.FILES) {
            getWindow().setStatusBarColor(appBlue());
        } else {
            getWindow().setStatusBarColor(pageBackground());
            if (!isDarkTheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }

        if (!isDarkTheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void renderHomeScreen() {
        renderHomeScreen(false);
    }

    private void renderHomeScreen(boolean includeTemporaryShortcuts) {
        contentContainer.addView(createHeader(false, includeTemporaryShortcuts));
        renderHomeBody(contentContainer);
    }

    private void renderHomeBody(LinearLayout target) {
        target.addView(createSectionTitle(tr("Recent files", "Tệp gần đây"), 24));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(6), dp(18), getNavigationBarHeight() + dp(154));
        scrollView.addView(list);

        if (cachedRecentDocuments.isEmpty()) {
            list.addView(createEmptyState(tr("No recent files", "Chưa có tệp gần đây")));
        } else {
            for (Document document : cachedRecentDocuments) {
                list.addView(createDocumentRow(document));
            }
        }

        target.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
    }

    private void renderFilesScreen() {
        contentContainer.addView(createHeader(true));
        renderFilesBody(contentContainer);
    }

    private void renderFilesBody(LinearLayout target) {
        if (showingDownloads || openedFolder != null) {
            target.addView(createBackRow());
        }

        explorerCardsByKey.clear();
        List<ExplorerItem> explorerItems = buildExplorerItems();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(14), dp(18), getNavigationBarHeight() + dp(158));
        scrollView.addView(list);

        if (explorerItems.isEmpty()) {
            list.addView(createEmptyState(tr("Your storage is empty", "Kho lưu trữ đang trống")));
        } else {
            for (int i = 0; i < explorerItems.size(); i += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);

                row.addView(createExplorerCard(explorerItems.get(i)), new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                ));

                Space gap = new Space(this);
                row.addView(gap, new LinearLayout.LayoutParams(dp(14), 1));

                if (i + 1 < explorerItems.size()) {
                    row.addView(createExplorerCard(explorerItems.get(i + 1)), new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    ));
                } else {
                    Space filler = new Space(this);
                    row.addView(filler, new LinearLayout.LayoutParams(0, 1, 1f));
                }

                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                rowParams.setMargins(0, 0, 0, dp(14));
                list.addView(row, rowParams);
            }
        }

        target.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
    }

    private void renderToolsScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), getStatusBarHeight() + dp(18), dp(18), getNavigationBarHeight() + dp(150));

        TextView title = createSectionTitle(tr("Magic Tools", "Công cụ thông minh"), 28);
        title.setPadding(dp(2), dp(8), dp(2), dp(16));
        container.addView(title);

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        rowOne.addView(createToolCard(tr("PDF Converter", "Chuyển PDF"), tr("Merge scans into one PDF", "Gộp bản quét thành một PDF"), Color.rgb(229, 115, 115),
                R.drawable.ic_magic_pdf,
                v -> showPdfImagePicker()), weightedCardParams(true));
        rowOne.addView(createToolCard(tr("Text Extract", "Trích xuất chữ"), tr("Convert images to editable text", "Chuyển ảnh thành văn bản"), appBlue(),
                R.drawable.ic_magic_ocr,
                v -> showOcrImagePicker()), weightedCardParams(false));
        container.addView(rowOne);

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        rowTwo.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 0.5f));
        rowTwo.addView(createToolCard(tr("Text Summarizer", "Tóm tắt văn bản"), tr("Summarize long text with backend API", "Tóm tắt văn bản dài bằng API"), Color.rgb(61, 186, 124),
                R.drawable.ic_magic_summary,
                v -> startActivity(new Intent(this, TextSummarizerActivity.class))), new LinearLayout.LayoutParams(0, dp(180), 1f));
        rowTwo.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 0.5f));
        LinearLayout.LayoutParams rowTwoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowTwoParams.setMargins(0, dp(16), 0, 0);
        container.addView(rowTwo, rowTwoParams);

        contentContainer.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void renderOptionsScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(pageBackground());

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), getStatusBarHeight() + dp(18), dp(18), getNavigationBarHeight() + dp(150));
        scrollView.addView(container, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = createSectionTitle(tr("Options", "Tùy chọn"), 28);
        title.setPadding(dp(2), dp(8), dp(2), dp(16));
        container.addView(title);

        container.addView(createOptionRow(
                tr("Theme", "Giao diện"),
                currentThemeLabel(),
                tr("Choose the app appearance.", "Chọn giao diện của ứng dụng."),
                v -> showThemeDialog()
        ));
        container.addView(createOptionRow(
                tr("Language", "Ngôn ngữ"),
                currentLanguageLabel(),
                tr("Choose English or Vietnamese text.", "Chọn hiển thị tiếng Anh hoặc tiếng Việt."),
                v -> showLanguageDialog()
        ));
        container.addView(createOptionRow(
                tr("Permissions", "Quyền ứng dụng"),
                permissionSummary(),
                tr("Camera, storage, and notifications used by SmartScanner.", "Máy ảnh, bộ nhớ và thông báo mà SmartScanner sử dụng."),
                v -> showPermissionsDialog()
        ));

        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private View createOptionRow(String title, String value, String description, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(12));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(cardStroke());
        card.setCardBackgroundColor(cardBackground());
        card.setOnClickListener(listener);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(16), dp(14), dp(16));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(textPrimary());
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textColumn.addView(titleView);

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextColor(textMuted());
        descView.setTextSize(13);
        descView.setMaxLines(2);
        descView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, dp(4), 0, 0);
        textColumn.addView(descView, descParams);

        row.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(appBlue());
        valueView.setTextSize(13);
        valueView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        valueView.setPadding(dp(12), dp(7), dp(12), dp(7));
        valueView.setBackground(createRoundedBackground(optionValueBackground(), dp(16)));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        valueParams.setMargins(dp(12), 0, 0, 0);
        row.addView(valueView, valueParams);

        TextView chevron = new TextView(this);
        chevron.setText(">");
        chevron.setTextColor(textMuted());
        chevron.setTextSize(22);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));

        card.addView(row);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);
        return card;
    }

    private void showThemeDialog() {
        String[] themes = {tr("Light", "Sáng"), tr("Dark", "Tối")};
        int selected = isDarkTheme() ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(tr("Theme", "Giao diện"))
                .setSingleChoiceItems(themes, selected, (dialog, which) -> {
                    dialog.dismiss();
                    setThemePreference(which == 0 ? THEME_LIGHT : THEME_DARK);
                })
                .setNegativeButton(tr("Cancel", "Hủy"), null)
                .show();
    }

    private void showLanguageDialog() {
        String[] languages = {"English", "Tiếng Việt"};
        int selected = isVietnamese() ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(tr("Language", "Ngôn ngữ"))
                .setSingleChoiceItems(languages, selected, (dialog, which) -> {
                    dialog.dismiss();
                    setLanguagePreference(which == 0 ? LANGUAGE_EN : LANGUAGE_VI);
                })
                .setNegativeButton(tr("Cancel", "Hủy"), null)
                .show();
    }

    private void showPermissionsDialog() {
        String message = tr(
                "Permissions are Android approvals that let SmartScanner use device features. Camera is used for scanning, storage is used for importing/exporting files, and notifications may be used for background processing status.",
                "Quyền ứng dụng là các phê duyệt của Android để SmartScanner dùng tính năng trên máy. Camera dùng để quét, bộ nhớ dùng để nhập/xuất tệp, và thông báo có thể dùng để hiển thị trạng thái xử lý nền."
        );
        new AlertDialog.Builder(this)
                .setTitle(tr("Permissions", "Quyền ứng dụng"))
                .setMessage(message + "\n\n" + permissionDetails())
                .setPositiveButton(tr("Open App Settings", "Mở cài đặt app"), (dialog, which) -> openAppPermissionSettings())
                .setNeutralButton(tr("Storage Access", "Quyền bộ nhớ"), (dialog, which) -> openStoragePermissionSettings())
                .setNegativeButton(tr("Close", "Đóng"), null)
                .show();
    }

    private void setThemePreference(String theme) {
        if (Objects.equals(selectedTheme, theme)) {
            return;
        }
        selectedTheme = theme;
        optionsPrefs.edit().putString(PREF_THEME, theme).apply();
        AppCompatDelegate.setDefaultNightMode(
                isDarkTheme() ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        recreate();
    }

    private void setLanguagePreference(String language) {
        if (Objects.equals(selectedLanguage, language)) {
            return;
        }
        selectedLanguage = language;
        optionsPrefs.edit().putString(PREF_LANGUAGE, language).apply();
        if (searchPopup != null) {
            searchPopup.dismiss();
        }
        renderCurrentTab();
    }

    private LinearLayout createHeader(boolean showShortcuts) {
        return createHeader(showShortcuts, false);
    }

    private LinearLayout createHeader(boolean showShortcuts, boolean forceShortcutRow) {
        boolean hasShortcutRow = showShortcuts || forceShortcutRow;
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(dp(22), getStatusBarHeight() + dp(18), dp(22), hasShortcutRow ? dp(22) : dp(28));
        header.setBackgroundColor(appBlue());

        EditText searchBox = new EditText(this);
        searchAnchor = searchBox;
        searchBox.setSingleLine(true);
        searchBox.setHint(tr("Search for any file!", "Tìm kiếm tệp bất kỳ!"));
        searchBox.setHintTextColor(textMuted());
        searchBox.setTextColor(textPrimary());
        searchBox.setTextSize(16);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setPadding(dp(20), 0, dp(20), 0);
        searchBox.setBackground(createRoundedBackground(inputBackground(), dp(28)));
        searchBox.setText(viewModel.getSearchQueryValue());
        searchBox.setSelection(searchBox.getText().length());
        header.addView(searchBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        ));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
                updateSearchPopup();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (hasShortcutRow) {
            LinearLayout shortcuts = new LinearLayout(this);
            shortcuts.setOrientation(LinearLayout.HORIZONTAL);
            shortcuts.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams shortcutRowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(90)
            );
            shortcutRowParams.setMargins(0, dp(20), 0, 0);

            if (selectedItemKeys.isEmpty()) {
                shortcuts.addView(createShortcutButton(tr("Upload file", "Tải tệp"), R.drawable.ic_upload_file_shortcut, v -> filePickerLauncher.launch("*/*")), shortcutParams(true));
                shortcuts.addView(createShortcutButton(tr("Upload image", "Tải ảnh"), R.drawable.ic_upload_image_shortcut, v -> imagePickerLauncher.launch("image/*")), shortcutParams(false));
                shortcuts.addView(createShortcutButton(tr("Create folder", "Tạo thư mục"), R.drawable.ic_create_file_shortcut, v -> viewModel.createFolder(tr("New Folder", "Thư mục mới"), currentFolderId())), shortcutParams(false));
            } else {
                shortcuts.addView(createShortcutButton(tr("Delete", "Xóa"), R.drawable.ic_delete_shortcut, v -> deleteSelectedItems()), shortcutParams(true));
                if (hasSelectedFolder()) {
                    shortcuts.addView(createShortcutButton(tr("Unfold", "Mở bung"), R.drawable.ic_unfold_shortcut, v -> unfoldSelectedFolders()), shortcutParams(false));
                } else {
                    shortcuts.addView(createShortcutButton(tr("Share", "Chia sẻ"), R.drawable.ic_share_shortcut, v -> shareSelectedItems()), shortcutParams(false));
                }
                shortcuts.addView(createShortcutButton(tr("To Folder", "Vào thư mục"), R.drawable.ic_move_folder_shortcut, v -> moveSelectedToNewFolder()), shortcutParams(false));
            }

            header.addView(shortcuts, shortcutRowParams);
        }

        return header;
    }

    private View createBackRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(12), dp(18), dp(4));

        TextView back = new TextView(this);
        back.setText("<");
        back.setTextSize(28);
        back.setTextColor(textPrimary());
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> navigateUp());
        row.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = new TextView(this);
        title.setText(showingDownloads ? tr("Downloads", "Tải xuống") : openedFolder != null ? openedFolder.name : tr("Folder", "Thư mục"));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(textPrimary());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        attachParentDropTarget(row);
        return row;
    }

    private TextView createSectionTitle(String title, int sizeSp) {
        TextView textView = new TextView(this);
        textView.setText(title);
        textView.setTextColor(textPrimary());
        textView.setTextSize(sizeSp);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textView.setPadding(dp(22), dp(14), dp(22), dp(8));
        return textView;
    }

    private View createEmptyState(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(emptyTextColor());
        empty.setTextSize(16);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(80), 0, dp(80));
        return empty;
    }

    private View createDocumentRow(Document document) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(10));
        card.setCardElevation(0);
        card.setUseCompatPadding(false);
        card.setCardBackgroundColor(Color.TRANSPARENT);
        card.setStrokeWidth(0);
        card.setStrokeColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(86));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));

        attachItemGestures(
                card,
                () -> openFile(document.filePath, document.fileType),
                () -> selectItem(document),
                () -> showRenameDialog(document)
        );

        row.addView(createFileIcon(mapFileType(document.fileType), document.filePath, dp(66), dp(76)));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(document.title);
        title.setTextColor(textPrimary());
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title);

        TextView date = new TextView(this);
        date.setText(dateFormat.format(new Date(document.createdAt)));
        date.setTextSize(12);
        date.setTextColor(textMuted());
        textColumn.addView(date);

        row.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
        return card;
    }

    private MaterialCardView createExplorerCard(ExplorerItem item) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0);
        card.setRadius(dp(14));
        card.setUseCompatPadding(false);
        card.setCardBackgroundColor(Color.TRANSPARENT);
        applyExplorerSelectionStyle(card, item.originalItem);
        card.setMinimumHeight(dp(166));
        String itemKey = itemKey(item.originalItem);
        if (itemKey != null) {
            explorerCardsByKey.put(itemKey, card);
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(8), dp(10), dp(8), dp(10));

        content.addView(createFileIcon(item.type, item.filePath, dp(84), dp(94)));

        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(textPrimary());
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(8), 0, 0);
        content.addView(title, titleParams);

        TextView date = new TextView(this);
        date.setText(item.date);
        date.setTextSize(11);
        date.setTextColor(textMuted());
        date.setGravity(Gravity.CENTER);
        date.setSingleLine(true);
        date.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        card.addView(content);
        if (item.isVirtualDownloads()) {
            card.setOnClickListener(v -> handleExplorerClick(item));
        } else {
            attachItemGestures(
                    card,
                    () -> handleExplorerClick(item),
                    () -> handleExplorerLongPress(item, card),
                    () -> {
                        if (selectedItemKeys.isEmpty()) {
                            showRenameDialog(item.originalItem);
                        }
                    }
            );
            if (item.originalItem instanceof Folder) {
                attachFolderDropTarget(card, item, (Folder) item.originalItem);
            }
        }
        return card;
    }

    private MaterialButton createShortcutButton(String label, View.OnClickListener listener) {
        return createShortcutButton(label, 0, listener);
    }

    private MaterialButton createShortcutButton(String label, int iconRes, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(appBlue());
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMaxLines(1);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(6), dp(10), dp(6), dp(8));
        button.setBackgroundTintList(ColorStateList.valueOf(shortcutBackground()));
        button.setStrokeWidth(dp(1));
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.argb(45, 255, 255, 255)));
        button.setCornerRadius(dp(20));
        button.setElevation(dp(1));
        if (iconRes != 0) {
            button.setIconResource(iconRes);
            button.setIconTint(ColorStateList.valueOf(appBlue()));
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TOP);
            button.setIconSize(dp(46));
            button.setIconPadding(dp(4));
        }
        button.setOnClickListener(listener);
        return button;
    }

    private View createToolCard(String title, String description, int color, int iconRes, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(14));
        card.setCardElevation(dp(2));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(cardStroke());
        card.setCardBackgroundColor(cardBackground());
        card.setMinimumHeight(dp(180));
        card.setOnClickListener(listener);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(14), dp(14), dp(14), dp(14));

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconRes);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(iconView, new LinearLayout.LayoutParams(dp(76), dp(76)));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(textPrimary());
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(12), 0, dp(4));
        content.addView(titleView, titleParams);

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextColor(textMuted());
        descView.setTextSize(12);
        descView.setGravity(Gravity.CENTER);
        descView.setMaxLines(2);
        descView.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(descView);

        card.addView(content);
        return card;
    }

    private View createFileIcon(ExplorerType type, @Nullable String filePath, int width, int height) {
        if (type == ExplorerType.PNG && filePath != null && new File(filePath).exists()) {
            MaterialCardView thumbnailCard = new MaterialCardView(this);
            thumbnailCard.setRadius(dp(10));
            thumbnailCard.setCardElevation(0);
            thumbnailCard.setUseCompatPadding(false);
            thumbnailCard.setStrokeWidth(dp(1));
            thumbnailCard.setStrokeColor(cardStroke());
            thumbnailCard.setCardBackgroundColor(cardBackground());
            thumbnailCard.setLayoutParams(new LinearLayout.LayoutParams(width, height));

            ImageView imageView = new ImageView(this);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = BitmapFactory.decodeFile(filePath);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setBackgroundColor(Color.rgb(229, 239, 255));
            }
            imageView.setPadding(0, 0, 0, 0);
            thumbnailCard.addView(imageView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return thumbnailCard;
        }

        FileGlyphView icon = new FileGlyphView(this, type);
        icon.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return icon;
    }

    private List<ExplorerItem> buildExplorerItems() {
        List<ExplorerItem> items = new ArrayList<>();
        if (showingDownloads) {
            for (Document document : cachedDownloadFiles) {
                items.add(new ExplorerItem(document.title, dateFormat.format(new Date(document.createdAt)),
                        mapFileType(document.fileType), document, document.filePath));
            }
        } else if (openedFolder != null) {
            for (Folder folder : cachedFolders) {
                if (Objects.equals(folder.parentFolderId, openedFolder.id)) {
                    items.add(new ExplorerItem(folder.name, dateFormat.format(new Date(folder.createdAt)),
                            ExplorerType.FOLDER, folder, null));
                }
            }
            for (Document document : cachedDatabaseDocuments) {
                if (Objects.equals(document.folderId, openedFolder.id)) {
                    items.add(new ExplorerItem(document.title, dateFormat.format(new Date(document.createdAt)),
                            mapFileType(document.fileType), document, document.filePath));
                }
            }
        } else {
            items.add(new ExplorerItem(tr("Downloads", "Tải xuống"), cachedDownloadFiles.size() + tr(" files synced", " tệp đã đồng bộ"),
                    ExplorerType.FOLDER, ExplorerItem.VIRTUAL_DOWNLOADS, null));
            for (Folder folder : cachedFolders) {
                if (folder.parentFolderId == null) {
                    items.add(new ExplorerItem(folder.name, dateFormat.format(new Date(folder.createdAt)),
                            ExplorerType.FOLDER, folder, null));
                }
            }
            for (Document document : cachedDatabaseDocuments) {
                if (document.folderId == null) {
                    items.add(new ExplorerItem(document.title, dateFormat.format(new Date(document.createdAt)),
                            mapFileType(document.fileType), document, document.filePath));
                }
            }
        }
        return items;
    }

    private void handleExplorerClick(ExplorerItem item) {
        if (!selectedItemKeys.isEmpty() && !item.isVirtualDownloads()) {
            toggleSelection(item.originalItem);
            return;
        }

        if (item.isVirtualDownloads()) {
            showingDownloads = true;
            openedFolder = null;
            renderCurrentTab();
        } else if (item.originalItem instanceof Folder) {
            openedFolder = (Folder) item.originalItem;
            showingDownloads = false;
            selectedItemKeys.clear();
            renderCurrentTab();
        } else if (item.originalItem instanceof Document) {
            Document document = (Document) item.originalItem;
            openFile(document.filePath, document.fileType);
        }
    }

    private void navigateUp() {
        selectedItemKeys.clear();
        if (showingDownloads) {
            showingDownloads = false;
        } else if (openedFolder != null) {
            Integer parentId = openedFolder.parentFolderId;
            openedFolder = null;
            if (parentId != null) {
                for (Folder folder : cachedFolders) {
                    if (folder.id == parentId) {
                        openedFolder = folder;
                        break;
                    }
                }
            }
        }
        renderCurrentTab();
    }

    private void handleFileImport(Uri uri) {
        DocumentRepository.DATABASE_EXECUTOR.execute(() -> {
            String filePath = FileStorageManager.saveFileFromUri(this, uri);
            if (filePath == null) {
                runOnUiThread(() -> Toast.makeText(this, tr("Import failed", "Nhập tệp thất bại"), Toast.LENGTH_SHORT).show());
                return;
            }

            File actualFile = new File(filePath);
            String fileName = actualFile.getName();
            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) {
                mimeType = getMimeType(filePath);
            }
            Integer folderId = selectedTab == BottomTab.FILES && !showingDownloads && openedFolder != null
                    ? openedFolder.id
                    : null;

            viewModel.insertDocument(folderId, fileName, filePath, mimeType);
            runOnUiThread(() -> Toast.makeText(this, tr("Imported: ", "Đã nhập: ") + fileName, Toast.LENGTH_SHORT).show());
        });
    }

    public void openFile(String filePath, @Nullable String fileType) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(this, tr("File not found", "Không tìm thấy tệp"), Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = FileProvider.getUriForFile(
                    getApplicationContext(),
                    "com.smartscanner.fileprovider",
                    file
            );

            String mimeType = fileType != null ? fileType : getMimeType(filePath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, tr("No app found to open this file", "Không có ứng dụng để mở tệp này"), Toast.LENGTH_SHORT).show();
        }
    }

    private void attachItemGestures(View view, Runnable onClick, Runnable onLongPress, Runnable onDoubleTap) {
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                onClick.run();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                onLongPress.run();
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                onDoubleTap.run();
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        view.setClickable(true);
        view.setLongClickable(true);
        view.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void handleExplorerLongPress(ExplorerItem item, View dragSource) {
        if (item == null || item.isVirtualDownloads()) {
            return;
        }

        if (!selectedItemKeys.isEmpty() && isSelected(item.originalItem)) {
            if (startSelectedItemsDrag(dragSource)) {
                return;
            }
        }

        selectItem(item.originalItem);
    }

    private boolean startSelectedItemsDrag(View dragSource) {
        List<Object> movableItems = normalizeMoveItems(resolveSelectedMovableItems());
        if (movableItems.isEmpty()) {
            Toast.makeText(this, tr("No app files selected", "Khong co tep trong app duoc chon"), Toast.LENGTH_SHORT).show();
            return false;
        }

        DragPayload payload = new DragPayload(new ArrayList<>(selectedItemKeys));
        ClipData dragData = ClipData.newPlainText(DRAG_LABEL, String.valueOf(movableItems.size()));
        return dragSource.startDragAndDrop(
                dragData,
                new SelectionDragShadow(dragSource, movableItems.size()),
                payload,
                0
        );
    }

    private void attachFolderDropTarget(MaterialCardView card, ExplorerItem item, Folder targetFolder) {
        card.setOnDragListener((view, event) -> {
            DragPayload payload = dragPayload(event);
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return payload != null;
                case DragEvent.ACTION_DRAG_ENTERED:
                    if (canDropPayloadTo(payload, targetFolder.id)) {
                        setExplorerDropHighlight(card, true, item.originalItem);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    setExplorerDropHighlight(card, false, item.originalItem);
                    return true;
                case DragEvent.ACTION_DROP:
                    setExplorerDropHighlight(card, false, item.originalItem);
                    if (canDropPayloadTo(payload, targetFolder.id)) {
                        moveDraggedItemsTo(payload, targetFolder.id);
                    } else {
                        showInvalidDropToast();
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    setExplorerDropHighlight(card, false, item.originalItem);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void attachParentDropTarget(LinearLayout row) {
        if (showingDownloads || openedFolder == null) {
            return;
        }

        Integer parentFolderId = openedFolder.parentFolderId;
        row.setOnDragListener((view, event) -> {
            DragPayload payload = dragPayload(event);
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return payload != null;
                case DragEvent.ACTION_DRAG_ENTERED:
                    if (canDropPayloadTo(payload, parentFolderId)) {
                        setParentDropHighlight(row, true);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    setParentDropHighlight(row, false);
                    return true;
                case DragEvent.ACTION_DROP:
                    setParentDropHighlight(row, false);
                    if (canDropPayloadTo(payload, parentFolderId)) {
                        moveDraggedItemsTo(payload, parentFolderId);
                    } else {
                        showInvalidDropToast();
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    setParentDropHighlight(row, false);
                    return true;
                default:
                    return true;
            }
        });
    }

    @Nullable
    private DragPayload dragPayload(DragEvent event) {
        Object localState = event.getLocalState();
        return localState instanceof DragPayload ? (DragPayload) localState : null;
    }

    private void setExplorerDropHighlight(MaterialCardView card, boolean highlighted, Object item) {
        if (highlighted) {
            card.setStrokeWidth(dp(2));
            card.setStrokeColor(appBlue());
            card.setCardBackgroundColor(Color.argb(24, 51, 103, 239));
            return;
        }

        applyExplorerSelectionStyle(card, item);
    }

    private void applyExplorerSelectionStyle(MaterialCardView card, Object item) {
        card.setCardBackgroundColor(Color.TRANSPARENT);
        card.setStrokeWidth(isSelected(item) ? dp(2) : 0);
        card.setStrokeColor(isSelected(item) ? appBlue() : Color.TRANSPARENT);
    }

    private void refreshSelectionUi() {
        if (selectedTab != BottomTab.FILES || contentContainer == null) {
            renderCurrentTab();
            return;
        }

        refreshFilesHeader();
        for (Map.Entry<String, MaterialCardView> entry : explorerCardsByKey.entrySet()) {
            Object item = findItemByKey(entry.getKey());
            if (item != null) {
                applyExplorerSelectionStyle(entry.getValue(), item);
            }
        }
    }

    private void refreshFilesHeader() {
        if (contentContainer.getChildCount() == 0) {
            return;
        }

        contentContainer.removeViewAt(0);
        contentContainer.addView(createHeader(true), 0);
    }

    private void setParentDropHighlight(View row, boolean highlighted) {
        row.setBackground(highlighted
                ? createRoundedBackground(Color.argb(28, 51, 103, 239), dp(18))
                : null);
    }

    private boolean canDropPayloadTo(@Nullable DragPayload payload, @Nullable Integer targetFolderId) {
        if (payload == null) {
            return false;
        }

        List<Object> items = normalizeMoveItems(resolveMovableItems(payload.selectedKeys));
        return canMoveItemsToFolder(items, targetFolderId) && countItemsMoving(items, targetFolderId) > 0;
    }

    private void moveDraggedItemsTo(@Nullable DragPayload payload, @Nullable Integer targetFolderId) {
        if (payload == null) {
            return;
        }

        List<Object> items = normalizeMoveItems(resolveMovableItems(payload.selectedKeys));
        if (items.isEmpty()) {
            Toast.makeText(this, tr("No app files selected", "Khong co tep trong app duoc chon"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!canMoveItemsToFolder(items, targetFolderId)) {
            showInvalidDropToast();
            return;
        }

        int expectedMoves = countItemsMoving(items, targetFolderId);
        if (expectedMoves == 0) {
            Toast.makeText(this, tr("Already in that folder", "Da o dung thu muc"), Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.moveItemsToFolder(items, targetFolderId, movedCount -> {
            int count = movedCount > 0 ? movedCount : expectedMoves;
            Toast.makeText(this, tr("Moved ", "Da chuyen ") + count + tr(" items", " muc"), Toast.LENGTH_SHORT).show();
        });
        selectedItemKeys.clear();
        renderCurrentTab();
    }

    private void showInvalidDropToast() {
        Toast.makeText(this, tr("Cannot move selected items there", "Khong the chuyen vao day"), Toast.LENGTH_SHORT).show();
    }

    private boolean canMoveItemsToFolder(List<Object> items, @Nullable Integer targetFolderId) {
        if (items.isEmpty()) {
            return false;
        }
        if (targetFolderId != null && findFolderById(targetFolderId) == null) {
            return false;
        }

        for (Object item : items) {
            if (item instanceof Document) {
                Document document = (Document) item;
                if (Objects.equals(document.folderId, -1)) {
                    return false;
                }
            } else if (item instanceof Folder) {
                Folder folder = (Folder) item;
                if (Objects.equals(folder.id, targetFolderId)) {
                    return false;
                }
                if (targetFolderId != null && isFolderInside(targetFolderId, folder.id)) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private int countItemsMoving(List<Object> items, @Nullable Integer targetFolderId) {
        int count = 0;
        for (Object item : items) {
            if (item instanceof Document) {
                Document document = (Document) item;
                if (!Objects.equals(document.folderId, targetFolderId)) {
                    count++;
                }
            } else if (item instanceof Folder) {
                Folder folder = (Folder) item;
                if (!Objects.equals(folder.parentFolderId, targetFolderId)) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<Object> normalizeMoveItems(List<Object> items) {
        Set<Integer> selectedFolderIds = new HashSet<>();
        for (Object item : items) {
            if (item instanceof Folder) {
                selectedFolderIds.add(((Folder) item).id);
            }
        }

        List<Object> normalized = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Document) {
                normalized.add(item);
            } else if (item instanceof Folder) {
                Folder folder = (Folder) item;
                if (!hasSelectedAncestor(folder, selectedFolderIds)) {
                    normalized.add(folder);
                }
            }
        }
        return normalized;
    }

    private boolean hasSelectedAncestor(Folder folder, Set<Integer> selectedFolderIds) {
        Integer parentId = folder.parentFolderId;
        Set<Integer> visited = new HashSet<>();
        while (parentId != null && visited.add(parentId)) {
            if (selectedFolderIds.contains(parentId)) {
                return true;
            }
            Folder parent = findFolderById(parentId);
            parentId = parent == null ? null : parent.parentFolderId;
        }
        return false;
    }

    private boolean isFolderInside(int possibleChildFolderId, int ancestorFolderId) {
        Integer currentId = possibleChildFolderId;
        Set<Integer> visited = new HashSet<>();
        while (currentId != null && visited.add(currentId)) {
            if (currentId == ancestorFolderId) {
                return true;
            }
            Folder folder = findFolderById(currentId);
            currentId = folder == null ? null : folder.parentFolderId;
        }
        return false;
    }

    private void addSelectedItem(Object item) {
        String key = itemKey(item);
        if (key != null) {
            selectedItemKeys.add(key);
        }
    }

    private List<Object> resolveSelectedItems() {
        return resolveItems(new ArrayList<>(selectedItemKeys), false);
    }

    private List<Object> resolveSelectedMovableItems() {
        return resolveItems(new ArrayList<>(selectedItemKeys), true);
    }

    private List<Object> resolveMovableItems(List<String> keys) {
        return resolveItems(keys, true);
    }

    private List<Object> resolveItems(List<String> keys, boolean movableOnly) {
        List<Object> items = new ArrayList<>();
        for (String key : keys) {
            Object item = findItemByKey(key);
            if (item != null && (!movableOnly || isMovableAppItem(item))) {
                items.add(item);
            }
        }
        return items;
    }

    @Nullable
    private Object findItemByKey(String key) {
        if (key == null) {
            return null;
        }

        if (key.startsWith(DOCUMENT_KEY_PREFIX)) {
            Integer documentId = parseKeyId(key, DOCUMENT_KEY_PREFIX);
            if (documentId == null) {
                return null;
            }
            for (Document document : cachedDatabaseDocuments) {
                if (document.id == documentId) {
                    return document;
                }
            }
        } else if (key.startsWith(DOWNLOAD_KEY_PREFIX)) {
            Integer documentId = parseKeyId(key, DOWNLOAD_KEY_PREFIX);
            if (documentId == null) {
                return null;
            }
            for (Document document : cachedDownloadFiles) {
                if (document.id == documentId) {
                    return document;
                }
            }
        } else if (key.startsWith(FOLDER_KEY_PREFIX)) {
            Integer folderId = parseKeyId(key, FOLDER_KEY_PREFIX);
            return folderId == null ? null : findFolderById(folderId);
        }
        return null;
    }

    @Nullable
    private String itemKey(Object item) {
        if (item instanceof Document) {
            Document document = (Document) item;
            String prefix = Objects.equals(document.folderId, -1) ? DOWNLOAD_KEY_PREFIX : DOCUMENT_KEY_PREFIX;
            return prefix + document.id;
        }
        if (item instanceof Folder) {
            return FOLDER_KEY_PREFIX + ((Folder) item).id;
        }
        return null;
    }

    @Nullable
    private Integer parseKeyId(String key, String prefix) {
        try {
            return Integer.parseInt(key.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Nullable
    private Folder findFolderById(int folderId) {
        for (Folder folder : cachedFolders) {
            if (folder.id == folderId) {
                return folder;
            }
        }
        return null;
    }

    private boolean isMovableAppItem(Object item) {
        if (item instanceof Folder) {
            return true;
        }
        return item instanceof Document && !Objects.equals(((Document) item).folderId, -1);
    }

    private void pruneSelectedItems() {
        if (selectedItemKeys.isEmpty()) {
            return;
        }

        Set<String> validKeys = new HashSet<>();
        for (String key : selectedItemKeys) {
            if (findItemByKey(key) != null) {
                validKeys.add(key);
            }
        }
        selectedItemKeys.clear();
        selectedItemKeys.addAll(validKeys);
    }

    private void selectItem(Object item) {
        if (item == null || ExplorerItem.VIRTUAL_DOWNLOADS.equals(item)) {
            return;
        }
        String key = itemKey(item);
        if (key == null) {
            return;
        }
        selectedItemKeys.clear();
        selectedItemKeys.add(key);
        refreshSelectionUi();
    }

    private void showItemActions(Object item) {
        if (item instanceof Document) {
            Document document = (Document) item;
            String[] actions = {
                    tr("Open", "Mở"),
                    tr("Select", "Chọn"),
                    tr("Rename", "Đổi tên"),
                    tr("Delete", "Xóa"),
                    tr("Share", "Chia sẻ")
            };
            new AlertDialog.Builder(this)
                    .setTitle(document.title)
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0) {
                            openFile(document.filePath, document.fileType);
                        } else if (which == 1) {
                            addSelectedItem(document);
                            refreshSelectionUi();
                        } else if (which == 2) {
                            showRenameDialog(document);
                        } else if (which == 3) {
                            deleteItem(document);
                        } else if (which == 4) {
                            shareDocuments(singleDocumentList(document));
                        }
                    })
                    .show();
        } else if (item instanceof Folder) {
            Folder folder = (Folder) item;
            String[] actions = {
                    tr("Open", "Mở"),
                    tr("Select", "Chọn"),
                    tr("Rename", "Đổi tên"),
                    tr("Delete", "Xóa")
            };
            new AlertDialog.Builder(this)
                    .setTitle(folder.name)
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0) {
                            openedFolder = folder;
                            showingDownloads = false;
                            renderCurrentTab();
                        } else if (which == 1) {
                            addSelectedItem(folder);
                            refreshSelectionUi();
                        } else if (which == 2) {
                            showRenameDialog(folder);
                        } else if (which == 3) {
                            deleteItem(folder);
                        }
                    })
                    .show();
        }
    }

    private void showRenameDialog(Object item) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setPadding(dp(18), dp(10), dp(18), dp(10));

        String extension = "";
        if (item instanceof Document) {
            Document document = (Document) item;
            int dot = document.title.lastIndexOf('.');
            if (dot > 0) {
                input.setText(document.title.substring(0, dot));
                extension = document.title.substring(dot);
            } else {
                input.setText(document.title);
            }
        } else if (item instanceof Folder) {
            input.setText(((Folder) item).name);
        }
        input.setSelection(input.getText().length());

        String finalExtension = extension;
        new AlertDialog.Builder(this)
                .setTitle(tr("Rename", "Đổi tên"))
                .setView(input)
                .setPositiveButton(tr("Save", "Lưu"), (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        return;
                    }
                    if (item instanceof Document) {
                        viewModel.renameDocument(this, (Document) item, value + finalExtension);
                    } else if (item instanceof Folder) {
                        viewModel.renameFolder((Folder) item, value);
                    }
                })
                .setNegativeButton(tr("Cancel", "Hủy"), null)
                .show();
    }

    private void deleteSelectedItems() {
        if (selectedItemKeys.isEmpty()) {
            return;
        }
        List<Object> items = resolveSelectedItems();
        for (Object item : items) {
            deleteItem(item);
        }
        selectedItemKeys.clear();
        renderCurrentTab();
    }

    private void deleteItem(Object item) {
        if (item instanceof Document) {
            Document document = (Document) item;
            if (Objects.equals(document.folderId, -1)) {
                viewModel.deleteDownloadDocument(this, document);
            } else {
                viewModel.deleteDocument(document);
            }
        } else if (item instanceof Folder) {
            viewModel.deleteFolder((Folder) item);
        }
    }

    private void shareSelectedItems() {
        List<Document> documents = new ArrayList<>();
        for (Object item : resolveSelectedItems()) {
            if (item instanceof Document) {
                documents.add((Document) item);
            }
        }
        shareDocuments(documents);
    }

    private void shareDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            Toast.makeText(this, tr("No files selected", "Chưa chọn tệp nào"), Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (Document document : documents) {
            File file = new File(document.filePath);
            if (file.exists()) {
                uris.add(FileProvider.getUriForFile(
                        getApplicationContext(),
                        "com.smartscanner.fileprovider",
                        file
                ));
            }
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, tr("Files not found", "Không tìm thấy tệp"), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("*/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, tr("Share files", "Chia sẻ tệp")));
    }

    private boolean hasSelectedFolder() {
        for (Object item : resolveSelectedItems()) {
            if (item instanceof Folder) {
                return true;
            }
        }
        return false;
    }

    private void unfoldSelectedFolders() {
        List<Folder> folders = new ArrayList<>();
        for (Object item : resolveSelectedItems()) {
            if (item instanceof Folder) {
                folders.add((Folder) item);
            }
        }
        if (folders.isEmpty()) {
            Toast.makeText(this, tr("No folders selected", "Chưa chọn thư mục nào"), Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.unfoldFolders(folders);
        Toast.makeText(this, tr("Unfolded ", "Đã mở bung ") + folders.size() + tr(" folders", " thư mục"), Toast.LENGTH_SHORT).show();
        selectedItemKeys.clear();
        renderCurrentTab();
    }

    private void moveSelectedToNewFolder() {
        List<Object> items = normalizeMoveItems(resolveSelectedMovableItems());
        if (items.isEmpty()) {
            Toast.makeText(this, tr("No app files selected", "Chưa chọn tệp trong app"), Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.createFolderAndMoveItems(tr("New Grouped Folder", "Thư mục nhóm mới"), items, currentFolderId());
        Toast.makeText(this, tr("Moved ", "Đã chuyển ") + items.size() + tr(" items", " mục"), Toast.LENGTH_SHORT).show();
        selectedItemKeys.clear();
        renderCurrentTab();
    }

    private void showPdfImagePicker() {
        List<Document> imageDocuments = getImageDocuments();
        if (imageDocuments.isEmpty()) {
            Toast.makeText(this, tr("No images found in app", "Không tìm thấy ảnh trong app"), Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[imageDocuments.size()];
        boolean[] checked = new boolean[imageDocuments.size()];
        ArrayList<Document> selected = new ArrayList<>();
        for (int i = 0; i < imageDocuments.size(); i++) {
            labels[i] = imageDocuments.get(i).title;
        }

        new AlertDialog.Builder(this)
                .setTitle(tr("Select Images for PDF", "Chọn ảnh để tạo PDF"))
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    Document document = imageDocuments.get(which);
                    if (isChecked) {
                        selected.add(document);
                    } else {
                        selected.remove(document);
                    }
                })
                .setPositiveButton(tr("Next", "Tiếp"), (dialog, which) -> {
                    if (selected.isEmpty()) {
                        Toast.makeText(this, tr("Select at least one image", "Chọn ít nhất một ảnh"), Toast.LENGTH_SHORT).show();
                    } else {
                        showPdfNameDialog(selected);
                    }
                })
                .setNegativeButton(tr("Cancel", "Hủy"), null)
                .show();
    }

    private void showPdfNameDialog(List<Document> selectedDocuments) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("Converted_Scan");
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle(tr("Export to PDF", "Xuất thành PDF"))
                .setView(input)
                .setPositiveButton(tr("Convert", "Chuyển đổi"), (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "Converted_Scan";
                    }
                    convertImagesToPdf(selectedDocuments, name);
                })
                .setNegativeButton(tr("Cancel", "Hủy"), null)
                .show();
    }

    private void convertImagesToPdf(List<Document> selectedDocuments, String pdfName) {
        DocumentRepository.DATABASE_EXECUTOR.execute(() -> {
            List<Uri> uris = new ArrayList<>();
            for (Document document : selectedDocuments) {
                uris.add(Uri.fromFile(new File(document.filePath)));
            }

            String path = FileStorageManager.convertImagesToPdf(this, uris, pdfName);
            if (path != null) {
                File file = new File(path);
                viewModel.insertDocument(null, file.getName(), path, "application/pdf");
                runOnUiThread(() -> Toast.makeText(this, tr("PDF Created: ", "Đã tạo PDF: ") + file.getName(), Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(this, tr("Failed to create PDF", "Tạo PDF thất bại"), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showOcrImagePicker() {
        List<Document> imageDocuments = getImageDocuments();
        if (imageDocuments.isEmpty()) {
            Toast.makeText(this, tr("No images found in app", "Không tìm thấy ảnh trong app"), Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[imageDocuments.size()];
        for (int i = 0; i < imageDocuments.size(); i++) {
            labels[i] = imageDocuments.get(i).title;
        }

        new AlertDialog.Builder(this)
                .setTitle(tr("Select Image for OCR", "Chọn ảnh để OCR"))
                .setItems(labels, (dialog, which) -> processImageOcr(imageDocuments.get(which)))
                .setNegativeButton(tr("Cancel", "Hủy"), null)
                .show();
    }

    private void processImageOcr(Document document) {
        try {
            InputImage image = InputImage.fromFilePath(this, Uri.fromFile(new File(document.filePath)));
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(visionText -> {
                        String text = visionText.getText();
                        if (text == null || text.trim().isEmpty()) {
                            Toast.makeText(this, tr("No text found in image", "Không tìm thấy chữ trong ảnh"), Toast.LENGTH_SHORT).show();
                        } else {
                            viewModel.updateDocumentOcrText(document.id, text.trim());
                            showOcrResult(text);
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, tr("OCR Error: ", "Lỗi OCR: ") + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Toast.makeText(this, tr("Failed to load image", "Tải ảnh thất bại"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showOcrResult(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(15);
        textView.setPadding(dp(16), dp(12), dp(16), dp(12));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(380)
        ));

        new AlertDialog.Builder(this)
                .setTitle(tr("Extracted Text", "Văn bản trích xuất"))
                .setView(scrollView)
                .setPositiveButton(tr("Copy", "Sao chép"), (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(tr("Extracted Text", "Văn bản trích xuất"), text));
                    }
                    Toast.makeText(this, tr("Copied to clipboard", "Đã sao chép vào clipboard"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(tr("Close", "Đóng"), null)
                .show();
    }

    private List<Document> getImageDocuments() {
        List<Document> documents = new ArrayList<>();
        for (Document document : cachedDatabaseDocuments) {
            if (document.fileType != null && document.fileType.toLowerCase(Locale.US).contains("image")) {
                documents.add(document);
            }
        }
        return documents;
    }

    private void updateSearchPopup() {
        if (searchAnchor == null) {
            return;
        }

        String query = viewModel.getSearchQueryValue().trim();
        if (query.isEmpty()) {
            if (searchPopup != null) {
                searchPopup.dismiss();
            }
            return;
        }

        if (searchPopupContent == null) {
            searchPopupContent = new LinearLayout(this);
            searchPopupContent.setOrientation(LinearLayout.VERTICAL);
            searchPopupContent.setPadding(dp(8), dp(8), dp(8), dp(8));
            searchPopupContent.setBackgroundColor(cardBackground());
        }
        searchPopupContent.removeAllViews();

        if (cachedSearchResults.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(tr("No files found", "Không tìm thấy tệp"));
            empty.setTextColor(emptyTextColor());
            empty.setGravity(Gravity.CENTER);
            empty.setMinHeight(dp(68));
            empty.setPadding(dp(16), dp(16), dp(16), dp(16));
            searchPopupContent.addView(empty);
        } else {
            int limit = Math.min(8, cachedSearchResults.size());
            for (int i = 0; i < limit; i++) {
                Document document = cachedSearchResults.get(i);
                View row = createDocumentRow(document);
                row.setOnClickListener(v -> {
                    viewModel.setSearchQuery("");
                    openFile(document.filePath, document.fileType);
                });
                searchPopupContent.addView(row);
            }
        }

        if (searchPopup == null) {
            ScrollView scrollView = new ScrollView(this);
            scrollView.setClipToPadding(false);
            scrollView.addView(searchPopupContent);
            searchPopup = new PopupWindow(
                    scrollView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    false
            );
            searchPopup.setBackgroundDrawable(new ColorDrawable(cardBackground()));
            searchPopup.setOutsideTouchable(true);
            searchPopup.setElevation(dp(8));
        }

        int maxHeight = dp(360);
        int desiredHeight;
        if (cachedSearchResults.isEmpty()) {
            desiredHeight = dp(92);
        } else {
            int visibleCount = Math.min(8, cachedSearchResults.size());
            desiredHeight = dp(16) + visibleCount * dp(96);
        }
        searchPopup.setHeight(Math.min(maxHeight, desiredHeight));

        if (!searchPopup.isShowing() && searchAnchor.getWindowToken() != null) {
            searchPopup.setWidth(root.getWidth() - dp(44));
            searchPopup.showAsDropDown(searchAnchor, 0, dp(8));
        } else if (searchPopup.isShowing()) {
            searchPopup.setWidth(root.getWidth() - dp(44));
            searchPopup.update(searchPopup.getWidth(), Math.min(maxHeight, desiredHeight));
        }
    }

    private void toggleSelection(Object item) {
        String key = itemKey(item);
        if (key == null) {
            return;
        }
        if (selectedItemKeys.contains(key)) {
            selectedItemKeys.remove(key);
        } else {
            selectedItemKeys.add(key);
        }
        refreshSelectionUi();
    }

    private boolean isSelected(Object item) {
        String key = itemKey(item);
        return key != null && selectedItemKeys.contains(key);
    }

    @Nullable
    private Integer currentFolderId() {
        return showingDownloads || openedFolder == null ? null : openedFolder.id;
    }

    private boolean isDarkTheme() {
        return THEME_DARK.equals(selectedTheme);
    }

    private boolean isVietnamese() {
        return LANGUAGE_VI.equals(selectedLanguage);
    }

    private String tr(String english, String vietnamese) {
        return isVietnamese() ? vietnamese : english;
    }

    private String tabLabel(BottomTab tab) {
        switch (tab) {
            case HOME:
                return tr("Home", "Trang chủ");
            case FILES:
                return tr("Files", "Tệp");
            case TOOLS:
                return tr("Tools", "Công cụ");
            case OPTIONS:
                return tr("Options", "Tùy chọn");
            default:
                return "";
        }
    }

    private String currentThemeLabel() {
        return isDarkTheme() ? tr("Dark", "Tối") : tr("Light", "Sáng");
    }

    private String currentLanguageLabel() {
        return isVietnamese() ? "Tiếng Việt" : "English";
    }

    private int appBlue() {
        return isDarkTheme() ? Color.rgb(93, 142, 255) : APP_BLUE;
    }

    private int pageBackground() {
        return isDarkTheme() ? Color.rgb(18, 20, 26) : PAGE_BACKGROUND;
    }

    private int cardBackground() {
        return isDarkTheme() ? Color.rgb(31, 34, 43) : CARD_BACKGROUND;
    }

    private int inputBackground() {
        return isDarkTheme() ? Color.rgb(31, 34, 43) : Color.rgb(250, 251, 253);
    }

    private int shortcutBackground() {
        return isDarkTheme() ? Color.rgb(246, 248, 252) : Color.WHITE;
    }

    private int optionValueBackground() {
        return isDarkTheme() ? Color.rgb(42, 50, 70) : Color.rgb(235, 241, 255);
    }

    private int navIndicatorColor() {
        return isDarkTheme() ? Color.rgb(41, 49, 67) : Color.rgb(232, 240, 255);
    }

    private int navTextColor() {
        return isDarkTheme() ? Color.rgb(190, 197, 213) : Color.rgb(70, 78, 94);
    }

    private int cardStroke() {
        return isDarkTheme() ? Color.rgb(62, 67, 80) : CARD_STROKE;
    }

    private int textPrimary() {
        return isDarkTheme() ? Color.rgb(242, 245, 249) : TEXT_DARK;
    }

    private int textMuted() {
        return isDarkTheme() ? Color.rgb(169, 177, 193) : TEXT_MUTED;
    }

    private int emptyTextColor() {
        return isDarkTheme() ? Color.rgb(148, 156, 172) : Color.GRAY;
    }

    private String permissionSummary() {
        int total = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 3 : 2;
        int granted = 0;
        if (hasCameraPermission()) {
            granted++;
        }
        if (hasStoragePermission()) {
            granted++;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasNotificationPermission()) {
            granted++;
        }
        return tr(granted + "/" + total + " allowed", granted + "/" + total + " đã cấp");
    }

    private String permissionDetails() {
        List<String> lines = new ArrayList<>();
        lines.add(tr("Camera: ", "Máy ảnh: ") + permissionStateLabel(hasCameraPermission()));
        lines.add(tr("Storage: ", "Bộ nhớ: ") + permissionStateLabel(hasStoragePermission()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lines.add(tr("Notifications: ", "Thông báo: ") + permissionStateLabel(hasNotificationPermission()));
        }
        return TextUtils.join("\n", lines);
    }

    private String permissionStateLabel(boolean granted) {
        return granted ? tr("Allowed", "Đã cấp") : tr("Needs permission", "Cần cấp quyền");
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void openAppPermissionSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openStoragePermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            openAppPermissionSettings();
        }
    }

    private LinearLayout.LayoutParams shortcutParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (!first) {
            params.setMargins(dp(14), 0, 0, 0);
        }
        return params;
    }

    private LinearLayout.LayoutParams weightedCardParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(180), 1f);
        if (!first) {
            params.setMargins(dp(16), 0, 0, 0);
        }
        return params;
    }

    private GradientDrawable createRoundedBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable createGlassBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(isDarkTheme() ? Color.argb(238, 31, 34, 43) : Color.argb(232, 255, 255, 255));
        drawable.setCornerRadius(dp(30));
        drawable.setStroke(dp(1), isDarkTheme() ? Color.rgb(62, 67, 80) : Color.argb(245, 255, 255, 255));
        return drawable;
    }

    private String getMimeType(String url) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.US));
            if (mimeType != null) {
                return mimeType;
            }
        }
        return "application/octet-stream";
    }

    private ExplorerType mapFileType(String mimeType) {
        String value = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        if (value.contains("pdf")) {
            return ExplorerType.PDF;
        } else if (value.contains("image")) {
            return ExplorerType.PNG;
        } else if (value.contains("excel") || value.contains("spreadsheet")) {
            return ExplorerType.XLS;
        } else if (value.contains("csv")) {
            return ExplorerType.CSV;
        } else if (value.contains("word") || value.contains("officedocument.wordprocessingml")) {
            return ExplorerType.DOC;
        } else if (value.contains("presentation")) {
            return ExplorerType.PPT;
        }
        return ExplorerType.GENERIC_FILE;
    }

    private String iconLabel(ExplorerType type) {
        switch (type) {
            case FOLDER:
                return "FOLDER";
            case PNG:
                return "IMG";
            case XLS:
                return "XLS";
            case CSV:
                return "CSV";
            case PDF:
                return "PDF";
            case DOC:
                return "DOC";
            case PPT:
                return "PPT";
            default:
                return "FILE";
        }
    }

    private int iconColor(ExplorerType type) {
        switch (type) {
            case FOLDER:
                return Color.rgb(239, 174, 33);
            case PNG:
                return Color.rgb(48, 119, 189);
            case XLS:
                return Color.rgb(24, 141, 86);
            case CSV:
                return Color.rgb(42, 115, 186);
            case PDF:
                return Color.rgb(198, 40, 40);
            case DOC:
                return Color.rgb(25, 118, 210);
            case PPT:
                return Color.rgb(245, 124, 0);
            default:
                return Color.rgb(97, 97, 97);
        }
    }

    private List<Document> singleDocumentList(Document document) {
        List<Document> documents = new ArrayList<>();
        documents.add(document);
        return documents;
    }

    private List<Document> safeDocumentList(@Nullable List<Document> documents) {
        return documents == null ? new ArrayList<>() : new ArrayList<>(documents);
    }

    private List<Folder> safeFolderList(@Nullable List<Folder> folders) {
        return folders == null ? new ArrayList<>() : new ArrayList<>(folders);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return dp(24);
    }

    private int getNavigationBarHeight() {
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return dp(24);
    }

    @Override
    public void onBackPressed() {
        if (!selectedItemKeys.isEmpty()) {
            selectedItemKeys.clear();
            refreshSelectionUi();
        } else if (selectedTab == BottomTab.FILES && (showingDownloads || openedFolder != null)) {
            navigateUp();
        } else {
            super.onBackPressed();
        }
    }

    private enum BottomTab {
        HOME("Home"),
        FILES("Files"),
        TOOLS("Tools"),
        OPTIONS("Options");

        final String label;

        BottomTab(String label) {
            this.label = label;
        }
    }

    private enum ExplorerType {
        FOLDER,
        PNG,
        XLS,
        CSV,
        PDF,
        DOC,
        PPT,
        GENERIC_FILE
    }

    private static class FileGlyphView extends View {
        private final ExplorerType type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        FileGlyphView(Context context, ExplorerType type) {
            super(context);
            this.type = type;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (type == ExplorerType.FOLDER) {
                drawFolder(canvas);
            } else {
                drawFile(canvas);
            }
        }

        private void drawFolder(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = dpValue(8);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(237, 169, 35));
            rect.set(w * 0.12f, h * 0.18f, w * 0.48f, h * 0.36f);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setColor(Color.rgb(255, 198, 64));
            rect.set(w * 0.06f, h * 0.30f, w * 0.94f, h * 0.86f);
            canvas.drawRoundRect(rect, r * 1.25f, r * 1.25f, paint);

            paint.setColor(Color.rgb(245, 181, 38));
            rect.set(w * 0.06f, h * 0.48f, w * 0.94f, h * 0.86f);
            canvas.drawRoundRect(rect, r * 1.25f, r * 1.25f, paint);
        }

        private void drawFile(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = dpValue(7);
            float left = w * 0.14f;
            float top = h * 0.06f;
            float right = w * 0.86f;
            float bottom = h * 0.94f;
            float fold = Math.min(w, h) * 0.18f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpValue(1));
            paint.setColor(Color.rgb(214, 222, 234));
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(235, 239, 245));
            path.reset();
            path.moveTo(right - fold, top);
            path.lineTo(right, top + fold);
            path.lineTo(right - fold, top + fold);
            path.close();
            canvas.drawPath(path, paint);

            paint.setColor(typeColor(type));
            rect.set(w * 0.22f, h * 0.55f, w * 0.78f, h * 0.78f);
            canvas.drawRoundRect(rect, dpValue(5), dpValue(5), paint);

            paint.setColor(Color.rgb(187, 197, 211));
            paint.setStrokeWidth(dpValue(2));
            float lineLeft = w * 0.24f;
            float lineRight = w * 0.68f;
            canvas.drawLine(lineLeft, h * 0.30f, lineRight, h * 0.30f, paint);
            canvas.drawLine(lineLeft, h * 0.40f, w * 0.76f, h * 0.40f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(Math.max(dpValue(10), h * 0.13f));
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float textY = rect.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(typeLabel(type), rect.centerX(), textY, paint);
        }

        private float dpValue(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private static String typeLabel(ExplorerType type) {
            switch (type) {
                case PNG:
                    return "IMG";
                case XLS:
                    return "XLS";
                case CSV:
                    return "CSV";
                case PDF:
                    return "PDF";
                case DOC:
                    return "DOC";
                case PPT:
                    return "PPT";
                default:
                    return "FILE";
            }
        }

        private static int typeColor(ExplorerType type) {
            switch (type) {
                case PNG:
                    return Color.rgb(48, 119, 189);
                case XLS:
                    return Color.rgb(24, 141, 86);
                case CSV:
                    return Color.rgb(42, 115, 186);
                case PDF:
                    return Color.rgb(206, 42, 42);
                case DOC:
                    return Color.rgb(25, 118, 210);
                case PPT:
                    return Color.rgb(245, 124, 0);
                default:
                    return Color.rgb(97, 97, 97);
            }
        }
    }

    private static class DragPayload {
        final List<String> selectedKeys;

        DragPayload(List<String> selectedKeys) {
            this.selectedKeys = selectedKeys;
        }
    }

    private static class SelectionDragShadow extends View.DragShadowBuilder {
        private final View source;
        private final int count;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SelectionDragShadow(View source, int count) {
            super(source);
            this.source = source;
            this.count = count;
        }

        @Override
        public void onProvideShadowMetrics(Point size, Point touch) {
            int width = Math.max(1, source.getWidth());
            int height = Math.max(1, source.getHeight());
            size.set(width, height);
            touch.set(width / 2, height / 2);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            source.draw(canvas);
            if (count <= 1) {
                return;
            }

            float density = source.getResources().getDisplayMetrics().density;
            float radius = 14f * density;
            float centerX = source.getWidth() - radius - 4f * density;
            float centerY = radius + 4f * density;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(51, 103, 239));
            canvas.drawCircle(centerX, centerY, radius, paint);

            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(13f * density);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float textY = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(count), centerX, textY, paint);
        }
    }

    private static class ExplorerItem {
        static final String VIRTUAL_DOWNLOADS = "VIRTUAL_DOWNLOADS";

        final String title;
        final String date;
        final ExplorerType type;
        final Object originalItem;
        @Nullable
        final String filePath;

        ExplorerItem(String title, String date, ExplorerType type, Object originalItem, @Nullable String filePath) {
            this.title = title;
            this.date = date;
            this.type = type;
            this.originalItem = originalItem;
            this.filePath = filePath;
        }

        boolean isVirtualDownloads() {
            return VIRTUAL_DOWNLOADS.equals(originalItem);
        }
    }
}
