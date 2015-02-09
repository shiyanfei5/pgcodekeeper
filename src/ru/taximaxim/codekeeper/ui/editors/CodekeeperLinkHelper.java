package ru.taximaxim.codekeeper.ui.editors;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.navigator.ILinkHelper;

public class CodekeeperLinkHelper implements ILinkHelper{

    @Override
    public IStructuredSelection findSelection(IEditorInput anInput) {
        StructuredSelection sel = null;
        if (anInput instanceof ProjectEditorInput) {
            ProjectEditorInput in = (ProjectEditorInput)anInput;
            sel = new StructuredSelection(in.getProject());
        }
        return sel;
    }

    @Override
    public void activateEditor(IWorkbenchPage aPage,
            IStructuredSelection aSelection) {
        if (aSelection == null || aSelection.isEmpty()) {
            return;
        }
        Object element= aSelection.getFirstElement();
        if (element instanceof IProject) {
            IProject proj = (IProject)element;
            ProjectEditorInput in = new ProjectEditorInput(proj.getName());
            IEditorPart editor = aPage.findEditor(in);
            if (editor != null) {
                aPage.bringToTop(editor);
            }   
        }
    }

}
