package ru.taximaxim.codekeeper.ui.sqledit;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISharedTextColors;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

import ru.taximaxim.codekeeper.ui.Activator;
import ru.taximaxim.codekeeper.ui.prefs.SQLEditorSytaxColoring;

public class SQLSourceViewerConfiguration extends TextSourceViewerConfiguration {

    private static final class WordDetector implements IWordDetector {
        public boolean isWordPart(char c) {
            return !Character.isWhitespace(c);
        }

        public boolean isWordStart(char c) {
            return !Character.isWhitespace(c);
        }
    }
    
    private final ISharedTextColors fSharedColors;
    private final SqlPostgresSyntax sqlSyntax = new SqlPostgresSyntax();
    private IPreferenceStore prefs;;
    
    public SQLSourceViewerConfiguration(ISharedTextColors sharedColors, IPreferenceStore store) {
        super(store);
        fSharedColors= sharedColors;
        this.prefs = Activator.getDefault().getPreferenceStore();
    }
    
    // Всплывающая подсказка пока
    @Override
    public ITextHover getTextHover(ISourceViewer sourceViewer,
            String contentType) {
        return new ITextHover() {
            
            @Override
            public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
                Point selection= textViewer.getSelectedRange();
                if (selection.x <= offset && offset < selection.x + selection.y)
                    return new Region(selection.x, selection.y);
                return new Region(offset, 0);
            }
            
            @Override
            public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
                if (hoverRegion != null) {
                    try {
                        if (hoverRegion.getLength() > -1)
                            return textViewer.getDocument().get(
                                    hoverRegion.getOffset(),
                                    hoverRegion.getLength());
                    } catch (BadLocationException x) {
                    }
                }
                return "JavaTextHover.emptySelection"; 
            }
        };
    }
    
    // Отображает всю строку при наведении на левую полосу редактора
    @Override
    public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
        return new IAnnotationHover() {
            
            @Override
            public String getHoverInfo(ISourceViewer sourceViewer, int lineNumber) {
                IDocument document= sourceViewer.getDocument();

                try {
                    IRegion info= document.getLineInformation(lineNumber);
                    return document.get(info.getOffset(), info.getLength());
                } catch (BadLocationException x) {
                }
                return null;
            }
        };
    }
    
    // Автозавершение, не работает при вводе не фильтрует по вводу
    @Override
    public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
        ContentAssistant assistant= new ContentAssistant();
        assistant.setContentAssistProcessor(new IContentAssistProcessor() {
            
            @Override
            public String getErrorMessage() {
                return "Error Message";
            }
            
            @Override
            public IContextInformationValidator getContextInformationValidator() {
                return null;
            }
            
            @Override
            public char[] getContextInformationAutoActivationCharacters() {
                return new char[] { '#' };
            }
            
            @Override
            public char[] getCompletionProposalAutoActivationCharacters() {
                return new char[] { '.', '(' };
            }
            
            @Override
            public IContextInformation[] computeContextInformation(ITextViewer viewer,
                    int offset) {
                IContextInformation[] result= new IContextInformation[5];
                for (int i= 0; i < result.length; i++)
                    result[i]= new ContextInformation("CompletionProcessor.ContextInfo.display.pattern " + i + " " + offset,
                        "CompletionProcessor.ContextInfo.value.pattern " + i + " " + (offset - 5) + " " +(offset + 5));
                return result;
            }
            
            @Override
            public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer,
                    int offset) {
                List<ICompletionProposal> result= new ArrayList<ICompletionProposal>();
                for (String fgProposal : sqlSyntax.getReservedwords()) {
                    IContextInformation info = new ContextInformation(fgProposal, fgProposal);
                    result.add(new CompletionProposal(fgProposal, offset, 0, fgProposal.length(), null, fgProposal, info, fgProposal));
                }
                return result.toArray(new ICompletionProposal[result.size()]);
            }
        }, IDocument.DEFAULT_CONTENT_TYPE);
        assistant.enableAutoActivation(true);
        assistant.setAutoActivationDelay(500);
        assistant.setProposalPopupOrientation(IContentAssistant.PROPOSAL_OVERLAY);
        assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
        return assistant;
    }

    @Override
    public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
        return SQLDocumentProvider.SQL_PARTITIONING;
    }
    
    @Override
    public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
        return new String[] {
                SQLDocumentProvider.SQL_CODE,
                SQLDocumentProvider.SQL_SINGLE_COMMENT
        };
    }
    
    @Override
    public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
        PresentationReconciler reconciler= new PresentationReconciler();
        reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        
        addDamagerRepairer(reconciler, createCommentScanner(), SQLDocumentProvider.SQL_SINGLE_COMMENT);
        addDamagerRepairer(reconciler, createMultiCommentScanner(), SQLDocumentProvider.SQL_MULTI_COMMENT);
        addDamagerRepairer(reconciler, createRecipeScanner(), SQLDocumentProvider.SQL_CODE);
        
        return reconciler;
    }

    private void addDamagerRepairer(PresentationReconciler reconciler, RuleBasedScanner commentScanner, String contentType) {
        DefaultDamagerRepairer commentDamagerRepairer= new DefaultDamagerRepairer(commentScanner);
        reconciler.setDamager(commentDamagerRepairer, contentType);
        reconciler.setRepairer(commentDamagerRepairer, contentType);
    }

    private RuleBasedScanner createRecipeScanner() {
        RuleBasedScanner recipeScanner= new RuleBasedScanner();
        
        IRule[] rules= {
                sqlSyntaxRules()
        };
        recipeScanner.setRules(rules);
        return recipeScanner;
    }

    private WordRule sqlSyntaxRules() {
        
        // Define a word rule and add SQL keywords to it.
        WordRule wordRule = new WordRule(new WordDetector(), Token.WHITESPACE);
        for (String reservedWord : sqlSyntax.getReservedwords()) {
            wordRule.addWord(reservedWord.toLowerCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.RESERVED_WORDS)));
            wordRule.addWord(reservedWord.toUpperCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.RESERVED_WORDS)));
        }
        // TODO render unreserved keywords in the same way with reserved
        // keywords, should let user decide via preference
        for (String unreservedWord : sqlSyntax.getUnreservedwords()) {
            wordRule.addWord(unreservedWord.toLowerCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.UN_RESERVED_WORDS)));
            wordRule.addWord(unreservedWord.toUpperCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.UN_RESERVED_WORDS)));
        }

        // Add the SQL datatype names to the word rule.
        for (String datatype : sqlSyntax.getTypes()) {
            wordRule.addWord(datatype.toLowerCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.TYPES)));
            wordRule.addWord(datatype.toUpperCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.TYPES)));
        }

        // Add the SQL function names to the word rule.
        for (String function : sqlSyntax.getFunctions()) {
            wordRule.addWord(function.toLowerCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.FUNCTIONS)));
            wordRule.addWord(function.toUpperCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.FUNCTIONS)));
        }

        // Add the SQL constants to the word rule.
        for (String constant : sqlSyntax.getConstants()) {
            wordRule.addWord(constant.toLowerCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.CONSTANTS)));
            wordRule.addWord(constant.toUpperCase(), new Token(
                    getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.CONSTANTS)));
        }

        return wordRule;
    }
    
    private TextAttribute getTextAttribute(IPreferenceStore prefs, SQLEditorSytaxColoring.StatementsTypes type) {
        SyntaxModel sm = new SyntaxModel(type, prefs).load();
        int style = 0 | (sm.isBold() ? SWT.BOLD : 0)
                | (sm.isItalic() ? SWT.ITALIC: 0)
                | (sm.isUnderline() ? SWT.UNDERLINE_SINGLE: 0)
                | (sm.isUnderline() ? TextAttribute.UNDERLINE: 0)
                | (sm.isStrikethrough() ? TextAttribute.STRIKETHROUGH: 0); 
        return new TextAttribute(fSharedColors.getColor(sm.getColor()), null, style);
    }

    private RuleBasedScanner createCommentScanner() {
        RuleBasedScanner commentScanner= new RuleBasedScanner();
        commentScanner.setDefaultReturnToken(new Token(getTextAttribute(prefs, SQLEditorSytaxColoring.StatementsTypes.SINGLE_LINE_COMMENTS)));
        return commentScanner;
    }
    
    private RuleBasedScanner createMultiCommentScanner() {
        Color blue= fSharedColors.getColor(new RGB(0, 0, 200));
        RuleBasedScanner commentScanner= new RuleBasedScanner();
        commentScanner.setDefaultReturnToken(new Token(new TextAttribute(blue, null, SWT.ITALIC)));
        return commentScanner;
    }
}
