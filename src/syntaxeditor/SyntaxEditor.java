/*---------------------------------------------------------
 *  JAVA "Text editor with syntax highlighting"
 *
 *  @author Arminas Peckus
 *---------------------------------------------------------*/
package syntaxeditor;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

public class SyntaxEditor
{
    // constants
    public static final String DIRECTORY = "";
//    public static final String DIRECTORY = System.getenv("APPDATA") + "\\Syntax Editor\\";
    public static final String DICTIONARY_DIR = "dictionaries/english_dic.zip";
    public static final String THEME_DIR = "resources/themes/";
    public static final String ICON_DIR = "resources/icons/";
    public static final int THEME_COUNT = 2;

    // icons
    private List<? extends Image> getIconList() throws IOException
    {
        List<Image> list = new ArrayList<>();
        ImageIcon icon;
        int size = 16;

        for (int i = 0; i < 4; i++, size *= 2)
        {
            InputStream stream = getClass().getResourceAsStream(ICON_DIR + "icon" + size + ".png");
            icon = new ImageIcon(ImageIO.read(stream));
            list.add(icon.getImage());
        }

        return list;
    }

    public static void main(String[] args) throws InterruptedException, ClassNotFoundException, InstantiationException, IllegalAccessException
    {
        try
        {
            SyntaxEditor textEditor = new SyntaxEditor();
            File file = new File(DIRECTORY + "history");

            if (!file.exists())
            {
                file.mkdirs();
            }

            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Frame frame = new Frame();
            List<? extends Image> iconList = textEditor.getIconList();

            frame.addWindowListener(new java.awt.event.WindowAdapter()
            {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent)
                {
                    frame.exit();
                }
            });

            frame.setIconImages(iconList);
            frame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            if (args.length > 0)
            {
                for (String arg : args)
                {
                    file = new File(arg);

                    if (file.exists())
                    {
                        if (file.isDirectory())
                        {
                            frame.openFolder(file);
                        }
                        else
                        {
                            frame.open(file);
                        }
                    }
                }
            }
        }
        catch (UnsupportedLookAndFeelException | IOException ex)
        {
            Logger.getLogger(SyntaxEditor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
