package syntaxeditor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import static syntaxeditor.SyntaxEditor.THEME_COUNT;
import static syntaxeditor.SyntaxEditor.DIRECTORY;

public class Database
{
    private Connection connection;
    private Statement statement;
    private ResultSet result;
    
    public void open()
    {
        try
        {
            File file = new File(DIRECTORY + "database.db");
            boolean fileExists = file.exists();

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getPath());

            if (!fileExists)
            {
                String sql;

                statement = connection.createStatement();
                sql = "create table History "
                        + "(Path text, "
                        + "Status int not null)";
                statement.executeUpdate(sql);
                statement = connection.createStatement();
                sql = "create table Themes "
                        + "(Title text not null, "
                        + "Status int not null)";
                statement.executeUpdate(sql);
                statement = connection.createStatement();
                sql = "insert into themes (title, status) "
                        + "values ('light', 0), "
                        + "('dark', 1);";
                statement.executeUpdate(sql);
            }
        }
        catch (ClassNotFoundException | SQLException e)
        {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
    }
    
    public void insertFile(String path, int status)
    {
        try
        {
            statement = connection.createStatement();
            String sql = "insert into history (path, status) values ('" + path + "', " + status + ");";
            statement.executeUpdate(sql);
        }
        catch (SQLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void clearHistory()
    {
        try
        {
            statement = connection.createStatement();
            String sql = "delete from history;";
            statement.executeUpdate(sql);
        }
        catch (SQLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    // setters
    public void setTheme(String title)
    {
        try
        {
            String sql;

            statement = connection.createStatement();
            sql = "update themes set status = 0 where status = 1";
            statement.executeUpdate(sql);
            statement = connection.createStatement();
            sql = "update themes set status = 1 where title = '" + title + "'";
            statement.executeUpdate(sql);
        }
        catch (SQLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    // getters
    public LinkedHashMap getHistory()
    {
        try
        {
            LinkedHashMap<String, Integer> map = new LinkedHashMap();

            statement = connection.createStatement();
            result = statement.executeQuery("select * from history;");
            
            while (result.next())
                map.put(result.getString("Path"), result.getInt("Status"));
            
            return map;
        }
        catch (SQLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }
    
    public String getSelectedTheme()
    {
        try
        {
            statement = connection.createStatement();
            result = statement.executeQuery("select * from themes where status = 1;");
            result.next();
            return result.getString("Title");
        }
        catch (SQLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return null;
    }
    
    public String[] getThemeTitles()
    {
        try
        {
            String[] titles = new String[THEME_COUNT];
            int i = 0;

            statement = connection.createStatement();
            result = statement.executeQuery("select * from themes;");

            while (result.next())
                titles[i++] = result.getString("Title");

            return titles;
        }
        catch (SQLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }
    
    public void close()
    {
        try
        {
            statement.close();
            result.close();
            connection.close();
        }
        catch (SQLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}