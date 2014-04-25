package ru.taximaxim.codekeeper.ui.parts;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.di.extensions.EventTopic;
import org.eclipse.e4.core.di.extensions.Preference;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartStack;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.e4.ui.workbench.modeling.EPartService.PartState;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement.DiffSide;
import ru.taximaxim.codekeeper.ui.ExceptionNotifyHelper;
import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.TextDialog;
import ru.taximaxim.codekeeper.ui.UIConsts;
import ru.taximaxim.codekeeper.ui.dbstore.DbPicker;
import ru.taximaxim.codekeeper.ui.differ.DbSource;
import ru.taximaxim.codekeeper.ui.differ.DiffTableViewer;
import ru.taximaxim.codekeeper.ui.differ.Differ;
import ru.taximaxim.codekeeper.ui.differ.TreeDiffer;
import ru.taximaxim.codekeeper.ui.handlers.ProjSyncSrc;
import ru.taximaxim.codekeeper.ui.pgdbproject.PgDbProject;

public class DiffPartDescr {

    @Inject
    private MPart part;
    @Inject
    private EPartService partService;
    @Inject
    @Preference(value = UIConsts.PREF_PGDUMP_EXE_PATH)
    private String exePgdump;

    private Button btnGetLatest;
    private DiffTableViewer diffTable;
    private Button btnNone, btnDump, btnDb;
    private Button btnGetChanges;
    private DbPicker dbSrc;
    private Text txtDb, txtRepo;

    /**
     * Remote DB.
     */
    private DbSource dbSource;
    /**
     * Local repo cache.
     */
    private DbSource dbTarget;

    @PostConstruct
    private void postConstruct(Composite parent, final PgDbProject proj,
            @Named(UIConsts.PREF_STORE) final IPreferenceStore mainPrefs,
            @Named(IServiceConstants.ACTIVE_SHELL) final Shell shell,
            final EModelService model, final MApplication app) {
        parent.setLayout(new GridLayout());
        // upper container
        Composite containerUpper = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout(1, false);
        gl.marginHeight = gl.marginWidth = 0;
        containerUpper.setLayout(gl);
        containerUpper.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        btnGetLatest = new Button(containerUpper, SWT.PUSH);
        btnGetLatest.setText("Get Latest");
        btnGetLatest.setEnabled(false);
        btnGetLatest.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (diffTable.viewer.getCheckedElements().length < 1){
                    MessageBox mb = new MessageBox(shell, SWT.ICON_INFORMATION);
                    mb.setMessage("Please, check at least one row.");
                    mb.setText("Empty selection");
                    mb.open();
                    return;
                }
                final TreeElement filtered = diffTable.filterDiffTree();

                Differ differ = new Differ(DbSource.fromFilter(dbSource,
                        filtered, DiffSide.LEFT), DbSource.fromFilter(dbTarget,
                        filtered, DiffSide.RIGHT));

                try {
                    new ProgressMonitorDialog(shell).run(true, false, differ);
                } catch (InvocationTargetException ex) {
                    throw new IllegalStateException(
                            "Error in the project modifier thread", ex);
                } catch (InterruptedException ex) {
                    // assume run() was called as non cancelable
                    throw new IllegalStateException(
                            "Project modifier thread cancelled. Shouldn't happen!",
                            ex);
                }

                TextDialog dialog = new TextDialog(shell,
                        TextDialog.INFORMATION, "Diff script",
                        "This will apply selected changes to your database",
                        differ.getDiffDirect(),
                        new String[] { IDialogConstants.OK_LABEL }, 0);
                dialog.open();
            }
        });
        // end upper commit comment container

        SashForm sashOuter = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);
        sashOuter.setLayoutData(new GridData(GridData.FILL_BOTH));

        // middle container
        SashForm sashDb = new SashForm(sashOuter, SWT.HORIZONTAL | SWT.SMOOTH);
        sashDb.setSashWidth(8);
        // ВКЛАДКА Get latest
        diffTable = new DiffTableViewer(sashDb, SWT.NONE);
        diffTable.viewer
                .addSelectionChangedListener(new ISelectionChangedListener() {
                    @Override
                    public void selectionChanged(SelectionChangedEvent event) {
                        StructuredSelection selection = ((StructuredSelection) event
                                .getSelection());
                        if (selection.size() != 1) {
                            txtRepo.setText("");
                            txtDb.setText("");
                            return;
                        }
                        TreeElement el = (TreeElement) selection.getFirstElement();

                        if (el.getSide() == DiffSide.LEFT
                                || el.getSide() == DiffSide.BOTH) {
                            txtDb.setText(el.getPgStatement(
                                    dbSource.getDbObject()).getCreationSQL());
                        } else {
                            txtDb.setText("");
                        }
                        if (el.getSide() == DiffSide.RIGHT
                                || el.getSide() == DiffSide.BOTH) {
                            txtRepo.setText(el.getPgStatement(
                                    dbTarget.getDbObject()).getCreationSQL());
                        } else {
                            txtRepo.setText("");
                        }
                    }
                });
        // middle right container
        Composite containerSrc = new Composite(sashDb, SWT.NONE);
        gl = new GridLayout(2, false);
        gl.marginHeight = gl.marginWidth = 0;
        containerSrc.setLayout(gl);

        Group grpSrc = new Group(containerSrc, SWT.NONE);
        grpSrc.setText("Get changes from");
        grpSrc.setLayout(new GridLayout(3, false));

        btnNone = new Button(grpSrc, SWT.RADIO);
        btnNone.setText("None");
        btnNone.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(false);
                btnGetChanges.setEnabled(false);
            }
        });

        btnDump = new Button(grpSrc, SWT.RADIO);
        btnDump.setText("Dump");
        btnDump.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(false);
                btnGetChanges.setEnabled(true);
            }
        });

        btnDb = new Button(grpSrc, SWT.RADIO);
        btnDb.setText("DB");
        btnDb.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(true);
                btnGetChanges.setEnabled(true);
            }
        });

        btnGetChanges = new Button(containerSrc, SWT.PUSH);
        btnGetChanges.setText("Get Changes");
        btnGetChanges.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
                false));
        btnGetChanges.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                try {
                    if (!ProjSyncSrc.sync(proj, shell, mainPrefs)) {
                        return;
                    }
                } catch (InvocationTargetException | IOException ex) {
                    throw new IllegalStateException(
                            "Unexpected error while trying to sync repository cache!",
                            ex);
                }
                
                dbTarget = DbSource.fromProject(proj);
                if (btnDump.getSelection()) {
                    FileDialog dialog = new FileDialog(shell);
                    dialog.setText("Choose dump file with changes...");
                    String dumpfile = dialog.open();
                    if (dumpfile != null) {
                        dbSource = DbSource.fromFile(dumpfile,
                                proj.getString(UIConsts.PROJ_PREF_ENCODING));
                    } else {
                        return;
                    }
                } else if (btnDb.getSelection()) {
                    int port;
                    try {
                        port = Integer.parseInt(dbSrc.txtDbPort.getText());
                    } catch (NumberFormatException ex) {
                        MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR);
                        mb.setText("Bad port!");
                        mb.setMessage("Port must be a number!");
                        return;
                    }

                    dbSource = DbSource.fromDb(exePgdump,
                            dbSrc.txtDbHost.getText(), port,
                            dbSrc.txtDbUser.getText(),
                            dbSrc.txtDbPass.getText(),
                            dbSrc.txtDbName.getText(),
                            proj.getString(UIConsts.PROJ_PREF_ENCODING));
                } else {
                    throw new IllegalStateException(
                            "Undefined source for DB changes!");
                }
                
                Log.log(Log.LOG_INFO, "Getting changes to generate script");
                TreeDiffer treediffer = new TreeDiffer(dbSource, dbTarget);
                try {
                    new ProgressMonitorDialog(shell).run(true, false,
                            treediffer);
                } catch (InvocationTargetException ex) {
                    ExceptionNotifyHelper.notifyAndThrow(new IllegalStateException("Error in differ thread",
                            ex), shell);
                } catch (InterruptedException ex) {
                    // assume run() was called as non cancelable
                    ExceptionNotifyHelper.notifyAndThrow(new IllegalStateException(
                            "Differ thread cancelled. Shouldn't happen!", ex), shell);
                }

                diffTable.setInput(treediffer.getDiffTree());

                txtDb.setText("");
                txtRepo.setText("");

                btnGetLatest.setEnabled(true);
            }
        });

        dbSrc = new DbPicker(containerSrc, SWT.NONE, mainPrefs, false);
        dbSrc.setText("DB Source");
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

        sashDb.setWeights(new int[] { 7750, 2250 });
        // end middle container

        // lower diff container
        SashForm sashDiff = new SashForm(sashOuter, SWT.HORIZONTAL | SWT.SMOOTH);
        sashDiff.setSashWidth(8);

        Composite containerLeft = new Composite(sashDiff, SWT.NONE);
        gl = new GridLayout();
        gl.marginHeight = gl.marginWidth = 0;
        containerLeft.setLayout(gl);

        Label l = new Label(containerLeft, SWT.RIGHT);
        l.setText("Database version <  <");
        l.setLayoutData(new GridData(SWT.RIGHT, SWT.DEFAULT, false, false));
        txtDb = new Text(containerLeft, SWT.BORDER | SWT.H_SCROLL
                | SWT.V_SCROLL | SWT.MULTI | SWT.READ_ONLY);
        txtDb.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
        txtDb.setBackground(shell.getDisplay().getSystemColor(
                SWT.COLOR_LIST_BACKGROUND));
        txtDb.setLayoutData(new GridData(GridData.FILL_BOTH));

        Composite containerRight = new Composite(sashDiff, SWT.NONE);
        gl = new GridLayout();
        gl.marginHeight = gl.marginWidth = 0;
        containerRight.setLayout(gl);

        new Label(containerRight, SWT.NONE).setText("< " + 
                proj.getString(UIConsts.PROJ_PREF_REPO_TYPE) + " version");
        txtRepo = new Text(containerRight, SWT.BORDER | SWT.H_SCROLL
                | SWT.V_SCROLL | SWT.MULTI | SWT.READ_ONLY);
        txtRepo.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
        txtRepo.setBackground(shell.getDisplay().getSystemColor(
                SWT.COLOR_LIST_BACKGROUND));
        txtRepo.setLayoutData(new GridData(GridData.FILL_BOTH));
        // end lower diff container
    }

    private void showDbPicker(boolean show) {
        ((GridData) dbSrc.getLayoutData()).exclude = !show;
        dbSrc.setVisible(show);

        dbSrc.getParent().layout();
    }

    @Inject
    private void changeProject(PgDbProject proj, @Optional @Named("__DUMMY__")
                @EventTopic(UIConsts.EVENT_REOPEN_PROJECT) PgDbProject proj2) {
        if (proj == null
                || !proj.getProjectFile().toString().equals(
                        part.getPersistedState().get(UIConsts.PART_DIFF_ID))) {
            partService.hidePart(part);
        } else if (proj2 != null) {
            diffTable.setInput(null);
            txtDb.setText("");
            txtRepo.setText("");
        }
    }

    public static void openNew(String projectPath, EPartService partService,
            EModelService model, MApplication app) {
        for (MPart existingPart : model.findElements(app, UIConsts.PART_DIFF,
                MPart.class, null)) {
            if (projectPath.equals(existingPart.getPersistedState().get(
                    UIConsts.PART_DIFF_ID))) {
                partService.hidePart(existingPart);
                break;
            }
        }

        MPart diffPart = partService.createPart(UIConsts.PART_DIFF);
        diffPart.getPersistedState().put(UIConsts.PART_DIFF_ID, projectPath);
        ((MPartStack) model.find(UIConsts.PART_STACK_EDITORS, app))
                .getChildren().add(diffPart);
        partService.showPart(diffPart, PartState.CREATE);
    }
}