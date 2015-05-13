package ru.taximaxim.codekeeper.ui.prefs;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import ru.taximaxim.codekeeper.ui.Activator;
import ru.taximaxim.codekeeper.ui.UIConsts.FILE;
import ru.taximaxim.codekeeper.ui.localizations.Messages;

public abstract class PrefListEditor<T> extends Composite {
    
    protected StructuredViewer viewerObjs;
    private LinkedList<T> objsList = new LinkedList<>();
    private final boolean doSorting;
    private Button upBtn;
    private Button downBtn;
    private Button btnDelete;
    private Button btnAdd;
    private T newVal;
    
    public PrefListEditor(Composite parent, boolean doSorting) {
        super(parent, SWT.NONE);
        
        this.doSorting = doSorting;
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        this.setLayout(gridLayout);
        this.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        populateUiContent(this);
    }
    
    private void populateUiContent(Composite parent){
        LocalResourceManager lrm = new LocalResourceManager(
                JFaceResources.getResources(), this);
        final Text txtNewValue = new Text(parent, SWT.BORDER);
        txtNewValue.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        btnAdd = new Button(parent, SWT.PUSH);
        btnAdd.setToolTipText(Messages.add);
        btnAdd.setImage(lrm.createImage(ImageDescriptor.createFromURL(Activator
                .getContext().getBundle().getResource(FILE.ICONADD))));
        btnAdd.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                T newValue = getObject(txtNewValue.getText().trim());
                if (newValue != null && !objsList.contains(newValue)) {
                    objsList.add(0, newValue);
                    newVal = newValue; 
                    txtNewValue.setText(""); //$NON-NLS-1$
                    viewerObjs.refresh();
                } else {
                    newVal = null;
                }
            }
        });

        createViewer(this);

        Composite comp = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(3, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        comp.setLayout(gridLayout);
        comp.setLayoutData(new GridData());
        
        btnDelete = new Button(comp, SWT.PUSH);
        btnDelete.setLayoutData(new GridData());
        btnDelete.setToolTipText(Messages.delete);
        btnDelete.setImage(lrm.createImage(ImageDescriptor.createFromURL(
                Activator.getContext().getBundle().getResource(
                        FILE.ICONDEL))));
        btnDelete.addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                deleteSelected();
            }
        });
        
        upBtn = new Button(comp, SWT.PUSH);
        upBtn.setImage(lrm.createImage(ImageDescriptor.createFromURL(
                Activator.getContext().getBundle().getResource(
                        FILE.ICONUP))));
        upBtn.setLayoutData(new GridData(GridData.END));
        upBtn.addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (viewerObjs.getSelection().isEmpty()) {
                    return;
                }
                IStructuredSelection selection = (IStructuredSelection) viewerObjs
                        .getSelection();
                @SuppressWarnings("unchecked")
                T sel = ((T) selection.getFirstElement());
                ListIterator<T> it = objsList.listIterator();
                while (it.hasNext()) {
                    T match = it.next();
                    if (match == sel) {
                        it.previous();
                        if (it.hasPrevious()) {
                            T prev = it.previous();
                            it.set(sel);
                            it.next();
                            it.next();
                            it.set(prev);
                            viewerObjs.refresh();
                        }
                        return;
                    }
                }
            }
        });
        
        downBtn = new Button(comp, SWT.PUSH);
        downBtn.setImage(lrm.createImage(ImageDescriptor.createFromURL(
                Activator.getContext().getBundle().getResource(
                        FILE.ICONDOWN))));
        downBtn.setLayoutData(new GridData(GridData.END));
        downBtn.addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (viewerObjs.getSelection().isEmpty()) {
                    return;
                }
                IStructuredSelection selection = (IStructuredSelection) viewerObjs
                        .getSelection();
                @SuppressWarnings("unchecked")
                T sel = ((T) selection.getFirstElement());
                ListIterator<T> it = objsList.listIterator();
                while (it.hasNext()) {
                    T match = it.next();
                    if (match == sel) {
                        if (it.hasNext()) {
                            T next = it.next();
                            it.set(sel);
                            it.previous();
                            it.previous();
                            it.set(next);
                            viewerObjs.refresh();
                        }
                        return;
                    }
                }
            }
        });
        if (doSorting){
            viewerObjs.setSorter(new ViewerSorter());
            upBtn.setEnabled(false);
            downBtn.setEnabled(false);
        }
    }
    
    protected abstract T getObject(String name);

    protected abstract void createViewer(Composite parent);
    
    public Object deleteSelected() {
        IStructuredSelection selection = 
                (IStructuredSelection) viewerObjs.getSelection();
        Object objToRemove = selection.getFirstElement();
        if (objToRemove == null) {
            return null;
        }

        objsList.remove(objToRemove);
        viewerObjs.refresh();
        return objToRemove;
    }
    
    public Object getSelected() {
        return ((IStructuredSelection)viewerObjs.getSelection()).getFirstElement();
    }
    
    public List<T> getList(){
        return objsList;
    }
    
    public StructuredViewer getListViewer() {
        return viewerObjs;
    }
    
    public Button getDelDtn() {
        return btnDelete;
    }
    
    public Button getAddBtn() {
        return btnAdd;
    }
    
    public void setInputList(LinkedList<T> list){
        objsList = list;
        viewerObjs.setInput(objsList);
        viewerObjs.refresh();
    }

    public void select(T name) {
        viewerObjs.setSelection(new StructuredSelection(name));
    }
    
    public void select(int index) {
        viewerObjs.setSelection(new StructuredSelection(objsList.get(index)));
    }

    public T getNewEntry() {
        return newVal;
    }
}