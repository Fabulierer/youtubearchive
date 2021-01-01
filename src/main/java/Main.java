import com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Scanner;

public class Main {

    public static void main(String[] arguments) throws IOException, WrongSettingsFileException, DatabaseConnectionFailedException {
        File settings = new File("./sqlsettings.txt");
        if (settings.createNewFile()) {
            System.out.println("New SQL Settings file created!");
            FileWriter fileWriter = new FileWriter(settings);
            fileWriter.write("url=\nuser=\npassword=");
            fileWriter.close();
        } else System.out.println("SQL Settings file found...");

        Scanner settingsScanner = new Scanner(settings);

        try {
            String url = settingsScanner.nextLine().split(" ")[1];
            String user = settingsScanner.nextLine().split(" ")[1];
            String password = settingsScanner.nextLine().split(" ")[1];

            System.out.println("SQL Settings successfully loaded!");
            System.out.println("Trying to connect to database...");

            Connection con = DriverManager.getConnection(url, user, password);
            con.prepareStatement("SET CHARACTER SET utf8").execute();
            System.out.println("Successfully connected to database!");

            checkTable("VideoList", con);
            checkTable("ArchivedVideo", con);
            checkTable("ArchivedAudio", con);
            checkTable("ArchivedDescription", con);
            checkTable("Messages", con);

            System.out.println("Checking if 'storage' directory exists...");
            Path storage = Paths.get("./storage/");
            try {
                Files.createDirectory(storage);
                System.out.println("Directory 'storage' has been created.");
            } catch (FileAlreadyExistsException e) {
                System.out.println("Directory 'storage' already exists.");
            }

            System.out.println();

            boolean quit = false;
            while (!quit) {
                checkUnreadMessages(con);

                Scanner scan = new Scanner(System.in);
                System.out.print("YoutubeArchive> ");
                String cmd = scan.nextLine();
                String[] args = cmd.split(" ");
                args[0] = args[0].toLowerCase();

                switch (args[0]) {
                    case "addvideo":
                    case "av":
                        if (args.length > 1) {
                            AddVideo.addVideo(args[1], con);
                        } else {
                            System.out.println("You need to use at least 1 Parameter! Use h for more information!");
                        }
                        break;
                    case "update":
                    case "u":
                        if (args.length > 1) {
                            UpdateVideo.checkVideo(args[1], con);
                        } else {
                            UpdateVideo.checkAll(con);
                        }
                        break;
                    case "updateall":
                    case "ua":
                        UpdateVideo.checkAll(con);
                        break;
                    case "status":
                    case "s":
                        int length;
                        if (args.length == 1) {
                            length = 25;
                        }
                        else if (Integer.parseInt(args[1]) < 12) {
                            length = 25;
                            System.out.println("Length must be at least 12!");
                        }
                        else length = Integer.parseInt(args[1]);
                        PreparedStatement ps = con.prepareStatement("SELECT SUBSTRING(VideoTitle, 1, (?)) AS Title," +
                                "ChannelName AS Channel," +
                                "LastChecked," +
                                "(SELECT COUNT(y.VideoID) FROM archivedvideo y WHERE x.VideoID = y.VideoID) AS VideoChanged," +
                                "(SELECT COUNT(y.VideoID) FROM archivedaudio y WHERE x.VideoID = y.VideoID) AS AudioChanged," +
                                "(SELECT COUNT(y.VideoID) FROM archiveddescription y WHERE x.VideoID = y.VideoID) AS DescriptionChanged " +
                                "FROM videolist x");
                        ps.setInt(1, length);
                        ResultSet rs = ps.executeQuery();
                        printTable(rs, length);
                        break;
                    case "messages":
                    case "m":
                        con.prepareStatement("UPDATE messages SET MessageRead = 1 WHERE MessageRead = 0").execute();
                        printTableMax(con.prepareStatement("SELECT * FROM messages").executeQuery());
                        break;
                    case "wipe":
                    case "w":
                        if (new File("wipe").exists()) {
                            con.prepareStatement("DROP TABLE archivedaudio, archiveddescription, archivedvideo, messages, videolist").execute();
                            System.out.println("Database has been wiped!");
                            FileUtils.deleteDirectory(new File("storage"));
                            System.out.println("Storage has been wiped!");
                            System.out.println("Quitting YoutubeArchive...");
                            quit = true;
                        } else {
                            System.out.println("In order to complete a wipe, you must create a file called wipe.");
                        }
                        break;
                    case "help":
                    case "h":
                        System.out.println("Help:\n" +
                                "addvideo/av (videoId) | adds a video to the list.\n" +
                                "update/u (videoId) | manually update a video\n" +
                                "updateall/ua | manually update every video\n" +
                                "status/s | Shows the status of all videos\n" +
                                "help/h | this\n" +
                                "quit/q | obvious");
                        break;
                    case "quit":
                    case "q":
                        System.out.println("Quitting YoutubeArchive...");
                        quit = true;
                        break;
                    default:
                        System.out.println("Unknown command! Use h for help!");
                        break;
                }
            }
        } catch (IndexOutOfBoundsException e) {
            throw new WrongSettingsFileException();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new DatabaseConnectionFailedException();
        } catch (TableCreationFailedException e) {
            e.printStackTrace();
        }
    }

    private static void checkTable(String tableName, Connection con) throws SQLException, TableCreationFailedException {
        System.out.println("Checking if table " + tableName + " exists...");
        try {
            con.prepareStatement("SELECT * FROM " + tableName).executeQuery();
            System.out.println("Found the " + tableName + " Table.");
        } catch (MySQLSyntaxErrorException e) {
            System.out.println("Couldn't find table '" + tableName + "'. Creating a new one...");
            switch (tableName) {
                case "VideoList":
                    con.prepareStatement("CREATE TABLE VideoList(" +
                            "VideoID varchar(255)," +
                            "VideoTitle varchar(255)," +
                            "ChannelName varchar(255)," +
                            "LastChecked TIMESTAMP," +
                            "VideoItag int," +
                            "AudioItag int)").execute();
                    break;
                case "ArchivedVideo":
                    con.prepareStatement("CREATE TABLE ArchivedVideo(" +
                            "VideoVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (VideoVersionID))").execute();
                    break;
                case "ArchivedAudio":
                    con.prepareStatement("CREATE TABLE ArchivedAudio(" +
                            "AudioVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (AudioVersionID))").execute();
                    break;
                case "ArchivedDescription":
                    con.prepareStatement("CREATE TABLE ArchivedDescription(" +
                            "DescriptionVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "Description varchar(5000)," +
                            "PRIMARY KEY (DescriptionVersionID))").execute();
                    break;
                case "Messages":
                    con.prepareStatement("CREATE TABLE Messages(" +
                            "MessageId int NOT NULL AUTO_INCREMENT," +
                            "Message varchar(511)," +
                            "MessageRead BOOLEAN," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (MessageId))").execute();
                    break;
                default:
                    throw new TableCreationFailedException();
            }
            try {
                con.prepareStatement("SELECT * FROM " + tableName).executeQuery();
                System.out.println("Successfully created the new Table!");
            } catch (MySQLSyntaxErrorException ee) {
                ee.printStackTrace();
                throw new TableCreationFailedException();
            }
        }
    }

    public static void sendMessage(Connection con, String msg) {
        try {
            PreparedStatement ps = con.prepareStatement("INSERT INTO messages " +
                    "VALUES(NULL," +
                    "(?)," +
                    "0," +
                    "(?))");
            ps.setString(1, msg);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.execute();
        } catch (SQLException e) {
            System.out.println("Something went wrong while trying to save a message!");
            System.out.println("The message should have been: " + msg);
            e.printStackTrace();
        }
    }

    private static void printTable(ResultSet rs, int length) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
            System.out.print(rsmd.getColumnName(i));
            for (int j = 0; j < length - rsmd.getColumnName(i).length(); j++) {
                System.out.print(" ");
            }
            if (i != rsmd.getColumnCount()) System.out.print(" | ");
        }
        while (rs.next()) {
            System.out.println();
            for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
                String content = rs.getString(i);
                if (content == null) content = "null"; // To prevent Nullpointer in "content.length()"
                System.out.print(content);
                for (int j = 0; j < length - content.length(); j++) {
                    System.out.print(" ");
                }
                if (i != rsmd.getColumnCount()) System.out.print(" | ");
            }
        }
        System.out.println();
    }
    
    private static void printTableMax(ResultSet rs) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int[] length = new int[rsmd.getColumnCount()];

        for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
            if (length[i - 1] < rsmd.getColumnName(i).length()) {
                length[i - 1] = rsmd.getColumnName(i).length();
            }
        }
        while (rs.next()) {
            System.out.println();
            for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
                if (length[i - 1] < rs.getString(i).length()) {
                    length[i - 1] = rs.getString(i).length();
                }
            }
        }
        for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
            System.out.print(rsmd.getColumnName(i));
            for (int j = 0; j < length[i - 1] - rsmd.getColumnName(i).length(); j++) {
                System.out.print(" ");
            }
            if (i != rsmd.getColumnCount()) System.out.print(" | ");
        }
        rs.beforeFirst();
        while (rs.next()) {
            System.out.println();
            for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
                String content = rs.getString(i);
                if (content == null) content = "null"; // To prevent Nullpointer in "content.length()"
                System.out.print(content);
                for (int j = 0; j < length[i - 1] - content.length(); j++) {
                    System.out.print(" ");
                }
                if (i != rsmd.getColumnCount()) System.out.print(" | ");
            }
        }
        System.out.println();
    }

    private static void checkUnreadMessages(Connection con) throws SQLException {
        ResultSet rs = con.prepareStatement("SELECT * FROM messages WHERE MessageRead = 0").executeQuery();
        int unreadMessages = 0;
        while (rs.next()) unreadMessages++;
        if (unreadMessages != 0) System.out.println("You have " + unreadMessages + " unread messages!");
    }

}
