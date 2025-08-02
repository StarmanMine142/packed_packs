package io.github.fishstiz.packed_packs.gui.screens;

import com.google.common.collect.ImmutableList;
import io.github.fishstiz.fidgetz.gui.components.*;
import io.github.fishstiz.fidgetz.gui.layouts.FlexLayout;
import io.github.fishstiz.fidgetz.gui.renderables.ColoredRect;
import io.github.fishstiz.fidgetz.gui.renderables.sprites.Sprite;
import io.github.fishstiz.fidgetz.gui.shapes.Size;
import io.github.fishstiz.fidgetz.util.debounce.ImmediateDebouncer;
import io.github.fishstiz.fidgetz.util.debounce.PollingDebouncer;
import io.github.fishstiz.packed_packs.PackedPacks;
import io.github.fishstiz.packed_packs.compat.ModAdditions;
import io.github.fishstiz.packed_packs.config.Config;
import io.github.fishstiz.packed_packs.gui.components.pack.AvailablePackList;
import io.github.fishstiz.packed_packs.gui.components.pack.CurrentPackList;
import io.github.fishstiz.packed_packs.gui.components.pack.PackList;
import io.github.fishstiz.packed_packs.gui.components.profile.Sidebar;
import io.github.fishstiz.packed_packs.gui.layouts.pack.AvailablePacksLayout;
import io.github.fishstiz.packed_packs.gui.layouts.pack.CurrentPacksLayout;
import io.github.fishstiz.packed_packs.gui.layouts.pack.PackLayout;
import io.github.fishstiz.packed_packs.transform.mixin.PackSelectionModelAccessor;
import io.github.fishstiz.packed_packs.util.constants.Theme;
import io.github.fishstiz.packed_packs.util.pack.PackRepositoryHelper;
import io.github.fishstiz.packed_packs.config.Profile;
import io.github.fishstiz.packed_packs.gui.layouts.*;
import io.github.fishstiz.packed_packs.gui.components.events.*;
import io.github.fishstiz.packed_packs.gui.history.HistoryManager;
import io.github.fishstiz.packed_packs.gui.history.Restorable;
import io.github.fishstiz.packed_packs.gui.metadata.PackSelectionScreenArgs;
import io.github.fishstiz.packed_packs.transform.mixin.HeaderAndFooterLayoutAccess;
import io.github.fishstiz.packed_packs.transform.mixin.PackSelectionScreenAccessor;
import io.github.fishstiz.packed_packs.util.ResourceUtil;
import io.github.fishstiz.packed_packs.util.lang.ObjectsUtil;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE;
import static com.mojang.blaze3d.platform.InputConstants.KEY_SPACE;
import static io.github.fishstiz.packed_packs.util.InputUtil.*;
import static io.github.fishstiz.packed_packs.util.pack.PackUtil.*;

public class PackedPacksScreen extends PackListEventHandler implements ToggleableDialogContainer, Restorable<PackedPacksScreen.Snapshot> {
    private static final Component ACTION_BAR_INFO = ResourceUtil.getText("toggle_actionbar.info");
    private static final Component ORIGINAL_SCREEN_INFO = ResourceUtil.getText("original_screen.info");
    private static final Component OPTIONS_TEXT = ResourceUtil.getText("options.title");
    private static final Component OPEN_FOLDER_TEXT = Component.translatable("pack.openFolder");
    private static final Component OPEN_FOLDER_INFO_TEXT = Component.translatable("pack.folderInfo");
    private static final Component APPLY_TEXT = ResourceUtil.getText("apply");
    private static final int SPACING = 8;
    private final Screen previous;
    private final PackSelectionScreenArgs original;
    private final PackRepositoryHelper repository;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final HistoryManager<Snapshot> history = new HistoryManager<>();
    private final AvailablePacksLayout availablePacks;
    private final CurrentPacksLayout currentPacks;
    private final Config.Packs packsConfig;
    private final ProfilesLayout profiles;
    private final ImmediateDebouncer<String> searchListener = new ImmediateDebouncer<>(this::clearHistory, 250);
    private final PollingDebouncer<Void> revalidateTask = new PollingDebouncer<>(this::revalidate, 1000);
    private final Modal<LinearLayout> options = Modal.builder(this, new OptionsLayout(SPACING).layout())
            .setBackdrop(new ColoredRect(Theme.BLACK.withAlpha(0.5f)))
            .setCaptureFocus(true)
            .build();
    private PackSelectionScreen.Watcher watcher;
    private boolean showActionBar = PackedPacks.CONFIG.isShowActionBar();
    private boolean initialized = false;

    public PackedPacksScreen(Screen previous, PackSelectionScreenArgs original) {
        super(ResourceUtil.getModName());

        this.previous = previous;
        this.original = original;
        this.repository = new PackRepositoryHelper(this.original.repository(), this.original.packDir());
        this.availablePacks = new AvailablePacksLayout(this.repository, this, SPACING);
        this.currentPacks = new CurrentPacksLayout(this.repository, this, SPACING);
        this.packsConfig = this.repository.getConfig();
        this.profiles = new ProfilesLayout(Sidebar.builder(this)
                .setHeaderSettings(LayoutSettings.defaults().paddingLeft(SPACING).paddingTop(SPACING - 1)),
                this.packsConfig,
                this.currentPacks.getList()::copyPacks,
                this::onProfileChange
        );
    }

    @Override
    public void added() {
        if (this.initialized) {
            this.revalidate();
            this.createWatcher();
        }
    }

    @Override
    public void removed() {
        this.closeWatcher();
        this.updateProfile(this.profiles.getProfile());
        this.packsConfig.setLastViewed(this.profiles.getProfile());
        PackedPacks.CONFIG.save();
    }

    @Override
    protected void init() {
        this.layout.addToHeader(this.createHeader());
        this.layout.addToContents(this.createContents());
        this.layout.addToFooter(this.createFooter());

        this.profiles.initContents(SPACING);
        this.profiles.getSidebar().getCloseButton().addListener(this::setInitialFocus);

        this.options.root().visitWidgets(this.options::addRenderableWidget);

        this.addWidget(this.options);
        this.addWidget(this.profiles.getSidebar());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.addRenderableOnly(this.profiles.getSidebar());
        this.addRenderableOnly(this.options);

        this.clearHistory();
        this.repositionElements();

        this.createWatcher();
        this.initialized = true;
    }

    private FlexLayout createHeader() {
        FlexLayout header = FlexLayout.horizontal(this::getMaxWidth).spacing(SPACING);
        header.addChild(
                FidgetzButton.builder()
                        .makeSquare()
                        .setMessage(ProfilesLayout.TITLE_TEXT)
                        .setTooltip(Tooltip.create(ProfilesLayout.TITLE_TEXT))
                        .setSpriteOnly(new Sprite(ResourceUtil.getIcon("hamburger"), Size.of16()))
                        .setOnPress(this.profiles.getSidebar()::toggle)
                        .build()
        );
        header.addChild(
                FidgetzButton.builder()
                        .makeSquare()
                        .setTooltip(Tooltip.create(ACTION_BAR_INFO))
                        .setSpriteOnly(new Sprite(ResourceUtil.getIcon("filter"), Size.of16()))
                        .setOnPress(this::toggleActionBar)
                        .build()
        );
        header.addChild(this.profiles.getToggleNameButton());
        header.addFlexChild(this.profiles.getNameField());

        PackSelectionScreen packSelectionScreen = this.previous instanceof PackSelectionScreen s ? s : this.original.createDummy();
        ModAdditions.addToHeader(this.repository.isResourcePacks(), header, packSelectionScreen);

        header.addChild(
                FidgetzButton.builder()
                        .makeSquare()
                        .setMessage(OPTIONS_TEXT)
                        .setTooltip(Tooltip.create(OPTIONS_TEXT))
                        .setSpriteOnly(new Sprite(ResourceUtil.getIcon("gear"), Size.of16()))
                        .setOnPress(this.options::toggle)
                        .build()
        );
        header.addChild(
                FidgetzButton.builder()
                        .makeSquare()
                        .setTooltip(Tooltip.create(ORIGINAL_SCREEN_INFO))
                        .setSpriteOnly(new Sprite(ResourceUtil.getIcon("exit"), Size.of16()))
                        .setOnPress(this::setOriginalScreen)
                        .build()
        );
        return header;
    }

    private FlexLayout createContents() {
        FlexLayout contents = FlexLayout.horizontal(this::getMaxWidth).spacing(SPACING);
        this.availablePacks.init(contents.addFlexChild(FlexLayout.vertical(this.layout::getContentHeight).spacing(SPACING), false));
        this.currentPacks.init(contents.addFlexChild(FlexLayout.vertical(this.layout::getContentHeight).spacing(SPACING), false));
        this.currentPacks.getSearchField().addListener(this.searchListener);
        this.availablePacks.getSearchField().addListener(this.searchListener);
        return contents;
    }

    private FlexLayout createFooter() {
        FlexLayout footer = FlexLayout.horizontal(this::getMaxWidth).spacing(SPACING);
        FlexLayout firstColumn = FlexLayout.horizontal().spacing(SPACING);
        FlexLayout secondColumn = firstColumn.copyLayout();

        firstColumn.addFlexChild(
                FidgetzButton.builder()
                        .setMessage(OPEN_FOLDER_TEXT)
                        .setTooltip(Tooltip.create(OPEN_FOLDER_INFO_TEXT))
                        .setOnPress(this.repository::openDirectory)
                        .build()
        );

        if (this.repository.isResourcePacks()) {
            secondColumn.addFlexChild(FidgetzButton.builder().setMessage(APPLY_TEXT).setOnPress(this::commit).build());
        }

        secondColumn.addFlexChild(FidgetzButton.builder().setMessage(CommonComponents.GUI_DONE).setOnPress(this::onClose).build());

        footer.addFlexChild(firstColumn);
        footer.addFlexChild(secondColumn);
        return footer;
    }

    public int getMaxWidth() {
        return this.width - SPACING * 2;
    }

    @Override
    public void tick() {
        if (this.watcher != null) {
            try {
                if (this.watcher.pollForChanges()) {
                    this.revalidateTask.run();
                }
            } catch (IOException e) {
                PackedPacks.LOGGER.warn("Failed to poll for directory {} changes, stopping", this.original.packDir(), e);
                this.closeWatcher();
            }
        }
        this.revalidateTask.poll();
    }

    @Override
    public void onFilesDrop(List<Path> packs) {
        if (this.minecraft != null) {
            String packNames = extractPackNames(packs).collect(Collectors.joining(", "));
            this.minecraft.setScreen(new ConfirmScreen(
                    this.confirmFileDrop(packs),
                    Component.translatable("pack.dropConfirm"),
                    Component.literal(packNames)
            ));
        }
    }

    private BooleanConsumer confirmFileDrop(List<Path> packs) {
        return confirmed -> {
            if (this.minecraft == null) {
                return;
            }
            if (!confirmed) {
                this.minecraft.setScreen(this);
                return;
            }
            PathValidationResults results = validatePaths(packs, createPackDetector());

            if (!results.symlinkWarnings().isEmpty()) {
                this.minecraft.setScreen(NoticeWithLinkScreen.createPackSymlinkWarningScreen(() -> this.minecraft.setScreen(this)));
                return;
            }
            if (!results.valid().isEmpty()) {
                PackSelectionScreen.copyPacks(this.minecraft, results.valid(), this.original.packDir());
                this.revalidate();
            }
            if (!results.rejected().isEmpty()) {
                String rejectedNames = extractPackNames(results.rejected()).collect(Collectors.joining(", "));
                this.minecraft.setScreen(new AlertScreen(
                        () -> this.minecraft.setScreen(this),
                        Component.translatable("pack.dropRejected.title"),
                        Component.translatable("pack.dropRejected.message", rejectedNames)
                ));
                return;
            }
            this.minecraft.setScreen(this);
        };
    }

    private void setOriginalScreen() {
        if (this.previous instanceof PackSelectionScreen) {
            this.onClose();
        } else if (this.minecraft != null) {
            PackSelectionScreen originalScreen = this.original.createScreen();
            ((PackSelectionScreenAccessor) originalScreen).packedPacks$setPrevious(this.previous);
            this.minecraft.setScreen(originalScreen);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) return;

        Config.ResourcePacks resourceConfig = this.packsConfig instanceof Config.ResourcePacks resources ? resources : null;
        String requestor = ModAdditions.shouldCommit(this.repository.isResourcePacks());

        if (requestor != null) {
            this.commit();
            PackedPacks.LOGGER.info("[packed_packs] Commiting packs on close at the request of mod '{}'.", requestor);
        } else if (resourceConfig == null || resourceConfig.isApplyOnClose()) {
            this.commit();
        }

        if (resourceConfig == null && !(this.previous instanceof PackSelectionScreen)) {
            this.original.output().accept(this.repository.getRepository()); // validate datapacks
            return;
        }

        if (this.previous instanceof PackSelectionScreenAccessor packScreen) {
            ((PackSelectionModelAccessor) packScreen.getModel()).packed_packs$reset();
            packScreen.invokeReload();
        }

        this.minecraft.setScreen(this.previous);
    }

    private void createWatcher() {
        if (this.watcher == null) {
            this.watcher = PackSelectionScreen.Watcher.create(this.original.packDir());
        }
    }

    private void closeWatcher() {
        if (this.watcher != null) {
            try {
                this.watcher.close();
                this.watcher = null;
            } catch (Exception e) {
                PackedPacks.LOGGER.error("Failed to close watcher for pack directory '{}'.", this.original.packDir(), e);
            }
        }
    }

    private void repositionLists() {
        this.availablePacks.setHeaderVisibility(this.showActionBar);
        this.currentPacks.setHeaderVisibility(this.showActionBar);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        ((HeaderAndFooterLayoutAccess) this.layout).getContentsFrame().setY(this.layout.getHeaderHeight());
        this.profiles.getSidebar().repositionElements();
        this.options.repositionElements();
        this.repositionLists();
    }

    public void toggleActionBar() {
        this.showActionBar = !this.showActionBar;
        PackedPacks.CONFIG.setShowActionBar(this.showActionBar);
        this.repositionLists();
    }

    public void commit() {
        this.currentPacks.getSearchField().setValue("");
        this.updateProfile(this.profiles.getProfile());
        this.repository.selectPacks(this.currentPacks.getList().copyPacks());

        if (this.repository.isResourcePacks()) {
            this.original.output().accept(this.repository.getRepository());
        }
    }

    private void replacePacks(PackList list, ImmutableList<Pack> packs) {
        list.replaceState(new PackList.Snapshot(list, packs, list.copySelection(), list.copyQuery()));
    }

    public void revalidate() {
        CompletableFuture.runAsync(this.repository::refresh).thenRunAsync(() -> {
            PackList availableList = this.availablePacks.getList();
            PackList currentList = this.currentPacks.getList();
            PackRepositoryHelper.PackGroup packs = this.repository.validatePacks(availableList.copyPacks(), currentList.copyPacks());
            this.repository.clearIconCache();
            this.replacePacks(availableList, packs.unselected());
            this.replacePacks(currentList, packs.selected());
            this.clearHistory();
        }, this.minecraft);
    }

    public void reset() {
        PackRepositoryHelper.PackGroup packs = this.repository.getPacksByRequirement();
        this.availablePacks.getList().reload(packs.unselected());
        this.currentPacks.getList().reload(packs.selected());
        this.clearHistory();
    }

    public void useSelected() {
        PackRepositoryHelper.PackGroup packs = this.repository.getPacksBySelected();
        this.availablePacks.getList().reload(packs.unselected());
        this.currentPacks.getList().reload(packs.selected());
        this.clearHistory();
    }

    public void onProfileChange(@Nullable Profile profile) {
        if (profile == null) {
            this.useSelected();
        } else if (!profile.getPackIds().isEmpty()) {
            this.applyProfile(profile);
        } else {
            this.reset();
        }
        this.availablePacks.getSearchField().setValue("");
        this.currentPacks.getSearchField().setValue("");
    }

    private void applyProfile(@NotNull Profile profile) {
        List<Pack> available = this.availablePacks.getList().copyPacks();
        List<Pack> current = this.repository.getPacksById(profile.getPackIds());
        PackRepositoryHelper.PackGroup packs = this.repository.validatePacks(available, current);
        this.availablePacks.getList().reload(packs.unselected());
        this.currentPacks.getList().reload(packs.selected());
        this.clearHistory();
    }

    public void updateProfile(@Nullable Profile profile) {
        if (profile != null) {
            profile.setPacks(this.repository.flattenPacks(this.currentPacks.getList().copyPacks()));
        }
    }

    @Override
    public @NotNull List<PackList> getPackLists() {
        return List.of(this.availablePacks.getList(), this.currentPacks.getList());
    }

    @Override
    public @NotNull PackList getDestination(PackList source) {
        return switch (source) {
            case AvailablePackList ignore -> this.currentPacks.getList();
            case CurrentPackList ignore -> this.availablePacks.getList();
            default -> throw new IllegalStateException("Unexpected value: " + source);
        };
    }

    @Override
    protected void transferFocus(PackList source, PackList destination) {
        super.transferFocus(source, destination);

        if (destination == currentPacks.getList()) {
            currentPacks.getList().scrollToLastSelected();
        }
    }

    @Override
    public void onEvent(PackListEvent event) {
        super.onEvent(event);

        this.profiles.getSidebar().setOpen(false);

        if (event.modifiesTarget()) {
            this.history.push(this.captureState());
        }
    }

    public @Nullable PackLayout<?> getLayoutFromSelectedList() {
        return ObjectsUtil.firstNonNull(
                ObjectsUtil.<PackLayout<?>>pick(this.availablePacks, this.currentPacks, pl -> pl.getList() == this.getFocused()),
                ObjectsUtil.<PackLayout<?>>pick(this.availablePacks, this.currentPacks, pl -> pl.getList().isHovered()),
                ObjectsUtil.<PackLayout<?>>pick(this.availablePacks, this.currentPacks, pl -> pl.getList().isFocused())
        );
    }

    public ToggleableEditBox<Void> focusSearchField(@NotNull PackLayout<?> packLayout) {
        if (!this.showActionBar) this.toggleActionBar();
        ToggleableEditBox<Void> searchField = packLayout.getSearchField();
        this.focus(searchField);
        return searchField;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (codePoint != KEY_SPACE) {
            PackLayout<?> packLayout = this.getLayoutFromSelectedList();
            if (packLayout != null && !packLayout.getSearchField().isFocused()) {
                return this.focusSearchField(packLayout).charTyped(codePoint, modifiers);
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (isRedo(keyCode, modifiers)) {
            return this.history.redo();
        }
        if (isUndo(keyCode, modifiers)) {
            return this.history.undo();
        }
        if (keyCode == KEY_BACKSPACE) {
            PackLayout<?> packLayout = this.getLayoutFromSelectedList();
            if (packLayout != null) {
                ToggleableEditBox<Void> searchField = packLayout.getSearchField();
                if (!searchField.isFocused() && !searchField.getValue().isEmpty()) {
                    return this.focusSearchField(packLayout).keyPressed(keyCode, scanCode, modifiers);
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ToggleableDialogContainer.super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (isClickForward(button)) {
            return this.history.redo();
        }
        if (isClickBack(button)) {
            return this.history.undo();
        }
        if (isLeftClick(button) && !(this.getFocused() instanceof PackList)) {
            this.setFocused(this.children().getFirst());
            this.layout.visitWidgets(w -> w.setFocused(false));
        }
        return false;
    }

    @Override
    public List<ToggleableDialog<?>> getDialogs() {
        return List.of(this.profiles.getSidebar(), this.options);
    }

    public Modal<LinearLayout> getOptionsDialog() {
        return this.options;
    }

    public void clearHistory() {
        this.history.reset(this.captureState());
    }

    @Override
    public @NotNull PackedPacksScreen.Snapshot captureState() {
        return new Snapshot(this, this.availablePacks.getList().captureState(), this.currentPacks.getList().captureState());
    }

    @Override
    public void replaceState(@NotNull Snapshot snapshot) {
        List<Pack> validPacks = this.repository.getPacks();
        this.availablePacks.getSortButton().setValueSilently(snapshot.availablePacks.query().getSort());
        this.availablePacks.getCompatButton().setValueSilently(snapshot.availablePacks.query().isHideIncompatible());
        snapshot.availablePacks.validate(validPacks).restore();
        snapshot.currentPacks.validate(validPacks).restore();
        this.availablePacks.getList().scrollToLastSelected();
        this.currentPacks.getList().scrollToLastSelected();
    }

    public record Snapshot(
            PackedPacksScreen target,
            PackList.Snapshot availablePacks,
            PackList.Snapshot currentPacks
    ) implements Restorable.Snapshot<Snapshot> {
    }
}
