package ru.taximaxim.codekeeper.ui.prefs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import ru.taximaxim.codekeeper.ui.Activator;

public class SQLEditorSytaxColoring extends FieldEditorPreferencePage implements
        IWorkbenchPreferencePage {

    enum StatementsTypes {
        FUNCTIONS("prefsFunction", "Function"),
        PREDICATES("prefsPredicates", "Predicates"),
        RESERVED_WORDS("prefsReservedWords", "ReservedWords"),
        UN_RESERVED_WORDS("prefsUnReservedWords", "UnReservedWords"),
        TYPES("prefsTypes", "Types"),
        CONSTANTS("prefsConstants", "Constants"),
        SINGLE_LINE_COMMENTS("prefsSingleLineComments", "SingleLineComments"),
        GLOBAL_VARIABLES("prefsGlobalVariables", "GlobalVariables");
        
        private String name;
        private String tranclatedName;
        private StatementsTypes(String name, String tranclatedName) {
            this.name = name;
            this.tranclatedName = tranclatedName;
        }
        @Override
        public String toString() {
            return tranclatedName;
        }
        public String getPrefName() {
            return name;
        }
    }
    
    class SyntaxModel {
        
        public SyntaxModel(StatementsTypes type, IPreferenceStore prefStore) {
            this.type = type;
            this.prefStore = prefStore;
        }
        protected RGB getColor() {
            return color;
        }
        protected void setColor(RGB color) {
            this.color = color;
        }
        protected void setBold(boolean bold) {
            this.bold = bold;
        }
        protected void setItalic(boolean italic) {
            this.italic = italic;
        }
        protected void setStrikethrough(boolean strikethrough) {
            this.strikethrough = strikethrough;
        }
        protected void setUnderline(boolean underline) {
            this.underline = underline;
        }
        protected boolean isBold() {
            return bold;
        }
        protected boolean isItalic() {
            return italic;
        }
        protected boolean isStrikethrough() {
            return strikethrough;
        }
        protected boolean isUnderline() {
            return underline;
        }
        public StatementsTypes getType() {
            return type;
        }
        public String getPrefName() {
            return type.getPrefName();
        }

        private StatementsTypes type;
        private RGB color;
        private boolean bold;
        private boolean italic;
        private boolean strikethrough;
        private boolean underline;
        private IPreferenceStore prefStore;
        
        SyntaxModel load() {
            color = PreferenceConverter.getColor(prefStore,
                    type.getPrefName() + ".Color");
            bold = prefStore.getBoolean(type.getPrefName() + ".Bold");
            italic = prefStore.getBoolean(type.getPrefName() + ".Italic");
            strikethrough = prefStore.getBoolean(type.getPrefName() + ".strikethrough");
            underline = prefStore.getBoolean(type.getPrefName() + ".underline");
            return this;
        }
        
        @Override
        public String toString() {
            return type.toString();
        }
        public void store() {
            PreferenceConverter.setValue(prefStore, type.getPrefName() + ".Color", color);
            prefStore.setValue(type.getPrefName() + ".Bold", bold);
            prefStore.setValue(type.getPrefName() + ".Italic", italic);
            prefStore.setValue(type.getPrefName() + ".strikethrough", strikethrough);
            prefStore.setValue(type.getPrefName() + ".underline", underline);
        }
    }
    
    private ListViewer listIgnoredObjs;
    private ColorFieldEditor colorFieldEditor;
    private BooleanFieldEditor boldField;
    private BooleanFieldEditor italicField;
    private BooleanFieldEditor strikethroughField;
    private BooleanFieldEditor underlineField;
    private IPreferenceStore store;
    private SyntaxModel sel;
    private List<SyntaxModel> input = new ArrayList<>();
    private Group group;

    public SQLEditorSytaxColoring() {
        super(GRID);
    }

    @Override
    public void init(IWorkbench workbench) {
        store = Activator.getDefault().getPreferenceStore();
        setPreferenceStore(store);
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        composite.setLayout(gridLayout);
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        listIgnoredObjs = new ListViewer(composite);
        listIgnoredObjs.getList().setLayoutData(new GridData(GridData.FILL_BOTH));

        listIgnoredObjs.setContentProvider(new ArrayContentProvider());
        listIgnoredObjs.setLabelProvider(new LabelProvider());
        listIgnoredObjs.setSorter(new ViewerSorter());
        listIgnoredObjs.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                sel = (SyntaxModel) ((StructuredSelection) event
                                .getSelection()).getFirstElement();
                if (sel == null) {
                    return;
                }
                colorFieldEditor.getColorSelector().setColorValue(sel.getColor());
                ((Button)boldField.getDescriptionControl(group)).setSelection(sel.isBold());
                ((Button)italicField.getDescriptionControl(group)).setSelection(sel.isItalic());
                ((Button)strikethroughField.getDescriptionControl(group)).setSelection(sel.isStrikethrough());
                ((Button)underlineField.getDescriptionControl(group)).setSelection(sel.isUnderline());
            }
            
        });
        
        group = new Group(composite, SWT.NONE);
        group.setLayoutData(new GridData(GridData.FILL_BOTH));
        group.setText("Font and color preference");
        StatementsTypes first = StatementsTypes.CONSTANTS;
        colorFieldEditor = new ColorFieldEditor(first.getPrefName() + ".Color", "Color:", group);
        colorFieldEditor.setPreferenceStore(store);
        colorFieldEditor.getColorSelector().addListener(new IPropertyChangeListener() {
            
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                sel.setColor((RGB)event.getNewValue());
            }
        });
        addField(colorFieldEditor);
        
        boldField = new BooleanFieldEditor(first.getPrefName() + ".Bold", "Bold:", group);
        ((Button)boldField.getDescriptionControl(group)).addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                sel.setBold(((Button)e.widget).getSelection());
            }
        });
        addField(boldField);
        
        italicField = new BooleanFieldEditor(first.getPrefName() + ".Italic", "Italic", group);
        ((Button)italicField.getDescriptionControl(group)).addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                sel.setItalic(((Button)e.widget).getSelection());
            }
        });
        addField(italicField);
        strikethroughField = new BooleanFieldEditor(first.getPrefName() + ".strikethrough", "Strikethrough", group);
        ((Button)strikethroughField.getDescriptionControl(group)).addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                sel.setStrikethrough(((Button)e.widget).getSelection());
            }
        });
        addField(strikethroughField);
        underlineField = new BooleanFieldEditor(first.getPrefName() + ".underline", "Underline", group);
        ((Button)underlineField.getDescriptionControl(group)).addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                sel.setUnderline(((Button)e.widget).getSelection());
            }
        });
        addField(underlineField);
        
        for (StatementsTypes type : StatementsTypes.values()) {
            input.add(new SyntaxModel(type, store).load());
        }
        listIgnoredObjs.setInput(input);
        listIgnoredObjs.setSelection(new StructuredSelection(first));
        
        return composite;
    }

    @Override
    protected void createFieldEditors() {
    }
    
    @Override
    public boolean performOk() {
        for (SyntaxModel element : input) {
            element.store();
        }
        return true;
    }
}
