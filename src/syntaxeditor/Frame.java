package syntaxeditor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.spell.SpellingParser;
import org.fife.ui.rtextarea.RTextScrollPane;
import static syntaxeditor.SyntaxEditor.DICTIONARY_DIR;
import static syntaxeditor.SyntaxEditor.DIRECTORY;
import static syntaxeditor.SyntaxEditor.THEME_DIR;

public class Frame extends JFrame
{
    private Tab currentTab;
    private Database db;
    private LinkedHashMap<RTextScrollPane, Tab> tabMap;
    private LinkedHashMap<String, String> themeMap;
    private FindPanel panel;
    private String currentTheme;
    private SpellingParser parser;
    private File lastSavePath;
    private File lastOpenPath;
    private boolean isDictionary = true;

    // performance testing
    long startTime;

    // constructor
    public Frame()
    {
        try
        {
            LinkedHashMap<String, Integer> history;
            ArrayList<File> deleteList = new ArrayList();
            tabMap = new LinkedHashMap();
            themeMap = new LinkedHashMap();
            db = new Database();

            db.open();
            initComponents();
            findPanel.setVisible(false);
            replacePanel.setVisible(false);

            File dic = new File(DICTIONARY_DIR);

            if (dic.exists())
            {
                parser = SpellingParser.createEnglishSpellingParser(dic, false);
            }
            else
            {
                englishSpellingMenuItem.setEnabled(false);
                isDictionary = false;
            }

            enableDragAndDrop(this);
            tabbedPane.setFocusable(false);

            for (String title : db.getThemeTitles())
            {
                themeMap.put(title, THEME_DIR + title + ".xml");
            }

            currentTheme = db.getSelectedTheme();
            updateThemeMenu();
            history = db.getHistory();
            createTab();

            if (!history.isEmpty())
            {
                File file;
                boolean firstFile = false;

                for (Map.Entry<String, Integer> entry : history.entrySet())
                {
                    file = new File(entry.getKey());

                    if (file.exists())
                    {
                        if (entry.getValue() == 1)
                        {
                            open(file);

                            if (!firstFile)
                            {
                                firstFile = true;
                            }
                        }
                        else
                        {
                            try
                            {
                                if (!firstFile)
                                {
                                    tabMap.clear();
                                    createTab();
                                    tabbedPane.remove(tabbedPane.getSelectedIndex() - 1);
                                    firstFile = true;
                                }
                                else
                                {
                                    createTab();
                                }

                                deleteList.add(file);
                                String content = new String(Files.readAllBytes(Paths.get(entry.getKey())), StandardCharsets.UTF_8);
                                fillText(content);
                            }
                            catch (IOException ex)
                            {
                                Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
            }

            SwingUtilities.invokeLater(()
                    -> 
                    {
                        panel = new FindPanel(currentTab.getTextArea(), findTextField,
                                replaceTextField, findNextButton, findPreviousButton,
                                replaceButton, replaceAllButton, regexCB, matchCaseCB,
                                wholeWordCB);

                        for (int i = 0; i < deleteList.size(); i++)
                        {
                            deleteList.get(i).delete();
                        }

                        db.clearHistory();
                        db.close();
            });
        }
        catch (IOException ex)
        {
            Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // tab methods
    private void createTab()
    {
        try
        {
            RSyntaxTextArea textArea = new RSyntaxTextArea();
            Tab tab = new Tab(textArea);
            InputStream in = getClass().getResourceAsStream(themeMap.get(currentTheme));
            Theme theme = Theme.load(in);

            textArea.setCodeFoldingEnabled(true);
            textArea.setMarkOccurrences(true);
            enableDragAndDrop(textArea);

            theme.apply(textArea);
            RTextScrollPane sp = new RTextScrollPane(textArea);

            tab.setPath(DIRECTORY + "history/" + Long.toString(System.currentTimeMillis()) + ".txt");
            tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
            tabMap.put(sp, tab);
            tabbedPane.addTab("Untitled", sp);
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
            textArea.requestFocusInWindow();
        }
        catch (IOException ex)
        {
            Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void initTab(Tab tab, String title, String path)
    {
        String[] tokens = title.split("\\.(?=[^\\.]+$)");
        String extension = tokens[tokens.length - 1];

        tab.setTitle(title);
        tab.setPath(path);
        tab.setStatus(true);

        switch (extension)
        {
            case "as":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT);
                break;
            case "asm":
            case "inc":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86);
                break;
            case "bat":
            case "cmd":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
                break;
            case "c":
            case "h":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_C);
                break;
            case "cs":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_CSHARP);
                break;
            case "cpp":
            case "cc":
            case "cxx":
            case "c++":
            case "hpp":
            case "hh":
            case "hxx":
            case "h++":
            case "inl":
            case "ipp":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
                break;
            case "css":
            case "csserb":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_CSS);
                break;
            case "dpr":
            case "lpr":
            case "pp":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_DELPHI);
                break;
            case "html":
            case "htm":
            case "shtml":
            case "xhtml":
            case "tpml":
            case "tpl":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_HTML);
                break;
            case "java":
            case "bsh":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_JAVA);
                break;
            case "js":
            case "htc":
            case "jsx":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
                break;
            case "json":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_JSON);
                break;
            case "tex":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_LATEX);
                break;
            case "lisp":
            case "cl":
            case "l":
            case "mud":
            case "el":
            case "scm":
            case "ss":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_LISP);
                break;
            case "lua":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_LUA);
                break;
            case "make":
            case "GNUmakefile":
            case "makefile":
            case "Makefile":
            case "OCamIMakefile":
            case "mak":
            case "mk":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_MAKEFILE);
                break;
            case "pl":
            case "pm":
            case "pod":
            case "t":
            case "PL":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_PERL);
                break;
            case "php":
            case "php3":
            case "php4":
            case "php5":
            case "php7":
            case "phpt":
            case "phtml":
            case "aw":
            case "ctp":
            case "install":
            case "module":
            case "profile":
            case "php_cs":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_PHP);
                break;
            case "py":
            case "rpy":
            case "pyw":
            case "cpy":
            case "SConstruct":
            case "Sconstruct":
            case "sconstruct":
            case "SConscript":
            case "gyp":
            case "gypi":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_PYTHON);
                break;
            case "rb":
            case "rbx":
            case "rjs":
            case "RakeFile":
            case "rake":
            case "cgi":
            case "fcgi":
            case "gemspec":
            case "irbrc":
            case "capfile":
            case "Gemfile":
            case "Vagrantfile":
            case "configru":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_RUBY);
                break;
            case "scala":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_SCALA);
                break;
            case "sql":
            case "ddl":
            case "dml":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_SQL);
                break;
            case "tcl":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_TCL);
                break;
            case "sh":
            case "bash":
            case "zsh":
            case ".bash_aliases":
            case ".bash_functions":
            case ".bash_login":
            case ".bash_logout":
            case ".bash_profile":
            case ".bash_variables":
            case ".bashrc":
            case ".profile":
            case ".textmate_init":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
                break;
            case "xml":
            case "xsd":
            case "tld":
            case "dtml":
            case "rss":
            case "opml":
            case "xslt":
            case "svg":
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_XML);
                break;
            default:
                tab.setSyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
                break;
        }
    }

    private void updateTab()
    {
        if (tabbedPane.getTabCount() == 0)
        {
            createTab();
        }

        currentTab = tabMap.get(tabbedPane.getSelectedComponent());
        updateSyntax(currentTab.getSyntax());
        updateSpellingMenu();

        if (panel != null)
        {
            panel.setTextArea(currentTab.getTextArea());

            if (!findPanel.isVisible())
            {
                panel.endSearch();
            }
        }

        if (currentTab.getTitle() != null)
        {
            this.setTitle(currentTab.getPath() + " - Syntax Editor");
        }
        else
        {
            this.setTitle("Untitled - Syntax Editor");
        }
    }

    // file handling
    public void open(File file)
    {
        String path = file.getPath();
        String text = currentTab.getText();
        Boolean isDuplicate = false;
        Boolean isEmpty = false;
        int emptyIndex = 0;
        int i = 0;

        if ((text.length() == 0) && (currentTab.getTitle() == null))
        {
            emptyIndex = tabbedPane.getSelectedIndex();
            isEmpty = true;
        }

        for (Map.Entry<RTextScrollPane, Tab> entry : tabMap.entrySet())
        {
            i++;

            if (entry.getValue().getPath().equals(path))
            {
                isDuplicate = true;
                tabbedPane.setSelectedIndex(i - 1);
                break;
            }
        }

        if (!isDuplicate)
        {
            try
            {
                String name = file.getName();

                createTab();
                initTab(currentTab, name, file.getPath());
                int index = tabbedPane.getTabCount() - 1;

                if (name.length() > 38)
                {
                    name = shortenText(name, 38);
                }

                tabbedPane.setTitleAt(index, name);
                tabbedPane.setSelectedIndex(index);

                String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
                fillText(content);

                if (isEmpty)
                {
                    RTextScrollPane sp = (RTextScrollPane) tabbedPane.
                            getComponentAt(emptyIndex);

                    tabMap.remove(sp);
                    tabbedPane.remove(emptyIndex);
                }

                setStatusLabel("File Opened '" + path + "'", 5000);
                updateSyntax(currentTab.getSyntax());
                updateTab();
            }
            catch (IOException ex)
            {
                Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void save(String text, String path)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "UTF-8"));
            writer.write(text);
            writer.close();
            setStatusLabel("File Saved '" + path + "'", 5000);
        }

        catch (FileNotFoundException ex)
        {
            Logger.getLogger(Frame.class
                    .getName()).log(Level.SEVERE, null, ex);

        }
        catch (IOException ex)
        {
            Logger.getLogger(Frame.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void openFile()
    {
        JFileChooser chooser = new JFileChooser();

        if (lastOpenPath != null)
        {
            chooser.setCurrentDirectory(lastOpenPath);
        }

        int chooserValue = chooser.showOpenDialog(this);

        if (chooserValue == JFileChooser.APPROVE_OPTION)
        {
            File file = chooser.getSelectedFile();
            lastOpenPath = file.getParentFile();

            if (file.exists())
            {
                open(file);
            }
        }
    }

    private void saveFile(int mode) // 0 - save, 1 - save as
    {
        String text = currentTab.getText();
        String oldPath = currentTab.getPath();

        if (currentTab.getTitle() == null)
        {
            JFileChooser chooser = new JFileChooser();
            String title = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());

            if (!title.equals("Untitled"))
            {
                chooser.setSelectedFile(new File(title));
            }
            else
            {
                chooser.setSelectedFile(new File("Untitled."
                        + getSuggestedExtension(currentTab.getSyntax())));
            }

            if (lastSavePath != null)
            {
                chooser.setCurrentDirectory(lastSavePath);
            }

            int chooserValue = chooser.showSaveDialog(this);

            if (chooserValue == JFileChooser.APPROVE_OPTION)
            {
                File file = chooser.getSelectedFile();
                String name = file.getName();
                String path = file.getPath();

                lastSavePath = file.getParentFile();
                initTab(currentTab, name, path);
                save(text, path);

                if (name.length() > 38)
                {
                    name = shortenText(name, 38);
                }

                tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), name);

                if (mode != 1)
                {
                    deleteTemp(oldPath);
                }

                updateSyntax(currentTab.getSyntax());
                updateTab();
            }
        }
        else
        {
            save(text, currentTab.getPath());
        }
    }

    private void closeFile()
    {
        RTextScrollPane sp = (RTextScrollPane) tabbedPane.getSelectedComponent();
        String path = currentTab.getPath();

        if (currentTab.getTitle() != null)
        {
            setStatusLabel("File Closed '" + path + "'", 5000);
        }
        else
        {
            deleteTemp(path);
        }

        tabMap.remove(sp);
        tabbedPane.remove(tabbedPane.getSelectedIndex());
    }

    private String getSuggestedExtension(String syntax)
    {
        switch (syntax)
        {
            case SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT:
                return "as";
            case SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86:
                return "asm";
            case SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH:
                return "bat";
            case SyntaxConstants.SYNTAX_STYLE_C:
                return "c";
            case SyntaxConstants.SYNTAX_STYLE_CSHARP:
                return "cs";
            case SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS:
                return "cpp";
            case SyntaxConstants.SYNTAX_STYLE_CSS:
                return "css";
            case SyntaxConstants.SYNTAX_STYLE_DELPHI:
                return "dpr";
            case SyntaxConstants.SYNTAX_STYLE_HTML:
                return "html";
            case SyntaxConstants.SYNTAX_STYLE_JAVA:
                return "java";
            case SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT:
                return "js";
            case SyntaxConstants.SYNTAX_STYLE_JSON:
                return "json";
            case SyntaxConstants.SYNTAX_STYLE_LATEX:
                return "tex";
            case SyntaxConstants.SYNTAX_STYLE_LISP:
                return "lisp";
            case SyntaxConstants.SYNTAX_STYLE_LUA:
                return "lua";
            case SyntaxConstants.SYNTAX_STYLE_MAKEFILE:
                return "make";
            case SyntaxConstants.SYNTAX_STYLE_PERL:
                return "pl";
            case SyntaxConstants.SYNTAX_STYLE_PHP:
                return "php";
            case SyntaxConstants.SYNTAX_STYLE_PYTHON:
                return "py";
            case SyntaxConstants.SYNTAX_STYLE_RUBY:
                return "rb";
            case SyntaxConstants.SYNTAX_STYLE_SCALA:
                return "scala";
            case SyntaxConstants.SYNTAX_STYLE_SQL:
                return "sql";
            case SyntaxConstants.SYNTAX_STYLE_TCL:
                return "tcl";
            case SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL:
                return "sh";
            case SyntaxConstants.SYNTAX_STYLE_XML:
                return "xml";
            default:
                return "txt";
        }
    }

    private void fillText(String buffer)
    {
        currentTab.setText(buffer);
        currentTab.getTextArea().setCaretPosition(0);
    }

    private void deleteTemp(String path)
    {
        File file = new File(path);

        if (file.exists())
        {
            file.delete();
        }
    }

    public void openFolder(final File folder)
    {
        for (final File fileEntry : folder.listFiles())
        {
            if (fileEntry.isDirectory())
            {
                openFolder(fileEntry);
            }
            else
            {
                open(fileEntry);
            }
        }
    }

    // syntax
    private void updateSyntax(String syntax)
    {
        JRadioButtonMenuItem syntaxButton;
        RSyntaxTextArea textArea = currentTab.getTextArea();

        currentTab.setSyntax(syntax);

        if (syntax.equals(SyntaxConstants.SYNTAX_STYLE_NONE))
        {
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
        }
        else
        {
            textArea.setLineWrap(false);
            textArea.setWrapStyleWord(false);
        }

        switch (syntax)
        {
            case SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT:
                syntaxLabel.setText("ActionScript");
                syntaxButton = actionscriptMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86:
                syntaxLabel.setText("Assembly x86");
                syntaxButton = assemblyMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH:
                syntaxLabel.setText("Batch File");
                syntaxButton = batchfileMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_C:
                syntaxLabel.setText("C");
                syntaxButton = cMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_CSHARP:
                syntaxLabel.setText("C#");
                syntaxButton = csharpMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS:
                syntaxLabel.setText("C++");
                syntaxButton = cppMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_CSS:
                syntaxLabel.setText("CSS");
                syntaxButton = cssMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_DELPHI:
                syntaxLabel.setText("Delphi");
                syntaxButton = delphiMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_HTML:
                syntaxLabel.setText("HTML");
                syntaxButton = htmlMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_JAVA:
                syntaxLabel.setText("Java");
                syntaxButton = javaMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT:
                syntaxLabel.setText("JavaScript");
                syntaxButton = javascriptMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_JSON:
                syntaxLabel.setText("JSON");
                syntaxButton = jsonMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_LATEX:
                syntaxLabel.setText("LaTex");
                syntaxButton = latexMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_LISP:
                syntaxLabel.setText("Lisp");
                syntaxButton = lispMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_LUA:
                syntaxLabel.setText("Lua");
                syntaxButton = luaMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_MAKEFILE:
                syntaxLabel.setText("Makefile");
                syntaxButton = makefileMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_PERL:
                syntaxLabel.setText("Perl");
                syntaxButton = perlMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_PHP:
                syntaxLabel.setText("PHP");
                syntaxButton = phpMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_PYTHON:
                syntaxLabel.setText("Python");
                syntaxButton = pythonMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_RUBY:
                syntaxLabel.setText("Ruby");
                syntaxButton = rubyMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_SCALA:
                syntaxLabel.setText("Scala");
                syntaxButton = scalaMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_SQL:
                syntaxLabel.setText("SQL");
                syntaxButton = sqlMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_TCL:
                syntaxLabel.setText("TCL");
                syntaxButton = tclButton;
                break;
            case SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL:
                syntaxLabel.setText("Unix Shell");
                syntaxButton = unixshellMenuItem;
                break;
            case SyntaxConstants.SYNTAX_STYLE_XML:
                syntaxLabel.setText("XML");
                syntaxButton = xmlMenuItem;
                break;
            default:
                syntaxLabel.setText("Plain Text");
                syntaxButton = plaintextMenuItem;
                break;
        }

        actionscriptMenuItem.setSelected(false);
        assemblyMenuItem.setSelected(false);
        batchfileMenuItem.setSelected(false);
        cMenuItem.setSelected(false);
        csharpMenuItem.setSelected(false);
        cppMenuItem.setSelected(false);
        cssMenuItem.setSelected(false);
        delphiMenuItem.setSelected(false);
        htmlMenuItem.setSelected(false);
        javaMenuItem.setSelected(false);
        javascriptMenuItem.setSelected(false);
        jsonMenuItem.setSelected(false);
        latexMenuItem.setSelected(false);
        lispMenuItem.setSelected(false);
        luaMenuItem.setSelected(false);
        makefileMenuItem.setSelected(false);
        plaintextMenuItem.setSelected(false);
        perlMenuItem.setSelected(false);
        phpMenuItem.setSelected(false);
        pythonMenuItem.setSelected(false);
        rubyMenuItem.setSelected(false);
        scalaMenuItem.setSelected(false);
        sqlMenuItem.setSelected(false);
        tclButton.setSelected(false);
        unixshellMenuItem.setSelected(false);
        xmlMenuItem.setSelected(false);

        syntaxButton.setSelected(true);

        if (isDictionary)
        {
            if (syntaxButton != plaintextMenuItem)
            {
                if (!currentTab.getSpelling().equals(""))
                {
                    currentTab.getTextArea().removeParser(parser);
                    currentTab.setSpelling("");
                    updateSpellingMenu();
                }
                englishSpellingMenuItem.setEnabled(false);
            }
            else
            {
                englishSpellingMenuItem.setEnabled(true);
            }
        }
    }

    // appearance
    private void changeTheme(String title)
    {
        String path = themeMap.get(title);
        int selectedTab = tabbedPane.getSelectedIndex();

        tabMap.entrySet().stream().forEach((entry)
                -> 
                {
                    try
                    {
                        Tab tab = entry.getValue();
                        InputStream in = getClass().getResourceAsStream(path);
                        Theme theme = Theme.load(in);

                        theme.apply(tab.getTextArea());
                        in.close();

                    }
                    catch (FileNotFoundException ex)
                    {
                        Logger.getLogger(Frame.class
                                .getName()).log(Level.SEVERE, null, ex);

                    }
                    catch (IOException ex)
                    {
                        Logger.getLogger(Frame.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
        });

        tabbedPane.setSelectedIndex(selectedTab);
        currentTheme = title;
        updateThemeMenu();
    }

    private void updateThemeMenu()
    {
        JRadioButtonMenuItem themeButton;

        switch (currentTheme)
        {
            case "dark":
                themeButton = themeDarkMenuItem;
                break;

            default:
                themeButton = themeLightMenuItem;
                break;
        }

        themeLightMenuItem.setSelected(false);
        themeDarkMenuItem.setSelected(false);
        themeButton.setSelected(true);
    }

    private void setStatusLabel(String message, int wait)
    {
        if (message.length() > 55)
        {
            message = shortenText(message, 55) + '\'';
        }

        String msg = message;

        Timer timer = new Timer(wait, (ActionEvent ae)
                -> 
                {
                    if (statusLabel.getText().equals(msg))
                    {
                        statusLabel.setText(" ");
                    }
        });

        statusLabel.setText(msg);
        timer.start();
    }

    // drag & drop
    private void enableDragAndDrop(Component component)
    {
        DropTarget target = new DropTarget(component, new DropTargetListener()
        {
            @Override
            public void dragEnter(DropTargetDragEvent e)
            {
            }

            @Override
            public void dragExit(DropTargetEvent e)
            {
            }

            @Override
            public void dragOver(DropTargetDragEvent e)
            {
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent e)
            {
            }

            @Override
            public void drop(DropTargetDropEvent e)
            {
                try
                {
                    e.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    List list = (List) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    File file;

                    for (int i = 0; i < list.size(); i++)
                    {
                        file = (File) list.get(i);

                        if (file.isDirectory())
                        {
                            openFolder(file);
                        }
                        else
                        {
                            open(file);
                        }
                    }
                }
                catch (UnsupportedFlavorException | IOException | HeadlessException ex)
                {
                }
            }
        });
    }

    // spelling
    private void updateSpellingMenu()
    {
        switch (currentTab.getSpelling())
        {
            case "english":
                englishSpellingMenuItem.setSelected(true);
                break;
            default:
                englishSpellingMenuItem.setSelected(false);
                break;
        }
    }

    private String shortenText(String text, int length)
    {
        return text.substring(0, length - 1) + "...";
    }

    // performance tests
    private void timerStart()
    {
        startTime = System.currentTimeMillis();
    }

    private void timerStop()
    {
        System.out.println(System.currentTimeMillis() - startTime);
    }

    // exit
    public void exit()
    {
        this.setVisible(false);
        db.open();

        tabMap.entrySet().stream().forEach((entry)
                -> 
                {
                    String text = entry.getValue().getText();

                    if (entry.getValue().getTitle() != null)
                    {
                        db.insertFile(entry.getValue().getPath(), 1);
                    }
                    else if (text.length() > 0)
                    {
                        save(text, entry.getValue().getPath());
                        db.insertFile(entry.getValue().getPath(), 0);
                    }
        });

        db.setTheme(currentTheme);
        db.close();
        System.exit(0);
    }

    // Generated methods
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        jMenu1 = new javax.swing.JMenu();
        jMenu2 = new javax.swing.JMenu();
        jPopupMenu1 = new javax.swing.JPopupMenu();
        jPopupMenu2 = new javax.swing.JPopupMenu();
        jPopupMenu3 = new javax.swing.JPopupMenu();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextArea2 = new javax.swing.JTextArea();
        jRadioButtonMenuItem1 = new javax.swing.JRadioButtonMenuItem();
        jPopupMenu4 = new javax.swing.JPopupMenu();
        jPopupMenu5 = new javax.swing.JPopupMenu();
        jTextField1 = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jMenuItem1 = new javax.swing.JMenuItem();
        jPopupMenu6 = new javax.swing.JPopupMenu();
        jPopupMenu7 = new javax.swing.JPopupMenu();
        jMenu7 = new javax.swing.JMenu();
        jMenu3 = new javax.swing.JMenu();
        tabbedPane = new javax.swing.JTabbedPane();
        jPanel2 = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();
        syntaxLabel = new javax.swing.JLabel();
        findPanel = new javax.swing.JPanel();
        statusLabel1 = new javax.swing.JLabel();
        findTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        findNextButton = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        findPreviousButton = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        regexCB = new javax.swing.JCheckBox();
        jLabel5 = new javax.swing.JLabel();
        matchCaseCB = new javax.swing.JCheckBox();
        jLabel7 = new javax.swing.JLabel();
        wholeWordCB = new javax.swing.JCheckBox();
        jLabel6 = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        findPanelCloseButton = new javax.swing.JButton();
        replacePanel = new javax.swing.JPanel();
        statusLabel2 = new javax.swing.JLabel();
        replaceTextField = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        replaceButton = new javax.swing.JButton();
        jLabel9 = new javax.swing.JLabel();
        replaceAllButton = new javax.swing.JButton();
        jLabel10 = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        newFileMenuItem = new javax.swing.JMenuItem();
        openFileMenuItem = new javax.swing.JMenuItem();
        saveFileMenuItem = new javax.swing.JMenuItem();
        saveAsMenuItem = new javax.swing.JMenuItem();
        saveAllMenuItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        closeFileMenuItem = new javax.swing.JMenuItem();
        closeAllMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        findMenu = new javax.swing.JMenu();
        findMenuItem = new javax.swing.JMenuItem();
        findNextMenuItem = new javax.swing.JMenuItem();
        findPreviousMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        replaceMenuItem = new javax.swing.JMenuItem();
        replaceNextMenuItem = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        unmarkMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        syntaxMenu = new javax.swing.JMenu();
        actionscriptMenuItem = new javax.swing.JRadioButtonMenuItem();
        assemblyMenuItem = new javax.swing.JRadioButtonMenuItem();
        batchfileMenuItem = new javax.swing.JRadioButtonMenuItem();
        cMenuItem = new javax.swing.JRadioButtonMenuItem();
        csharpMenuItem = new javax.swing.JRadioButtonMenuItem();
        cppMenuItem = new javax.swing.JRadioButtonMenuItem();
        cssMenuItem = new javax.swing.JRadioButtonMenuItem();
        delphiMenuItem = new javax.swing.JRadioButtonMenuItem();
        htmlMenuItem = new javax.swing.JRadioButtonMenuItem();
        javaMenuItem = new javax.swing.JRadioButtonMenuItem();
        javascriptMenuItem = new javax.swing.JRadioButtonMenuItem();
        jsonMenuItem = new javax.swing.JRadioButtonMenuItem();
        latexMenuItem = new javax.swing.JRadioButtonMenuItem();
        lispMenuItem = new javax.swing.JRadioButtonMenuItem();
        luaMenuItem = new javax.swing.JRadioButtonMenuItem();
        makefileMenuItem = new javax.swing.JRadioButtonMenuItem();
        plaintextMenuItem = new javax.swing.JRadioButtonMenuItem();
        perlMenuItem = new javax.swing.JRadioButtonMenuItem();
        phpMenuItem = new javax.swing.JRadioButtonMenuItem();
        pythonMenuItem = new javax.swing.JRadioButtonMenuItem();
        rubyMenuItem = new javax.swing.JRadioButtonMenuItem();
        scalaMenuItem = new javax.swing.JRadioButtonMenuItem();
        tclButton = new javax.swing.JRadioButtonMenuItem();
        sqlMenuItem = new javax.swing.JRadioButtonMenuItem();
        unixshellMenuItem = new javax.swing.JRadioButtonMenuItem();
        xmlMenuItem = new javax.swing.JRadioButtonMenuItem();
        spellingMenu = new javax.swing.JMenu();
        englishSpellingMenuItem = new javax.swing.JRadioButtonMenuItem();
        themeMenu = new javax.swing.JMenu();
        themeLightMenuItem = new javax.swing.JRadioButtonMenuItem();
        themeDarkMenuItem = new javax.swing.JRadioButtonMenuItem();

        jMenu1.setText("jMenu1");

        jMenu2.setText("jMenu2");

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        jLabel1.setText("jLabel1");

        jTextArea2.setColumns(20);
        jTextArea2.setRows(5);
        jScrollPane2.setViewportView(jTextArea2);

        jRadioButtonMenuItem1.setSelected(true);
        jRadioButtonMenuItem1.setText("jRadioButtonMenuItem1");

        jTextField1.setText("jTextField1");

        jMenuItem1.setText("jMenuItem1");

        jMenu7.setText("jMenu7");

        jMenu3.setText("jMenu3");

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(500, 50));

        tabbedPane.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                tabbedPaneStateChanged(evt);
            }
        });

        jPanel2.setMaximumSize(new java.awt.Dimension(32767, 22));
        jPanel2.setMinimumSize(new java.awt.Dimension(100, 22));
        jPanel2.setPreferredSize(new java.awt.Dimension(580, 22));

        statusLabel.setFont(new java.awt.Font("Dialog", 0, 10)); // NOI18N
        statusLabel.setText(" ");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusLabel)
                .addContainerGap(657, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(statusLabel)
                .addGap(0, 8, Short.MAX_VALUE))
        );

        syntaxLabel.setFont(new java.awt.Font("Dialog", 0, 10)); // NOI18N
        syntaxLabel.setText(" ");

        findPanel.setMaximumSize(new java.awt.Dimension(32767, 22));
        findPanel.setMinimumSize(new java.awt.Dimension(100, 22));
        findPanel.setPreferredSize(new java.awt.Dimension(580, 22));
        findPanel.setLayout(new javax.swing.BoxLayout(findPanel, javax.swing.BoxLayout.LINE_AXIS));

        statusLabel1.setFont(new java.awt.Font("Dialog", 0, 10)); // NOI18N
        statusLabel1.setText(" ");
        findPanel.add(statusLabel1);
        findPanel.add(findTextField);

        jLabel2.setText("   ");
        findPanel.add(jLabel2);

        findNextButton.setText("Find Next");
        findPanel.add(findNextButton);

        jLabel3.setText("   ");
        findPanel.add(jLabel3);

        findPreviousButton.setText("Find Prev");
        findPanel.add(findPreviousButton);

        jLabel4.setText("   ");
        findPanel.add(jLabel4);

        regexCB.setText("Regex");
        findPanel.add(regexCB);

        jLabel5.setText("   ");
        findPanel.add(jLabel5);

        matchCaseCB.setText("Match Case");
        findPanel.add(matchCaseCB);

        jLabel7.setText("   ");
        findPanel.add(jLabel7);

        wholeWordCB.setText("Whole Word");
        findPanel.add(wholeWordCB);

        jLabel6.setText("      ");
        findPanel.add(jLabel6);

        jPanel4.setMaximumSize(new java.awt.Dimension(10, 31));
        jPanel4.setMinimumSize(new java.awt.Dimension(10, 31));
        jPanel4.setPreferredSize(new java.awt.Dimension(10, 31));
        jPanel4.setRequestFocusEnabled(false);

        findPanelCloseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/syntaxeditor/resources/icons/closeIcon.png"))); // NOI18N
        findPanelCloseButton.setMaximumSize(new java.awt.Dimension(10, 10));
        findPanelCloseButton.setMinimumSize(new java.awt.Dimension(10, 10));
        findPanelCloseButton.setPreferredSize(new java.awt.Dimension(10, 10));
        findPanelCloseButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                findPanelCloseButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(findPanelCloseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(findPanelCloseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 21, Short.MAX_VALUE))
        );

        findPanel.add(jPanel4);

        replacePanel.setMaximumSize(new java.awt.Dimension(32767, 22));
        replacePanel.setMinimumSize(new java.awt.Dimension(100, 22));
        replacePanel.setPreferredSize(new java.awt.Dimension(580, 22));
        replacePanel.setLayout(new javax.swing.BoxLayout(replacePanel, javax.swing.BoxLayout.LINE_AXIS));

        statusLabel2.setFont(new java.awt.Font("Dialog", 0, 10)); // NOI18N
        statusLabel2.setText(" ");
        replacePanel.add(statusLabel2);
        replacePanel.add(replaceTextField);

        jLabel8.setText("   ");
        replacePanel.add(jLabel8);

        replaceButton.setText("Replace");
        replacePanel.add(replaceButton);

        jLabel9.setText("   ");
        replacePanel.add(jLabel9);

        replaceAllButton.setText("Replace All");
        replacePanel.add(replaceAllButton);

        jLabel10.setText("                                                                                             ");
        replacePanel.add(jLabel10);

        fileMenu.setText("File");

        newFileMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        newFileMenuItem.setText("New File");
        newFileMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                newFileMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(newFileMenuItem);

        openFileMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        openFileMenuItem.setText("Open File");
        openFileMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                openFileMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openFileMenuItem);

        saveFileMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        saveFileMenuItem.setText("Save");
        saveFileMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveFileMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveFileMenuItem);

        saveAsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        saveAsMenuItem.setText("Save As...");
        saveAsMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveAsMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveAsMenuItem);

        saveAllMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        saveAllMenuItem.setText("Save All");
        saveAllMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveAllMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveAllMenuItem);
        fileMenu.add(jSeparator1);

        closeFileMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_MASK));
        closeFileMenuItem.setText("Close File");
        closeFileMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                closeFileMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(closeFileMenuItem);

        closeAllMenuItem.setText("Close All");
        closeAllMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                closeAllMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(closeAllMenuItem);

        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        jMenuBar1.add(fileMenu);

        editMenu.setText("Edit");

        undoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_MASK));
        undoMenuItem.setText("Undo");
        undoMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                undoMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(undoMenuItem);

        redoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_MASK));
        redoMenuItem.setText("Redo");
        redoMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                redoMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(redoMenuItem);

        cutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_MASK));
        cutMenuItem.setText("Cut");
        cutMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cutMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(cutMenuItem);

        copyMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_MASK));
        copyMenuItem.setText("Copy");
        copyMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                copyMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(copyMenuItem);

        pasteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_MASK));
        pasteMenuItem.setText("Paste");
        pasteMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                pasteMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(pasteMenuItem);

        selectAllMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_MASK));
        selectAllMenuItem.setText("Select All");
        selectAllMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                selectAllMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(selectAllMenuItem);

        jMenuBar1.add(editMenu);

        findMenu.setText("Find");

        findMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_MASK));
        findMenuItem.setText("Find...");
        findMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                findMenuItemActionPerformed(evt);
            }
        });
        findMenu.add(findMenuItem);

        findNextMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F3, 0));
        findNextMenuItem.setText("Find Next");
        findNextMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                findNextMenuItemActionPerformed(evt);
            }
        });
        findMenu.add(findNextMenuItem);

        findPreviousMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F3, java.awt.event.InputEvent.SHIFT_MASK));
        findPreviousMenuItem.setText("Find Previous");
        findPreviousMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                findPreviousMenuItemActionPerformed(evt);
            }
        });
        findMenu.add(findPreviousMenuItem);
        findMenu.add(jSeparator2);

        replaceMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, java.awt.event.InputEvent.CTRL_MASK));
        replaceMenuItem.setText("Replace...");
        replaceMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                replaceMenuItemActionPerformed(evt);
            }
        });
        findMenu.add(replaceMenuItem);

        replaceNextMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        replaceNextMenuItem.setText("Replace Next");
        replaceNextMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                replaceNextMenuItemActionPerformed(evt);
            }
        });
        findMenu.add(replaceNextMenuItem);
        findMenu.add(jSeparator3);

        unmarkMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0));
        unmarkMenuItem.setText("Unmark");
        unmarkMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                unmarkMenuItemActionPerformed(evt);
            }
        });
        findMenu.add(unmarkMenuItem);

        jMenuBar1.add(findMenu);

        viewMenu.setText("View");

        syntaxMenu.setText("Syntax");

        actionscriptMenuItem.setText("ActionScript");
        actionscriptMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                actionscriptMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(actionscriptMenuItem);

        assemblyMenuItem.setText("Assembly x86");
        assemblyMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                assemblyMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(assemblyMenuItem);

        batchfileMenuItem.setText("Batch File");
        batchfileMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                batchfileMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(batchfileMenuItem);

        cMenuItem.setText("C");
        cMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(cMenuItem);

        csharpMenuItem.setText("C#");
        csharpMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                csharpMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(csharpMenuItem);

        cppMenuItem.setText("C++");
        cppMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cppMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(cppMenuItem);

        cssMenuItem.setText("CSS");
        cssMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cssMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(cssMenuItem);

        delphiMenuItem.setText("Delphi");
        delphiMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                delphiMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(delphiMenuItem);

        htmlMenuItem.setText("HTML");
        htmlMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                htmlMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(htmlMenuItem);

        javaMenuItem.setText("Java");
        javaMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                javaMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(javaMenuItem);

        javascriptMenuItem.setText("JavaScript");
        javascriptMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                javascriptMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(javascriptMenuItem);

        jsonMenuItem.setText("JSON");
        jsonMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                jsonMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(jsonMenuItem);

        latexMenuItem.setText("LaTex");
        latexMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                latexMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(latexMenuItem);

        lispMenuItem.setText("Lisp");
        lispMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                lispMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(lispMenuItem);

        luaMenuItem.setText("Lua");
        luaMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                luaMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(luaMenuItem);

        makefileMenuItem.setText("Makefile");
        makefileMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                makefileMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(makefileMenuItem);

        plaintextMenuItem.setText("Plain Text");
        plaintextMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                plaintextMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(plaintextMenuItem);

        perlMenuItem.setText("Perl");
        perlMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                perlMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(perlMenuItem);

        phpMenuItem.setText("PHP");
        phpMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                phpMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(phpMenuItem);

        pythonMenuItem.setText("Python");
        pythonMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                pythonMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(pythonMenuItem);

        rubyMenuItem.setText("Ruby");
        rubyMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                rubyMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(rubyMenuItem);

        scalaMenuItem.setText("Scala");
        scalaMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                scalaMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(scalaMenuItem);

        tclButton.setText("TCL");
        tclButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                tclButtonActionPerformed(evt);
            }
        });
        syntaxMenu.add(tclButton);

        sqlMenuItem.setText("SQL");
        sqlMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                sqlMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(sqlMenuItem);

        unixshellMenuItem.setText("Unix Shell");
        unixshellMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                unixshellMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(unixshellMenuItem);

        xmlMenuItem.setText("XML");
        xmlMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                xmlMenuItemActionPerformed(evt);
            }
        });
        syntaxMenu.add(xmlMenuItem);

        viewMenu.add(syntaxMenu);

        spellingMenu.setText("Spelling");

        englishSpellingMenuItem.setText("English");
        englishSpellingMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                englishSpellingMenuItemActionPerformed(evt);
            }
        });
        spellingMenu.add(englishSpellingMenuItem);

        viewMenu.add(spellingMenu);

        themeMenu.setText("Theme");

        themeLightMenuItem.setText("Light");
        themeLightMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                themeLightMenuItemActionPerformed(evt);
            }
        });
        themeMenu.add(themeLightMenuItem);

        themeDarkMenuItem.setText("Dark");
        themeDarkMenuItem.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                themeDarkMenuItemActionPerformed(evt);
            }
        });
        themeMenu.add(themeDarkMenuItem);

        viewMenu.add(themeMenu);

        jMenuBar1.add(viewMenu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabbedPane)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, 666, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(syntaxLabel))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(replacePanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(findPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 342, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(findPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(replacePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(syntaxLabel)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // buttons
    private void newFileMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_newFileMenuItemActionPerformed
    {//GEN-HEADEREND:event_newFileMenuItemActionPerformed
        createTab();
    }//GEN-LAST:event_newFileMenuItemActionPerformed

    private void openFileMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_openFileMenuItemActionPerformed
    {//GEN-HEADEREND:event_openFileMenuItemActionPerformed
        openFile();
    }//GEN-LAST:event_openFileMenuItemActionPerformed

    private void saveFileMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveFileMenuItemActionPerformed
    {//GEN-HEADEREND:event_saveFileMenuItemActionPerformed
        saveFile(0);
    }//GEN-LAST:event_saveFileMenuItemActionPerformed

    private void closeFileMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_closeFileMenuItemActionPerformed
    {//GEN-HEADEREND:event_closeFileMenuItemActionPerformed
        if (!((currentTab.getTitle() == null)
                && (currentTab.getText().length() == 0)
                && (tabbedPane.getTabCount() == 1)))
        {
            closeFile();
        }
    }//GEN-LAST:event_closeFileMenuItemActionPerformed

    private void javaMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_javaMenuItemActionPerformed
    {//GEN-HEADEREND:event_javaMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_JAVA);
    }//GEN-LAST:event_javaMenuItemActionPerformed

    private void cppMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cppMenuItemActionPerformed
    {//GEN-HEADEREND:event_cppMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
    }//GEN-LAST:event_cppMenuItemActionPerformed

    private void plaintextMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_plaintextMenuItemActionPerformed
    {//GEN-HEADEREND:event_plaintextMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
    }//GEN-LAST:event_plaintextMenuItemActionPerformed

    private void themeLightMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_themeLightMenuItemActionPerformed
    {//GEN-HEADEREND:event_themeLightMenuItemActionPerformed
        changeTheme("light");
    }//GEN-LAST:event_themeLightMenuItemActionPerformed

    private void themeDarkMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_themeDarkMenuItemActionPerformed
    {//GEN-HEADEREND:event_themeDarkMenuItemActionPerformed
        changeTheme("dark");
    }//GEN-LAST:event_themeDarkMenuItemActionPerformed

    private void htmlMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_htmlMenuItemActionPerformed
    {//GEN-HEADEREND:event_htmlMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_HTML);
    }//GEN-LAST:event_htmlMenuItemActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_exitMenuItemActionPerformed
    {//GEN-HEADEREND:event_exitMenuItemActionPerformed
        exit();
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void latexMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_latexMenuItemActionPerformed
    {//GEN-HEADEREND:event_latexMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_LATEX);
    }//GEN-LAST:event_latexMenuItemActionPerformed

    private void phpMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_phpMenuItemActionPerformed
    {//GEN-HEADEREND:event_phpMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_PHP);
    }//GEN-LAST:event_phpMenuItemActionPerformed

    private void rubyMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_rubyMenuItemActionPerformed
    {//GEN-HEADEREND:event_rubyMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_RUBY);
    }//GEN-LAST:event_rubyMenuItemActionPerformed

    private void csharpMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_csharpMenuItemActionPerformed
    {//GEN-HEADEREND:event_csharpMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_CSHARP);
    }//GEN-LAST:event_csharpMenuItemActionPerformed

    private void scalaMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_scalaMenuItemActionPerformed
    {//GEN-HEADEREND:event_scalaMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_SCALA);
    }//GEN-LAST:event_scalaMenuItemActionPerformed

    private void xmlMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_xmlMenuItemActionPerformed
    {//GEN-HEADEREND:event_xmlMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_XML);
    }//GEN-LAST:event_xmlMenuItemActionPerformed

    private void actionscriptMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_actionscriptMenuItemActionPerformed
    {//GEN-HEADEREND:event_actionscriptMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT);
    }//GEN-LAST:event_actionscriptMenuItemActionPerformed

    private void assemblyMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_assemblyMenuItemActionPerformed
    {//GEN-HEADEREND:event_assemblyMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86);
    }//GEN-LAST:event_assemblyMenuItemActionPerformed

    private void batchfileMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_batchfileMenuItemActionPerformed
    {//GEN-HEADEREND:event_batchfileMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
    }//GEN-LAST:event_batchfileMenuItemActionPerformed

    private void cMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cMenuItemActionPerformed
    {//GEN-HEADEREND:event_cMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_C);
    }//GEN-LAST:event_cMenuItemActionPerformed

    private void cssMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cssMenuItemActionPerformed
    {//GEN-HEADEREND:event_cssMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_CSS);
    }//GEN-LAST:event_cssMenuItemActionPerformed

    private void delphiMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_delphiMenuItemActionPerformed
    {//GEN-HEADEREND:event_delphiMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_DELPHI);
    }//GEN-LAST:event_delphiMenuItemActionPerformed

    private void javascriptMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_javascriptMenuItemActionPerformed
    {//GEN-HEADEREND:event_javascriptMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
    }//GEN-LAST:event_javascriptMenuItemActionPerformed

    private void jsonMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_jsonMenuItemActionPerformed
    {//GEN-HEADEREND:event_jsonMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_JSON);
    }//GEN-LAST:event_jsonMenuItemActionPerformed

    private void lispMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_lispMenuItemActionPerformed
    {//GEN-HEADEREND:event_lispMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_LISP);
    }//GEN-LAST:event_lispMenuItemActionPerformed

    private void luaMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_luaMenuItemActionPerformed
    {//GEN-HEADEREND:event_luaMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_LUA);
    }//GEN-LAST:event_luaMenuItemActionPerformed

    private void makefileMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_makefileMenuItemActionPerformed
    {//GEN-HEADEREND:event_makefileMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_MAKEFILE);
    }//GEN-LAST:event_makefileMenuItemActionPerformed

    private void perlMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_perlMenuItemActionPerformed
    {//GEN-HEADEREND:event_perlMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_PERL);
    }//GEN-LAST:event_perlMenuItemActionPerformed

    private void pythonMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_pythonMenuItemActionPerformed
    {//GEN-HEADEREND:event_pythonMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_PYTHON);
    }//GEN-LAST:event_pythonMenuItemActionPerformed

    private void sqlMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sqlMenuItemActionPerformed
    {//GEN-HEADEREND:event_sqlMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_SQL);
    }//GEN-LAST:event_sqlMenuItemActionPerformed

    private void tclButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_tclButtonActionPerformed
    {//GEN-HEADEREND:event_tclButtonActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_TCL);
    }//GEN-LAST:event_tclButtonActionPerformed

    private void unixshellMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_unixshellMenuItemActionPerformed
    {//GEN-HEADEREND:event_unixshellMenuItemActionPerformed
        updateSyntax(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
    }//GEN-LAST:event_unixshellMenuItemActionPerformed

    private void saveAsMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveAsMenuItemActionPerformed
    {//GEN-HEADEREND:event_saveAsMenuItemActionPerformed
        currentTab.setTitle(null);
        saveFile(1);
    }//GEN-LAST:event_saveAsMenuItemActionPerformed

    private void saveAllMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveAllMenuItemActionPerformed
    {//GEN-HEADEREND:event_saveAllMenuItemActionPerformed
        int selectedIndex = tabbedPane.getSelectedIndex();

        for (int i = 0; i < tabbedPane.getTabCount(); i++)
        {
            tabbedPane.setSelectedIndex(i);
            saveFile(0);
        }

        tabbedPane.setSelectedIndex(selectedIndex);
    }//GEN-LAST:event_saveAllMenuItemActionPerformed

    private void closeAllMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_closeAllMenuItemActionPerformed
    {//GEN-HEADEREND:event_closeAllMenuItemActionPerformed
        int count = tabbedPane.getTabCount();

        for (int i = 0; i < count; i++)
        {
            closeFile();
        }
    }//GEN-LAST:event_closeAllMenuItemActionPerformed

    private void englishSpellingMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_englishSpellingMenuItemActionPerformed
    {//GEN-HEADEREND:event_englishSpellingMenuItemActionPerformed
        if (!currentTab.getSpelling().equals("english"))
        {
            currentTab.getTextArea().addParser(parser);
            currentTab.setSpelling("english");
        }
        else
        {
            currentTab.getTextArea().removeParser(parser);
            currentTab.setSpelling("");
        }
        updateSpellingMenu();
    }//GEN-LAST:event_englishSpellingMenuItemActionPerformed

    private void tabbedPaneStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_tabbedPaneStateChanged
    {//GEN-HEADEREND:event_tabbedPaneStateChanged
        updateTab();
    }//GEN-LAST:event_tabbedPaneStateChanged

    private void undoMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_undoMenuItemActionPerformed
    {//GEN-HEADEREND:event_undoMenuItemActionPerformed
        currentTab.getTextArea().undoLastAction();
    }//GEN-LAST:event_undoMenuItemActionPerformed

    private void redoMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_redoMenuItemActionPerformed
    {//GEN-HEADEREND:event_redoMenuItemActionPerformed
        currentTab.getTextArea().redoLastAction();
    }//GEN-LAST:event_redoMenuItemActionPerformed

    private void cutMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cutMenuItemActionPerformed
    {//GEN-HEADEREND:event_cutMenuItemActionPerformed
        currentTab.getTextArea().cut();
    }//GEN-LAST:event_cutMenuItemActionPerformed

    private void copyMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_copyMenuItemActionPerformed
    {//GEN-HEADEREND:event_copyMenuItemActionPerformed
        currentTab.getTextArea().copy();
    }//GEN-LAST:event_copyMenuItemActionPerformed

    private void pasteMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_pasteMenuItemActionPerformed
    {//GEN-HEADEREND:event_pasteMenuItemActionPerformed
        currentTab.getTextArea().paste();
    }//GEN-LAST:event_pasteMenuItemActionPerformed

    private void selectAllMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectAllMenuItemActionPerformed
    {//GEN-HEADEREND:event_selectAllMenuItemActionPerformed
        currentTab.getTextArea().selectAll();
    }//GEN-LAST:event_selectAllMenuItemActionPerformed

    private void findMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_findMenuItemActionPerformed
    {//GEN-HEADEREND:event_findMenuItemActionPerformed
        this.setMinimumSize(new Dimension(700, 50));
        RSyntaxTextArea textArea = currentTab.getTextArea();

        if (panel.getTextArea() != textArea)
        {
            panel.setTextArea(textArea);
        }

        if (!findPanel.isVisible())
        {
            findPanel.setVisible(true);
        }

        findTextField.requestFocusInWindow();
    }//GEN-LAST:event_findMenuItemActionPerformed

    private void findPanelCloseButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_findPanelCloseButtonActionPerformed
    {//GEN-HEADEREND:event_findPanelCloseButtonActionPerformed
        panel.endSearch();

        Dimension size = new Dimension(500, 50);

        if (this.getMinimumSize() != size)
        {
            this.setMinimumSize(size);
        }

        if (findPanel.isVisible())
        {
            findPanel.setVisible(false);
        }

        if (replacePanel.isVisible())
        {
            replacePanel.setVisible(false);
        }
    }//GEN-LAST:event_findPanelCloseButtonActionPerformed

    private void findNextMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_findNextMenuItemActionPerformed
    {//GEN-HEADEREND:event_findNextMenuItemActionPerformed
        findNextButton.doClick();
    }//GEN-LAST:event_findNextMenuItemActionPerformed

    private void findPreviousMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_findPreviousMenuItemActionPerformed
    {//GEN-HEADEREND:event_findPreviousMenuItemActionPerformed
        findPreviousButton.doClick();
    }//GEN-LAST:event_findPreviousMenuItemActionPerformed

    private void replaceMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_replaceMenuItemActionPerformed
    {//GEN-HEADEREND:event_replaceMenuItemActionPerformed
        findMenuItem.doClick();

        if (!replacePanel.isVisible())
        {
            replacePanel.setVisible(true);
        }
    }//GEN-LAST:event_replaceMenuItemActionPerformed

    private void replaceNextMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_replaceNextMenuItemActionPerformed
    {//GEN-HEADEREND:event_replaceNextMenuItemActionPerformed
        replaceButton.doClick();
    }//GEN-LAST:event_replaceNextMenuItemActionPerformed

    private void unmarkMenuItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_unmarkMenuItemActionPerformed
    {//GEN-HEADEREND:event_unmarkMenuItemActionPerformed
        findPanelCloseButton.doClick();
    }//GEN-LAST:event_unmarkMenuItemActionPerformed

    public static void main(String args[])
    {
        try
        {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                if ("Nimbus".equals(info.getName()))
                {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;

                }
            }
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex)
        {
            java.util.logging.Logger.getLogger(Frame.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(()
                -> 
                {
                    new Frame().setVisible(true);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButtonMenuItem actionscriptMenuItem;
    private javax.swing.JRadioButtonMenuItem assemblyMenuItem;
    private javax.swing.JRadioButtonMenuItem batchfileMenuItem;
    private javax.swing.JRadioButtonMenuItem cMenuItem;
    private javax.swing.JMenuItem closeAllMenuItem;
    private javax.swing.JMenuItem closeFileMenuItem;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JRadioButtonMenuItem cppMenuItem;
    private javax.swing.JRadioButtonMenuItem csharpMenuItem;
    private javax.swing.JRadioButtonMenuItem cssMenuItem;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JRadioButtonMenuItem delphiMenuItem;
    private javax.swing.JMenu editMenu;
    private javax.swing.JRadioButtonMenuItem englishSpellingMenuItem;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu findMenu;
    private javax.swing.JMenuItem findMenuItem;
    private javax.swing.JButton findNextButton;
    private javax.swing.JMenuItem findNextMenuItem;
    private javax.swing.JPanel findPanel;
    private javax.swing.JButton findPanelCloseButton;
    private javax.swing.JButton findPreviousButton;
    private javax.swing.JMenuItem findPreviousMenuItem;
    private javax.swing.JTextField findTextField;
    private javax.swing.JRadioButtonMenuItem htmlMenuItem;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenu jMenu7;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JPopupMenu jPopupMenu2;
    private javax.swing.JPopupMenu jPopupMenu3;
    private javax.swing.JPopupMenu jPopupMenu4;
    private javax.swing.JPopupMenu jPopupMenu5;
    private javax.swing.JPopupMenu jPopupMenu6;
    private javax.swing.JPopupMenu jPopupMenu7;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItem1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextArea jTextArea2;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JRadioButtonMenuItem javaMenuItem;
    private javax.swing.JRadioButtonMenuItem javascriptMenuItem;
    private javax.swing.JRadioButtonMenuItem jsonMenuItem;
    private javax.swing.JRadioButtonMenuItem latexMenuItem;
    private javax.swing.JRadioButtonMenuItem lispMenuItem;
    private javax.swing.JRadioButtonMenuItem luaMenuItem;
    private javax.swing.JRadioButtonMenuItem makefileMenuItem;
    private javax.swing.JCheckBox matchCaseCB;
    private javax.swing.JMenuItem newFileMenuItem;
    private javax.swing.JMenuItem openFileMenuItem;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JRadioButtonMenuItem perlMenuItem;
    private javax.swing.JRadioButtonMenuItem phpMenuItem;
    private javax.swing.JRadioButtonMenuItem plaintextMenuItem;
    private javax.swing.JRadioButtonMenuItem pythonMenuItem;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JCheckBox regexCB;
    private javax.swing.JButton replaceAllButton;
    private javax.swing.JButton replaceButton;
    private javax.swing.JMenuItem replaceMenuItem;
    private javax.swing.JMenuItem replaceNextMenuItem;
    private javax.swing.JPanel replacePanel;
    private javax.swing.JTextField replaceTextField;
    private javax.swing.JRadioButtonMenuItem rubyMenuItem;
    private javax.swing.JMenuItem saveAllMenuItem;
    private javax.swing.JMenuItem saveAsMenuItem;
    private javax.swing.JMenuItem saveFileMenuItem;
    private javax.swing.JRadioButtonMenuItem scalaMenuItem;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JMenu spellingMenu;
    private javax.swing.JRadioButtonMenuItem sqlMenuItem;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JLabel statusLabel1;
    private javax.swing.JLabel statusLabel2;
    private javax.swing.JLabel syntaxLabel;
    private javax.swing.JMenu syntaxMenu;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JRadioButtonMenuItem tclButton;
    private javax.swing.JRadioButtonMenuItem themeDarkMenuItem;
    private javax.swing.JRadioButtonMenuItem themeLightMenuItem;
    private javax.swing.JMenu themeMenu;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JRadioButtonMenuItem unixshellMenuItem;
    private javax.swing.JMenuItem unmarkMenuItem;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JCheckBox wholeWordCB;
    private javax.swing.JRadioButtonMenuItem xmlMenuItem;
    // End of variables declaration//GEN-END:variables
}
