package syntaxeditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JTextField;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

public class FindPanel extends JFrame implements ActionListener
{
    private RSyntaxTextArea textArea;
    private final JTextField searchField;
    private final JTextField replaceField;
    private final JCheckBox regexCB;
    private final JCheckBox matchCaseCB;
    private final JCheckBox wholeWordCB;

    public FindPanel(RSyntaxTextArea textArea, JTextField searchField, JTextField replaceField,
            JButton nextButton, JButton prevButton, JButton replaceButton, 
            JButton replaceAllButton, JCheckBox regexCB, JCheckBox matchCaseCB,
            JCheckBox wholeWordCB)
    {
        this.textArea = textArea;
        this.searchField = searchField;
        this.replaceField = replaceField;
        this.regexCB = regexCB;
        this.matchCaseCB = matchCaseCB;
        this.wholeWordCB = wholeWordCB;
        
        searchField.addActionListener((ActionEvent e) ->
        {
            nextButton.doClick(0);
        });

        nextButton.setActionCommand("FindNext");
        nextButton.addActionListener(this);
        prevButton.setActionCommand("FindPrev");
        prevButton.addActionListener(this);
        replaceButton.setActionCommand("ReplaceNext");
        replaceButton.addActionListener(this);
        replaceAllButton.setActionCommand("ReplaceAll");
        replaceAllButton.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String command = e.getActionCommand();
        boolean forward = "FindNext".equals(command) || ("ReplaceNext".equals(command));
        boolean replaceMode = ("ReplaceNext".equals(command)) || ("ReplaceAll".equals(command));

        SearchContext context = new SearchContext();
        String searchText = searchField.getText();
        String replaceText = replaceField.getText();

        if ((searchText.length() == 0) || ((replaceText.length() == 0) && (replaceMode)))
            return;

        context.setSearchFor(searchText);
        context.setMatchCase(matchCaseCB.isSelected());
        context.setRegularExpression(regexCB.isSelected());
        context.setWholeWord(wholeWordCB.isSelected());
        context.setSearchForward(forward);
        
        if (replaceMode)
        {
            context.setReplaceWith(replaceText);
            
            if ("ReplaceNext".equals(command))
            {
                if (SearchEngine.replace(textArea, context).getMatchRange() == null)
                {
                    textArea.setCaretPosition(0);
                    SearchEngine.replace(textArea, context);
                }
            }
            else
                SearchEngine.replaceAll(textArea, context);
        }
        else if (SearchEngine.find(textArea, context).getMatchRange() == null)
        {
            if (forward)
                textArea.setCaretPosition(0);
            else
                textArea.setCaretPosition(textArea.getText().length());

            SearchEngine.find(textArea, context);
        }
    }
    
    public void endSearch()
    {
        SearchContext context = new SearchContext();
        context.setSearchFor("");
        SearchEngine.find(textArea, context);
    }
    
    // setters, getters
    public void setTextArea(RSyntaxTextArea textArea)
    {
        this.textArea = textArea;
    }
    
    public RSyntaxTextArea getTextArea()
    {
        return this.textArea;
    }
}
