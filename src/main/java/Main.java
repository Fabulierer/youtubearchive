import com.github.kiulian.downloader.YoutubeException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Scanner;

public class Main {

    public static int drive;

    public static void main(String[] arguments) throws DatabaseConnectionFailedException, IOException {
        File[] computerDrives = File.listRoots();
        File settings = new File("./settings.txt");
        if (settings.createNewFile()) {
            System.out.println("New Settings file created!");
            FileWriter fileWriter = new FileWriter(settings);
            fileWriter.write("url \nuser \npassword \ndrive ");
            fileWriter.close();
            System.out.println("The following drives have been found. Please pick one in the \"settings.txt\" file.");
            for (int i = 0; i < computerDrives.length; i++) {
                System.out.println(i + ": " + computerDrives[i]);
            }
            return;
        } else System.out.println("Settings file found...");

        Scanner settingsScanner = new Scanner(settings);

        boolean quit = false;
        while (!quit) {
            try {
                String url = settingsScanner.nextLine().split(" ")[1];
                String user = settingsScanner.nextLine().split(" ")[1];
                String password = settingsScanner.nextLine().split(" ")[1];
                drive = Integer.parseInt(settingsScanner.nextLine().split(" ")[1]);

                System.out.println("Settings successfully loaded!");
                System.out.println("Trying to connect to database...");

                Connection con = DriverManager.getConnection(url, user, password);
                con.prepareStatement("SET CHARACTER SET utf8").execute();
                System.out.println("Successfully connected to database!");

                checkTable("videolist", con);
                checkTable("archivedlowvideoaudio", con);
                checkTable("archivedvideo", con);
                checkTable("archivedaudio", con);
                checkTable("archiveddescription", con);
                checkTable("archivedthumbnail", con);
                checkTable("archivedtitle", con);
                checkTable("archivedtags", con);
                checkTable("messages", con);
                updateTables(con);

                System.out.println("Checking if 'storage' directory exists...");
                Path storage = Paths.get("./storage/");
                try {
                    Files.createDirectory(storage);
                    System.out.println("Directory 'storage' has been created.");
                } catch (FileAlreadyExistsException e) {
                    System.out.println("Directory 'storage' already exists.");
                }

                System.out.println();

                while (!quit) {
                    checkUnreadMessages(con);
                    System.out.println("Free storage capacity: " + (computerDrives[drive].getFreeSpace() / 1073741824) +
                            " GB / " + (computerDrives[drive].getTotalSpace() / 1073741824) + " GB");

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
                        case "addplaylist":
                        case "ap":
                            if (args.length > 1) {
                                AddVideo.addPlaylist(args[1], con);
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
                        case "scheduleupdate":
                        case "su":
                            if (args.length > 1) {
                                UpdateVideo.scheduleUpdate(con, Integer.parseInt(args[1]));
                            } else {
                                UpdateVideo.scheduleUpdate(con, 24);
                            }
                            break;
                        case "status":
                        case "s":
                            int length;
                            if (args.length == 1) {
                                length = 25;
                            } else if (Integer.parseInt(args[1]) < 12) {
                                length = 25;
                                System.out.println("Length must be at least 12!");
                            } else length = Integer.parseInt(args[1]);
                            PreparedStatement ps = con.prepareStatement("SELECT SUBSTRING(VideoTitle, 1, (?)) AS Title," +
                                    "ChannelName AS Channel," +
                                    "LastChecked," +
                                    "(SELECT COUNT(y.VideoID) - 1 FROM archivedvideo y WHERE x.VideoID = y.VideoID) AS VideoChanged," +
                                    "(SELECT COUNT(y.VideoID) - 1 FROM archivedaudio y WHERE x.VideoID = y.VideoID) AS AudioChanged," +
                                    "(SELECT COUNT(y.VideoID) - 1 FROM archiveddescription y WHERE x.VideoID = y.VideoID) AS DescriptionChanged " +
                                    "FROM videolist x");
                            ps.setInt(1, length);
                            ResultSet rs = ps.executeQuery();
                            printTable(rs, length);
                            break;
                        case "messages":
                        case "m":
                            printTableMax(con.prepareStatement("SELECT Time, Message, MessageRead FROM messages ORDER BY Time DESC").executeQuery());
                            con.prepareStatement("UPDATE messages SET MessageRead = 1 WHERE MessageRead = 0").execute();
                            break;
                        case "clearmessages":
                        case "cm":
                            con.prepareStatement("DELETE FROM messages WHERE MessageRead = 1").execute();
                            System.out.println("Read messages have been removed!");
                            break;
                        case "remove":
                        case "r":
                            if (args.length <= 2) {
                                System.out.println("This command requires the use of 2 parameters! Check h for help.");
                            } else {
                                boolean correctArgument = false;
                                if (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("both")) {
                                    ps = con.prepareStatement("DELETE FROM videolist WHERE VideoID = (?)");
                                    ps.setString(1, args[2]);
                                    ps.execute();
                                    System.out.println("Successfully deleted the video of the list!");
                                    correctArgument = true;
                                }
                                if (args[1].equalsIgnoreCase("storage") || args[1].equalsIgnoreCase("both")) {
                                    ps = con.prepareStatement("DELETE FROM archivedaudio WHERE VideoID = (?)");
                                    ps.setString(1, args[2]);
                                    ps.execute();
                                    ps = con.prepareStatement("DELETE FROM archiveddescription WHERE VideoID = (?)");
                                    ps.setString(1, args[2]);
                                    ps.execute();
                                    ps = con.prepareStatement("DELETE FROM archivedthumbnail WHERE VideoID = (?)");
                                    ps.setString(1, args[2]);
                                    ps.execute();
                                    ps = con.prepareStatement("DELETE FROM archivedtitle WHERE VideoID = (?)");
                                    ps.setString(1, args[2]);
                                    ps.execute();
                                    ps = con.prepareStatement("DELETE FROM archivedvideo WHERE VideoID = (?)");
                                    ps.setString(1, args[2]);
                                    ps.execute();
                                    File directory = new File("./storage/" + args[2]);
                                    if (directory.delete()) {
                                        System.out.println("Successfully deleted every saved file from the given video.");
                                    } else {
                                        System.out.println("Something went wrong while trying to delete the storage directory for the video.");
                                    }
                                    correctArgument = true;
                                }
                                if (!correctArgument) {
                                    System.out.println("Your first argument needs to be either list, storage or both.");
                                }
                            }
                            break;
                        case "wipe":
                        case "w":
                            if (new File("wipe").exists()) {
                                con.prepareStatement("DROP TABLE " +
                                        "archivedlowvideoaudio, archivedaudio, archiveddescription, archivedthumbnail, archivedtitle," +
                                        "archivedvideo, messages, videolist").execute();
                                System.out.println("Database has been wiped!");
                                FileUtils.deleteDirectory(new File("storage"));
                                System.out.println("Storage has been wiped!");
                                System.out.println("Quitting YoutubeArchive...");
                                quit = true;
                            } else {
                                System.out.println("In order to complete a wipe, you must create a file called wipe.");
                            }
                            break;
                        case "startserver":
                        case "ss":
                            Server.startServer(con);
                            break;
                        case "help":
                        case "h":
                            System.out.println("Help:\n" +
                                    "addvideo/av (videoId) | adds a video to the list.\n" +
                                    "addplaylist/ap (playlistID) | adds every video from a playlist to the list\n" +
                                    "update/u <videoId> | manually update a video\n" +
                                    "updateall/ua | manually update every video\n" +
                                    "scheduleupdate/su | schedule an update for every video\n" +
                                    "status/s | shows the status of all videos\n" +
                                    "messages/m | show messages\n" +
                                    "clearmessages/cm | delete already read messages\n" +
                                    "remove/r (list/storage/both) (videoId) | removes from either list, storage or both\n" +
                                    "wipe/w | wipes everything\n" +
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
                if (UpdateVideo.t != null) UpdateVideo.t.cancel();
            } catch (IndexOutOfBoundsException | TableCreationFailedException | YoutubeException | VideoCodecNotFoundException | IOException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new DatabaseConnectionFailedException();
            }
        }
    }

    private static void updateTables(Connection con) throws SQLException {
        // adds "lowVideoAudioItag" to videolist
        try {
            con.prepareStatement("SELECT LowVideoAudioItag FROM videolist").execute();
        } catch (SQLSyntaxErrorException e) {
            System.out.println("Updated the 'videolist' table: added 'lowVideoAudioItag'");
            con.prepareStatement("ALTER TABLE videolist ADD LowVideoAudioItag int").execute();
            ResultSet rs = con.prepareStatement("SELECT VideoID FROM videolist").executeQuery();
            while (rs.next()) {
                String videoId = rs.getString(1);
                PreparedStatement ps = con.prepareStatement("DELETE FROM videolist WHERE VideoID = (?)");
                ps.setString(1, videoId);
                ps.execute();
                try {
                    AddVideo.addVideo(videoId, con);
                } catch (VideoCodecNotFoundException | YoutubeException videoCodecNotFoundException) {
                    videoCodecNotFoundException.printStackTrace();
                }
            }
        }
    }

    private static void checkTable(String tableName, Connection con) throws SQLException, TableCreationFailedException {
        System.out.println("Checking if table " + tableName + " exists...");
        try {
            con.prepareStatement("SELECT * FROM " + tableName).execute();
            System.out.println("Found the " + tableName + " Table.");
        } catch (SQLSyntaxErrorException e) {
            System.out.println("Couldn't find table '" + tableName + "'. Creating a new one...");
            switch (tableName) {
                case "videolist":
                    con.prepareStatement("CREATE TABLE videolist(" +
                            "VideoID varchar(255)," +
                            "VideoTitle varchar(255)," +
                            "ChannelName varchar(255)," +
                            "LastChecked TIMESTAMP," +
                            "VideoItag int," +
                            "AudioItag int," +
                            "LowVideoAudioItag int)").execute();
                    break;
                case "archivedlowvideoaudio":
                    con.prepareStatement("CREATE TABLE archivedlowvideoaudio(" +
                            "LowVideoAudioVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (LowVideoAudioVersionID))").execute();
                    break;
                case "archivedvideo":
                    con.prepareStatement("CREATE TABLE archivedvideo(" +
                            "VideoVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (VideoVersionID))").execute();
                    break;
                case "archivedaudio":
                    con.prepareStatement("CREATE TABLE archivedaudio(" +
                            "AudioVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (AudioVersionID))").execute();
                    break;
                case "archiveddescription":
                    con.prepareStatement("CREATE TABLE archiveddescription(" +
                            "DescriptionVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (DescriptionVersionID))").execute();
                    break;
                case "archivedthumbnail":
                    con.prepareStatement("CREATE TABLE archivedthumbnail(" +
                            "ThumbnailVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (ThumbnailVersionID))").execute();
                    break;
                case "archivedtitle":
                    con.prepareStatement("CREATE TABLE archivedtitle(" +
                            "TitleVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (TitleVersionID))").execute();
                    break;
                case "archivedtags":
                    con.prepareStatement("CREATE TABLE archivedtags(" +
                            "TagsVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (TagsVersionID))").execute();
                    break;
                case "messages":
                    con.prepareStatement("CREATE TABLE messages(" +
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
            } catch (SQLSyntaxErrorException ee) {
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
