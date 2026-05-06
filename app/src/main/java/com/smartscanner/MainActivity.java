package com.smartscanner;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.view.Gravity;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.vision.common.InputImage;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int APP_BLUE = Color.rgb(51, 103, 239);
    private static final int PAGE_BACKGROUND = Color.rgb(247, 248, 251);
    private static final int CARD_BACKGROUND = Color.WHITE;
    private static final int CARD_STROKE = Color.rgb(226, 232, 240);
    private static final int TEXT_DARK = Color.rgb(32, 33, 36);
    private static final int TEXT_MUTED = Color.rgb(107, 114, 128);

    private FilesViewModel viewModel;
    private FrameLayout root;
    private LinearLayout contentContainer;
    private FrameLayout bottomNavDock;
    private LinearLayout bottomNavItems;
    private View navIndicator;
    private FloatingActionButton cameraButton;

    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<String> imagePickerLauncher;

    private BottomTab selectedTab = BottomTab.HOME;
    private boolean showingDownloads = false;
    @Nullable
    private Folder openedFolder = null;
    private final Set<Object> selectedItems = new HashSet<>();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(FilesViewModel.class);

        requestStoragePermission();
        setupActivityResultLaunchers();
        buildRootUi();
        observeViewModel();
        renderCurrentTab();
    }

    private void setupActivityResultLaunchers() {
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

    private void observeViewModel() {
        viewModel.getFolders().observe(this, folders -> {
            cachedFolders = safeFolderList(folders);
            renderCurrentTab();
        });
        viewModel.getDatabaseDocuments().observe(this, documents -> {
            cachedDatabaseDocuments = safeDocumentList(documents);
            renderCurrentTab();
        });
        viewModel.getDownloadFiles().observe(this, documents -> {
            cachedDownloadFiles = safeDocumentList(documents);
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
        root.setBackgroundColor(PAGE_BACKGROUND);

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
        cameraButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(APP_BLUE));
        cameraButton.setCompatElevation(dp(18));
        cameraButton.setElevation(dp(18));
        cameraButton.setTranslationZ(dp(18));
        cameraButton.setOnClickListener(v -> startActivity(new Intent(this, CameraCaptureActivity.class)));
        FrameLayout.LayoutParams cameraParams = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cameraParams.setMargins(0, 0, 0, getNavigationBarHeight() + dp(61));
        root.addView(cameraButton, cameraParams);
        cameraButton.bringToFront();

        setContentView(root);
    }

    private FrameLayout createBottomNavDock() {
        FrameLayout dock = new FrameLayout(this);
        dock.setPadding(dp(8), dp(8), dp(8), dp(8));
        dock.setBackground(createGlassBackground());
        dock.setElevation(dp(8));
        dock.setTranslationZ(dp(8));

        navIndicator = new View(this);
        navIndicator.setBackground(createRoundedBackground(Color.rgb(232, 240, 255), dp(22)));
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                dp(1),
                dp(46),
                Gravity.START | Gravity.CENTER_VERTICAL
        );
        indicatorParams.leftMargin = dp(8);
        dock.addView(navIndicator, indicatorParams);

        bottomNavItems = new LinearLayout(this);
        bottomNavItems.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavItems.setGravity(Gravity.CENTER);

        tabButtons.clear();
        for (BottomTab tab : BottomTab.values()) {
            TextView button = new TextView(this);
            button.setText(tab.label);
            button.setGravity(Gravity.CENTER);
            button.setTextSize(13);
            button.setIncludeFontPadding(false);
            button.setSingleLine(true);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setOnClickListener(v -> {
                selectedTab = tab;
                selectedItems.clear();
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
            button.setTextColor(isSelected ? APP_BLUE : Color.rgb(70, 78, 94));
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

        int availableWidth = bottomNavDock.getWidth() - bottomNavDock.getPaddingLeft() - bottomNavDock.getPaddingRight();
        int tabWidth = availableWidth / BottomTab.values().length;
        if (tabWidth <= 0) {
            return;
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) navIndicator.getLayoutParams();
        params.width = tabWidth - dp(2);
        params.height = dp(46);
        params.leftMargin = 0;
        navIndicator.setLayoutParams(params);

        float target = selectedTab.ordinal() * tabWidth + dp(1);
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
        getWindow().setNavigationBarColor(Color.WHITE);

        int flags = 0;
        if (selectedTab == BottomTab.HOME || selectedTab == BottomTab.FILES) {
            getWindow().setStatusBarColor(APP_BLUE);
        } else {
            getWindow().setStatusBarColor(PAGE_BACKGROUND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void renderHomeScreen() {
        contentContainer.addView(createHeader(false));
        contentContainer.addView(createSectionTitle("Recent files", 24));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(6), dp(18), getNavigationBarHeight() + dp(154));
        scrollView.addView(list);

        if (cachedRecentDocuments.isEmpty()) {
            list.addView(createEmptyState("No recent files"));
        } else {
            for (Document document : cachedRecentDocuments) {
                list.addView(createDocumentRow(document));
            }
        }

        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
    }

    private void renderFilesScreen() {
        contentContainer.addView(createHeader(true));

        if (showingDownloads || openedFolder != null) {
            contentContainer.addView(createBackRow());
        }

        List<ExplorerItem> explorerItems = buildExplorerItems();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(14), dp(18), getNavigationBarHeight() + dp(158));
        scrollView.addView(list);

        if (explorerItems.isEmpty()) {
            list.addView(createEmptyState("Your storage is empty"));
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

        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
    }

    private void renderToolsScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), getStatusBarHeight() + dp(18), dp(18), getNavigationBarHeight() + dp(150));

        TextView title = createSectionTitle("Magic Tools", 28);
        title.setPadding(dp(2), dp(8), dp(2), dp(16));
        container.addView(title);

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        rowOne.addView(createToolCard("PDF Converter", "Merge scans into one PDF", Color.rgb(229, 115, 115),
                v -> showPdfImagePicker()), weightedCardParams(true));
        rowOne.addView(createToolCard("Text Extract", "Convert images to editable text", APP_BLUE,
                v -> showOcrImagePicker()), weightedCardParams(false));
        container.addView(rowOne);

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        rowTwo.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 0.5f));
        rowTwo.addView(createToolCard("Text Summarizer", "Summarize long text with backend API", Color.rgb(61, 186, 124),
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
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(0, getStatusBarHeight(), 0, getNavigationBarHeight() + dp(120));
        TextView text = new TextView(this);
        text.setText("Options");
        text.setTextColor(TEXT_DARK);
        text.setTextSize(28);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        frame.addView(text, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        contentContainer.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private LinearLayout createHeader(boolean showShortcuts) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(dp(22), getStatusBarHeight() + dp(18), dp(22), showShortcuts ? dp(22) : dp(28));
        header.setBackgroundColor(APP_BLUE);

        EditText searchBox = new EditText(this);
        searchAnchor = searchBox;
        searchBox.setSingleLine(true);
        searchBox.setHint("Search for any file!");
        searchBox.setHintTextColor(Color.rgb(132, 139, 151));
        searchBox.setTextColor(TEXT_DARK);
        searchBox.setTextSize(16);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setPadding(dp(20), 0, dp(20), 0);
        searchBox.setBackground(createRoundedBackground(Color.rgb(250, 251, 253), dp(28)));
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

        if (showShortcuts) {
            LinearLayout shortcuts = new LinearLayout(this);
            shortcuts.setOrientation(LinearLayout.HORIZONTAL);
            shortcuts.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams shortcutRowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(90)
            );
            shortcutRowParams.setMargins(0, dp(20), 0, 0);

            if (selectedItems.isEmpty()) {
                shortcuts.addView(createShortcutButton("Upload file", v -> filePickerLauncher.launch("*/*")), shortcutParams(true));
                shortcuts.addView(createShortcutButton("Upload image", v -> imagePickerLauncher.launch("image/*")), shortcutParams(false));
                shortcuts.addView(createShortcutButton("Create folder", v -> viewModel.createFolder("New Folder", currentFolderId())), shortcutParams(false));
            } else {
                shortcuts.addView(createShortcutButton("Delete", v -> deleteSelectedItems()), shortcutParams(true));
                shortcuts.addView(createShortcutButton("Share", v -> shareSelectedItems()), shortcutParams(false));
                shortcuts.addView(createShortcutButton("To Folder", v -> moveSelectedToNewFolder()), shortcutParams(false));
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
        back.setTextColor(TEXT_DARK);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> navigateUp());
        row.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = new TextView(this);
        title.setText(showingDownloads ? "Downloads" : openedFolder != null ? openedFolder.name : "Folder");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(TEXT_DARK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return row;
    }

    private TextView createSectionTitle(String title, int sizeSp) {
        TextView textView = new TextView(this);
        textView.setText(title);
        textView.setTextColor(TEXT_DARK);
        textView.setTextSize(sizeSp);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textView.setPadding(dp(22), dp(14), dp(22), dp(8));
        return textView;
    }

    private View createEmptyState(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(Color.GRAY);
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

        card.setOnClickListener(v -> openFile(document.filePath, document.fileType));
        card.setOnLongClickListener(v -> {
            showItemActions(document);
            return true;
        });

        row.addView(createFileIcon(mapFileType(document.fileType), document.filePath, dp(66), dp(76)));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(document.title);
        title.setTextColor(TEXT_DARK);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title);

        TextView date = new TextView(this);
        date.setText(dateFormat.format(new Date(document.createdAt)));
        date.setTextSize(12);
        date.setTextColor(TEXT_MUTED);
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
        card.setStrokeWidth(isSelected(item.originalItem) ? dp(2) : 0);
        card.setStrokeColor(isSelected(item.originalItem) ? APP_BLUE : Color.TRANSPARENT);
        card.setMinimumHeight(dp(166));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(8), dp(10), dp(8), dp(10));

        content.addView(createFileIcon(item.type, item.filePath, dp(84), dp(94)));

        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(TEXT_DARK);
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
        date.setTextColor(TEXT_MUTED);
        date.setGravity(Gravity.CENTER);
        date.setSingleLine(true);
        date.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        card.addView(content);
        card.setOnClickListener(v -> handleExplorerClick(item));
        card.setOnLongClickListener(v -> {
            if (!item.isVirtualDownloads()) {
                showItemActions(item.originalItem);
            }
            return true;
        });
        return card;
    }

    private MaterialButton createShortcutButton(String label, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label.replace(" ", "\n"));
        button.setAllCaps(false);
        button.setTextColor(APP_BLUE);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        button.setStrokeWidth(dp(1));
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.argb(45, 255, 255, 255)));
        button.setCornerRadius(dp(20));
        button.setElevation(dp(1));
        button.setOnClickListener(listener);
        return button;
    }

    private View createToolCard(String title, String description, int color, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(14));
        card.setCardElevation(dp(2));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(CARD_STROKE);
        card.setCardBackgroundColor(Color.WHITE);
        card.setMinimumHeight(dp(180));
        card.setOnClickListener(listener);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(14), dp(14), dp(14), dp(14));

        ExplorerType toolType = title.startsWith("PDF") ? ExplorerType.PDF : ExplorerType.DOC;
        content.addView(new FileGlyphView(this, toolType), new LinearLayout.LayoutParams(dp(72), dp(82)));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(TEXT_DARK);
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
        descView.setTextColor(TEXT_MUTED);
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
            thumbnailCard.setStrokeColor(CARD_STROKE);
            thumbnailCard.setCardBackgroundColor(Color.WHITE);
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
            items.add(new ExplorerItem("Downloads", cachedDownloadFiles.size() + " files synced",
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
        if (!selectedItems.isEmpty() && !item.isVirtualDownloads()) {
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
            selectedItems.clear();
            renderCurrentTab();
        } else if (item.originalItem instanceof Document) {
            Document document = (Document) item.originalItem;
            openFile(document.filePath, document.fileType);
        }
    }

    private void navigateUp() {
        selectedItems.clear();
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
                runOnUiThread(() -> Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show());
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
            runOnUiThread(() -> Toast.makeText(this, "Imported: " + fileName, Toast.LENGTH_SHORT).show());
        });
    }

    public void openFile(String filePath, @Nullable String fileType) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void showItemActions(Object item) {
        if (item instanceof Document) {
            Document document = (Document) item;
            String[] actions = {"Open", "Select", "Rename", "Delete", "Share"};
            new AlertDialog.Builder(this)
                    .setTitle(document.title)
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0) {
                            openFile(document.filePath, document.fileType);
                        } else if (which == 1) {
                            selectedItems.add(document);
                            renderCurrentTab();
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
            String[] actions = {"Open", "Select", "Rename", "Delete"};
            new AlertDialog.Builder(this)
                    .setTitle(folder.name)
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0) {
                            openedFolder = folder;
                            showingDownloads = false;
                            renderCurrentTab();
                        } else if (which == 1) {
                            selectedItems.add(folder);
                            renderCurrentTab();
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
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
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
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSelectedItems() {
        if (selectedItems.isEmpty()) {
            return;
        }
        List<Object> items = new ArrayList<>(selectedItems);
        for (Object item : items) {
            deleteItem(item);
        }
        selectedItems.clear();
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
        for (Object item : selectedItems) {
            if (item instanceof Document) {
                documents.add((Document) item);
            }
        }
        shareDocuments(documents);
    }

    private void shareDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Files not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("*/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share files"));
    }

    private void moveSelectedToNewFolder() {
        List<Document> documents = new ArrayList<>();
        for (Object item : selectedItems) {
            if (item instanceof Document) {
                Document document = (Document) item;
                if (!Objects.equals(document.folderId, -1)) {
                    documents.add(document);
                }
            }
        }
        if (documents.isEmpty()) {
            Toast.makeText(this, "No app files selected", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.createFolderAndMoveDocuments("New Grouped Folder", documents, currentFolderId());
        Toast.makeText(this, "Moved " + documents.size() + " files", Toast.LENGTH_SHORT).show();
        selectedItems.clear();
        renderCurrentTab();
    }

    private void showPdfImagePicker() {
        List<Document> imageDocuments = getImageDocuments();
        if (imageDocuments.isEmpty()) {
            Toast.makeText(this, "No images found in app", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[imageDocuments.size()];
        boolean[] checked = new boolean[imageDocuments.size()];
        ArrayList<Document> selected = new ArrayList<>();
        for (int i = 0; i < imageDocuments.size(); i++) {
            labels[i] = imageDocuments.get(i).title;
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Images for PDF")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    Document document = imageDocuments.get(which);
                    if (isChecked) {
                        selected.add(document);
                    } else {
                        selected.remove(document);
                    }
                })
                .setPositiveButton("Next", (dialog, which) -> {
                    if (selected.isEmpty()) {
                        Toast.makeText(this, "Select at least one image", Toast.LENGTH_SHORT).show();
                    } else {
                        showPdfNameDialog(selected);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPdfNameDialog(List<Document> selectedDocuments) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("Converted_Scan");
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Export to PDF")
                .setView(input)
                .setPositiveButton("Convert", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "Converted_Scan";
                    }
                    convertImagesToPdf(selectedDocuments, name);
                })
                .setNegativeButton("Cancel", null)
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
                runOnUiThread(() -> Toast.makeText(this, "PDF Created: " + file.getName(), Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Failed to create PDF", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showOcrImagePicker() {
        List<Document> imageDocuments = getImageDocuments();
        if (imageDocuments.isEmpty()) {
            Toast.makeText(this, "No images found in app", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[imageDocuments.size()];
        for (int i = 0; i < imageDocuments.size(); i++) {
            labels[i] = imageDocuments.get(i).title;
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Image for OCR")
                .setItems(labels, (dialog, which) -> processImageOcr(imageDocuments.get(which)))
                .setNegativeButton("Cancel", null)
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
                            Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show();
                        } else {
                            showOcrResult(text);
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "OCR Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
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
                .setTitle("Extracted Text")
                .setView(scrollView)
                .setPositiveButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", text));
                    }
                    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
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
            searchPopupContent.setBackgroundColor(Color.WHITE);
        }
        searchPopupContent.removeAllViews();

        if (cachedSearchResults.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No files found");
            empty.setTextColor(Color.GRAY);
            empty.setGravity(Gravity.CENTER);
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
            scrollView.addView(searchPopupContent);
            searchPopup = new PopupWindow(
                    scrollView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(360),
                    false
            );
            searchPopup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            searchPopup.setOutsideTouchable(true);
            searchPopup.setElevation(dp(8));
        }

        if (!searchPopup.isShowing() && searchAnchor.getWindowToken() != null) {
            searchPopup.setWidth(root.getWidth() - dp(44));
            searchPopup.showAsDropDown(searchAnchor, 0, dp(8));
        }
    }

    private void toggleSelection(Object item) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        renderCurrentTab();
    }

    private boolean isSelected(Object item) {
        return item != null && selectedItems.contains(item);
    }

    @Nullable
    private Integer currentFolderId() {
        return showingDownloads || openedFolder == null ? null : openedFolder.id;
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
        drawable.setColor(Color.argb(232, 255, 255, 255));
        drawable.setCornerRadius(dp(30));
        drawable.setStroke(dp(1), Color.argb(245, 255, 255, 255));
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
        if (!selectedItems.isEmpty()) {
            selectedItems.clear();
            renderCurrentTab();
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
