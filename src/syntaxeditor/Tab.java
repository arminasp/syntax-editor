package syntaxeditor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

public class Tab
{
    private RSyntaxTextArea textArea = null;
    private String title = null;
    private String path = null;
    private String spelling = "";
    private boolean status = false;

    // constructors
    public Tab(RSyntaxTextArea textArea)
    {
        this.textArea = textArea;
    }

    // setters
    void setTitle(String title)
    {
        this.title = title;
    }

    void setPath(String path)
    {
        this.path = path;
    }
    
    public void setSyntax(String syntax)
    {
        if (!this.textArea.getSyntaxEditingStyle().equals(syntax))
            this.textArea.setSyntaxEditingStyle(syntax);
    }
    
    public void setText(String text)
    {
        this.textArea.setText(text);
    }
    
    public void setSpelling(String spelling)
    {
        this.spelling = spelling;
    }
    
    public void setStatus(boolean status) 
    {
        this.status = status;
    }
    
    // getters
    public RSyntaxTextArea getTextArea()
    {
        return this.textArea;
    }
    
    public String getText()
    {
        return this.textArea.getText();
    }
    
    String getTitle()
    {
        return this.title;
    }

    String getPath()
    {
        return this.path;
    }

    public String getSyntax()
    {
        return this.textArea.getSyntaxEditingStyle();
    }
    
    public String getSpelling()
    {
        return this.spelling;
    }
    
    public boolean getStatus()
    {
        return this.status;
    }
}