package ru.taximaxim.codekeeper.ui.parts;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.contentmergeviewer.IMergeViewerContentProvider;
import org.eclipse.compare.contentmergeviewer.TextMergeViewer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.di.extensions.EventTopic;
import org.eclipse.e4.core.di.extensions.Preference;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartStack;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.e4.ui.workbench.modeling.EPartService.PartState;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DiffTreeApplier;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement.DiffSide;
import ru.taximaxim.codekeeper.apgdiff.model.exporter.ModelExporter;
import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.UIConsts;
import ru.taximaxim.codekeeper.ui.XmlCommitCommentHistory;
import ru.taximaxim.codekeeper.ui.dbstore.DbPicker;
import ru.taximaxim.codekeeper.ui.differ.DbSource;
import ru.taximaxim.codekeeper.ui.differ.DiffTableViewer;
import ru.taximaxim.codekeeper.ui.differ.TreeDiffer;
import ru.taximaxim.codekeeper.ui.externalcalls.IRepoWorker;
import ru.taximaxim.codekeeper.ui.externalcalls.JGitExec;
import ru.taximaxim.codekeeper.ui.fileutils.Dir;
import ru.taximaxim.codekeeper.ui.handlers.ProjSyncSrc;
import ru.taximaxim.codekeeper.ui.localizations.Messages;
import ru.taximaxim.codekeeper.ui.pgdbproject.PgDbProject;
import cz.startnet.utils.pgdiff.schema.PgDatabase;

public class CommitPartDescr {

    @Inject
    private MPart part;
    
    @Inject
    UISynchronize sync;
    
    @Inject
    private EPartService partService;
    
    @Inject
    @Preference(UIConsts.PREF_PGDUMP_EXE_PATH)
    private String exePgdump;

    @Inject
    @Preference(UIConsts.PREF_PGDUMP_CUSTOM_PARAMS)
    private String pgdumpCustom;
    
    @Inject
    private IEventBroker events;
    
    private Text txtCommitComment;
    private Button btnCommit;
    private DiffTableViewer diffTable;
    private Button btnNone, btnDump, btnDb;
    private Button btnGetChanges;
    private Composite containerSrc;
    private DbPicker dbSrc;
    private TextMergeViewer diffPane;
    private String repoName;
    /**
     * Local repository cache.
     */
    private DbSource dbSource;
    /**
     * Remote DB.
     */
    private DbSource dbTarget;

    @PostConstruct
    private void postConstruct(Composite parent, final PgDbProject proj,
            @Named(UIConsts.PREF_STORE) final IPreferenceStore mainPrefs,
            final EModelService model, final MApplication app) {
        final Shell shell = parent.getShell();
        
        parent.setLayout(new GridLayout());
        repoName = proj.getString(UIConsts.PROJ_PREF_REPO_TYPE);
        
        // upper container
        final Composite containerUpper = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout(3, false);
        gl.marginHeight = gl.marginWidth = 0;
        containerUpper.setLayout(gl);
        containerUpper.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        txtCommitComment = new Text(containerUpper, SWT.BORDER | SWT.MULTI | 
                SWT.H_SCROLL | SWT.V_SCROLL);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.heightHint = 80;
        txtCommitComment.setLayoutData(gd);
        
        final Button btnPrevComments = new Button(containerUpper, SWT.PUSH);
        btnPrevComments.setLayoutData(new GridData(SWT.DEFAULT, SWT.FILL, false,
                false));
        btnPrevComments.setText("\u25bc"); //$NON-NLS-1$
        btnPrevComments.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                List<String> comments = XmlCommitCommentHistory.read();
                
                MenuManager mmComments = new MenuManager();
                if (comments == null || comments.isEmpty()) {
                    mmComments.add(new Action(Messages.commitPartDescr_no_previous_comments) {
                        @Override
                        public boolean isEnabled() {
                            return false;
                        }
                    });
                } else { 
                    for (final String comment : comments) {
                        String menuLabel = comment;
                        if (menuLabel.length() > 120) {
                            menuLabel = menuLabel.substring(0, 120) + "..."; //$NON-NLS-1$
                        }
                        
                        mmComments.add(new Action(menuLabel) {
                            @Override
                            public void run() {
                                txtCommitComment.setText(comment);
                            }
                        });
                    }
                }
                Menu menuComments = mmComments.createContextMenu(shell);
                
                Point loc = btnPrevComments.getLocation();
                Rectangle rectBtn = btnPrevComments.getBounds();
                menuComments.setLocation(shell.getDisplay().map(
                        containerUpper, null,
                        loc.x + rectBtn.width + 1, loc.y + rectBtn.height));
                menuComments.setVisible(true);
            }
        });
        
        btnCommit = new Button(containerUpper, SWT.PUSH);
        btnCommit.setLayoutData(new GridData(SWT.DEFAULT, SWT.FILL, false,
                false));
        btnCommit.setText(Messages.commitPartDescr_commit);
        btnCommit.setEnabled(false);
        btnCommit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                final String commitComment = txtCommitComment.getText();
                if (diffTable.viewer.getCheckedElements().length < 1){
                    MessageBox mb = new MessageBox(shell, SWT.ICON_INFORMATION);
                    mb.setMessage(Messages.please_check_at_least_one_row);
                    mb.setText(Messages.empty_selection);
                    mb.open();
                    return;
                }
                if (commitComment.isEmpty()) {
                    MessageBox mb = new MessageBox(shell, SWT.ICON_INFORMATION);
                    mb.setMessage(Messages.commitPartDescr_comment_required);
                    mb.setText(Messages.commitPartDescr_please_enter_a_comment_for_the_commit);
                    mb.open();
                    return;
                }
                
                XmlCommitCommentHistory.add(commitComment);

                final TreeElement filtered = diffTable.filterDiffTree();
                IRunnableWithProgress commitRunnable = new IRunnableWithProgress() {

                    @Override
                    public void run(IProgressMonitor monitor)
                            throws InvocationTargetException,
                            InterruptedException {
                        SubMonitor pm = SubMonitor.convert(monitor,
                                Messages.commitPartDescr_commiting, 3);

                        pm.newChild(1).subTask(Messages.commitPartDescr_modifying_db_model); // 1
                        DiffTreeApplier applier = new DiffTreeApplier(dbSource
                                .getDbObject(), dbTarget.getDbObject(),
                                filtered);
                        PgDatabase dbNew = applier.apply();

                        pm.newChild(1).subTask(Messages.commitPartDescr_exporting_db_model); // 2
                        File workingDir = proj.getProjectWorkingDir();
                        try {
                            IRepoWorker repo = new JGitExec(proj,
                                    mainPrefs.getString(UIConsts.PREF_GIT_KEY_PRIVATE_FILE));

                            for (ApgdiffConsts.WORK_DIR_NAMES subdirName : ApgdiffConsts.WORK_DIR_NAMES.values()) {
                                File subdir = new File(workingDir, subdirName.toString());
                                if (subdir.exists()) {
                                    Dir.deleteRecursive(subdir);
                                }
                            }
                            
                            new ModelExporter(workingDir.getAbsolutePath(),
                                    dbNew,
                                    proj.getString(UIConsts.PROJ_PREF_ENCODING))
                                    .export();

                            pm.newChild(1).subTask(repoName + " committing..."); // 3 //$NON-NLS-1$
                            repo.repoRemoveMissingAddNew(workingDir);
                            repo.repoCommit(workingDir, commitComment);
                        } catch (IOException ex) {
                            throw new InvocationTargetException(ex,
                                    Messages.commitPartDescr_ioexception_while_modifying_project);
                        }

                        monitor.done();
                    }
                };

                try {
                    Log.log(Log.LOG_INFO, "Commit pressed. Commiting to " + //$NON-NLS-1$
                            proj.getString(UIConsts.PROJ_PREF_REPO_URL));
                    new ProgressMonitorDialog(shell).run(true, false,
                            commitRunnable);
                } catch (InvocationTargetException ex) {
                    throw new IllegalStateException(
                            Messages.error_in_the_project_modifier_thread, ex);
                } catch (InterruptedException ex) {
                    // assume run() was called as non cancelable
                    throw new IllegalStateException(
                            Messages.project_modifier_thread_cancelled_shouldnt_happen,
                            ex);
                }

                Console.addMessage(Messages.commitPartDescr_success_project_updated);

                // reopen project because file structure has been changed
                events.send(UIConsts.EVENT_REOPEN_PROJECT, proj);
            }
        });
        // end upper commit comment container

        SashForm sashOuter = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);
        sashOuter.setLayoutData(new GridData(GridData.FILL_BOTH));

        // middle container
        final Composite containerDb = new Composite(sashOuter, SWT.NONE);
        gl = new GridLayout(3, false);
        gl.marginHeight = gl.marginWidth = 0;
        gl.horizontalSpacing = gl.verticalSpacing = 2;
        containerDb.setLayout(gl);
        
        diffTable = new DiffTableViewer(containerDb, SWT.NONE);
        diffTable.setLayoutData(new GridData(GridData.FILL_BOTH));
        diffTable.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
                    
                    @Override
                    public void selectionChanged(SelectionChangedEvent event) {
                        StructuredSelection selection = ((StructuredSelection) event
                                .getSelection());
                        
                        if (selection.size() != 1) {
                            diffPane.setInput(null);
                        } else {
                            TreeElement el = (TreeElement) selection.getFirstElement();
                            diffPane.setInput(el);
                        }
                    }
                });
        
        // flip button set up
        final Button btnFlipDbPicker = new Button(containerDb, SWT.PUSH | SWT.FLAT);
        btnFlipDbPicker.setText("\u25B8"); //$NON-NLS-1$
        gd = new GridData(GridData.FILL_VERTICAL);
        gd.widthHint = 20;
        btnFlipDbPicker.setLayoutData(gd);
        btnFlipDbPicker.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean open = containerSrc.getVisible();
                
                containerSrc.setVisible(!open);
                ((GridData) containerSrc.getLayoutData()).exclude = open;
                containerDb.layout();
                
                btnFlipDbPicker.setText(open ? "\u25C2" // ◂ //$NON-NLS-1$
                        : "\u25B8"); // ▸ //$NON-NLS-1$
            }
        });
        
        // middle right container
        containerSrc = new Composite(containerDb, SWT.NONE);
        gl = new GridLayout(2, false);
        gl.marginHeight = gl.marginWidth = 0;
        containerSrc.setLayout(gl);

        gd = new GridData(SWT.FILL, SWT.FILL, false, true);
        gd.minimumWidth = gd.minimumHeight = 300;
        containerSrc.setLayoutData(gd);
        
        Group grpSrc = new Group(containerSrc, SWT.NONE);
        grpSrc.setText(Messages.commitPartDescr_get_changes_from);
        grpSrc.setLayout(new GridLayout(3, false));

        btnNone = new Button(grpSrc, SWT.RADIO);
        btnNone.setText(Messages.none);
        btnNone.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(false);
                btnGetChanges.setEnabled(false);
            }
        });

        btnDump = new Button(grpSrc, SWT.RADIO);
        btnDump.setText(Messages.dump);
        btnDump.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(false);
                btnGetChanges.setEnabled(true);
            }
        });

        btnDb = new Button(grpSrc, SWT.RADIO);
        btnDb.setText(Messages.db);
        btnDb.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(true);
                btnGetChanges.setEnabled(true);
            }
        });

        btnGetChanges = new Button(containerSrc, SWT.PUSH);
        btnGetChanges.setText(Messages.get_changes);
        btnGetChanges.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
                false));
        btnGetChanges.addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (!ProjSyncSrc.sync(proj, shell, mainPrefs)) {
                    return;
                }
                
                dbSource = DbSource.fromProject(proj);
                if (btnDump.getSelection()) {
                    FileDialog dialog = new FileDialog(shell);
                    dialog.setText(Messages.choose_dump_file_with_changes);
                    String dumpfile = dialog.open();
                    if (dumpfile != null) {
                        dbTarget = DbSource.fromFile(dumpfile,
                                proj.getString(UIConsts.PROJ_PREF_ENCODING));
                    } else {
                        return;
                    }
                } else if (btnDb.getSelection()) {
                    int port;
                    try {
                        String sPort = dbSrc.txtDbPort.getText();
                        port = sPort.isEmpty()? 0 : Integer.parseInt(sPort);
                    } catch (NumberFormatException ex) {
                        MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR);
                        mb.setText(Messages.bad_port);
                        mb.setMessage(Messages.port_must_be_a_number);
                        mb.open();
                        return;
                    }
                    dbTarget = DbSource.fromDb(exePgdump, pgdumpCustom,
                            dbSrc.txtDbHost.getText(), port,
                            dbSrc.txtDbUser.getText(),
                            dbSrc.txtDbPass.getText(),
                            dbSrc.txtDbName.getText(),
                            proj.getString(UIConsts.PROJ_PREF_ENCODING));
                } else {
                    throw new IllegalStateException(
                            Messages.undefined_surce_for_db_changes);
                }
                
                Log.log(Log.LOG_INFO, "Getting changes for commit"); //$NON-NLS-1$
                TreeDiffer treediffer = new TreeDiffer(dbSource, dbTarget);
                try {
                    new ProgressMonitorDialog(shell).run(true, false, treediffer);
                } catch (InvocationTargetException ex) {
                    throw new IllegalStateException(Messages.error_in_differ_thread, ex);
                } catch (InterruptedException ex) {
                    // assume run() was called as non cancelable
                    throw new IllegalStateException(
                            Messages.differ_thread_cancelled_shouldnt_happen, ex);
                }

                diffTable.setInput(treediffer);
                diffPane.setInput(null);
                btnCommit.setEnabled(true);
            }
        });

        dbSrc = new DbPicker(containerSrc, SWT.NONE, mainPrefs, false);
        dbSrc.setText(Messages.db_source);
        dbSrc.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2,
                1));

        boolean useDbPicker = false;
        String src = proj.getString(UIConsts.PROJ_PREF_SOURCE);
        if (src.equals(UIConsts.PROJ_SOURCE_TYPE_NONE)) {
            btnNone.setSelection(true);
            btnGetChanges.setEnabled(false);
        } else if (src.equals(UIConsts.PROJ_SOURCE_TYPE_DUMP)) {
            btnDump.setSelection(true);
        } else {
            btnDb.setSelection(true);
            useDbPicker = true;
        }
        showDbPicker(useDbPicker);

        if (useDbPicker) {
            dbSrc.txtDbName.setText(proj.getString(UIConsts.PROJ_PREF_DB_NAME));
            dbSrc.txtDbUser.setText(proj.getString(UIConsts.PROJ_PREF_DB_USER));
            dbSrc.txtDbPass.setText(proj.getString(UIConsts.PROJ_PREF_DB_PASS));
            dbSrc.txtDbHost.setText(proj.getString(UIConsts.PROJ_PREF_DB_HOST));
            dbSrc.txtDbPort.setText(String.valueOf(proj
                    .getInt(UIConsts.PROJ_PREF_DB_PORT)));
        }
        // end middle right container
        // end middle container

        CompareConfiguration conf = new CompareConfiguration();
        conf.setLeftEditable(false);
        conf.setRightEditable(false);
        
        diffPane = new TextMergeViewer(sashOuter, SWT.BORDER, conf) {
            
            @Override
            protected SourceViewer createSourceViewer(Composite parent, int textOrientation) {
                CompositeRuler ruler = new CompositeRuler();
                ruler.addDecorator(0, new LineNumberRulerColumn());
                
                return new SourceViewer(parent, ruler,
                        textOrientation | SWT.H_SCROLL | SWT.V_SCROLL);
            }
        };
        diffPane.setContentProvider(new IMergeViewerContentProvider() {
            
            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            }
            
            @Override
            public void dispose() {
            }
            
            @Override
            public boolean showAncestor(Object input) {
                return false;
            }
            
            @Override
            public void saveRightContent(Object input, byte[] bytes) {
            }
            
            @Override
            public void saveLeftContent(Object input, byte[] bytes) {
            }
            
            @Override
            public boolean isRightEditable(Object input) {
                return false;
            }
            
            @Override
            public boolean isLeftEditable(Object input) {
                return false;
            }
            
            @Override
            public String getRightLabel(Object input) {
                return Messages.commitPartDescr_to + repoName;
            }
            
            @Override
            public Image getRightImage(Object input) {
                return null;
            }
            
            @Override
            public Object getRightContent(Object input) {
                TreeElement el = (TreeElement) input;
                if (el != null && (el.getSide() == DiffSide.LEFT
                        || el.getSide() == DiffSide.BOTH)) {
                    return new Document(
                            el.getPgStatement(dbSource.getDbObject())
                                    .getCreationSQL());
                } else {
                    return new Document();
                }
            }
            
            @Override
            public String getLeftLabel(Object input) {
                return Messages.commitPartDescr_from_database;
            }
            
            @Override
            public Image getLeftImage(Object input) {
                return null;
            }
            
            @Override
            public Object getLeftContent(Object input) {
                TreeElement el = (TreeElement) input;
                if (el != null && (el.getSide() == DiffSide.RIGHT
                        || el.getSide() == DiffSide.BOTH)) {
                    return new Document(
                            el.getPgStatement(dbTarget.getDbObject())
                                .getCreationSQL());
                } else {
                    return new Document();
                }
            }
            
            @Override
            public String getAncestorLabel(Object input) {
                return null;
            }
            
            @Override
            public Image getAncestorImage(Object input) {
                return null;
            }
            
            @Override
            public Object getAncestorContent(Object input) {
                return null;
            }
        });
    }

    private void showDbPicker(boolean show) {
        ((GridData) dbSrc.getLayoutData()).exclude = !show;
        dbSrc.setVisible(show);

        dbSrc.getParent().layout();
    }

    @Inject
    private void changeProject(
            PgDbProject proj,
            @Optional
            @EventTopic(UIConsts.EVENT_REOPEN_PROJECT)
            PgDbProject proj2) {
        if (proj == null
                || !proj.getProjectFile().toString().equals(
                        part.getPersistedState().get(UIConsts.PART_SYNC_ID))) {
            sync.asyncExec(new Runnable() {
                
                @Override
                public void run() {
                    partService.hidePart(part);
                }
            });
        } else if (proj2 != null) {
            sync.asyncExec(new Runnable() {
                
                @Override
                public void run() {
                    diffTable.setInput(null);
                    diffPane.setInput(null);
                    txtCommitComment.setText(""); //$NON-NLS-1$
                    btnCommit.setEnabled(false);
                }
            });
        }
    }

    public static void openNew(String projectPath, EPartService partService,
            EModelService model, MApplication app) {
        for (MPart existingPart : model.findElements(app, UIConsts.PART_SYNC,
                MPart.class, null)) {
            if (projectPath.equals(existingPart.getPersistedState().get(
                    UIConsts.PART_SYNC_ID))) {
                partService.hidePart(existingPart);
                break;
            }
        }

        MPart syncPart = partService.createPart(UIConsts.PART_SYNC);
        syncPart.getPersistedState().put(UIConsts.PART_SYNC_ID, projectPath);
        ((MPartStack) model.find(UIConsts.PART_STACK_EDITORS, app))
                .getChildren().add(syncPart);
        partService.showPart(syncPart, PartState.CREATE);
    }
}