import com.github.kiulian.downloader.YoutubeException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class Menu {

    private static Queue<String> commandQueue = new LinkedList<>();
    private static int commandCounter = 0;

    public static void menuLoop(Connection con, int drive) {
        boolean quit = false;
        while (!quit) {
            try {
                checkUnreadMessages(con);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            File[] computerDrives = File.listRoots();
            println("Free storage capacity: " + (computerDrives[drive].getFreeSpace() / 1073741824) +
                    " GB / " + (computerDrives[drive].getTotalSpace() / 1073741824) + " GB");

            Scanner scan = new Scanner(System.in);
            print("YoutubeArchive> ");
            String cmd;
            if (commandCounter != 0) {
                cmd = commandQueue.remove();
                commandCounter--;
            } else {
                cmd = scan.nextLine();
            }
            String[] args = cmd.split(" ");
            args[0] = args[0].toLowerCase();

            try {
                switch (args[0]) {
                    case "addvideo":
                    case "av":
                        if (args.length > 1) {
                            AddVideo.addVideo(args[1], con);
                        } else {
                            println("You need to use at least 1 parameter! Use h for more information!");
                        }
                        break;
                    case "addplaylist":
                    case "ap":
                        if (args.length > 1) {
                            AddVideo.addPlaylist(args[1], Integer.parseInt(args[2]), con);
                        } else {
                            println("You need to use 2 parameters! Use h for more information!");
                        }
                        break;
                    case "addchannel":
                    case "ac":
                        if (args.length > 1) {
                            AddVideo.addChannel(args[1], Integer.parseInt(args[2]), con);
                        } else {
                            println("You need to use 2 parameters! Use h for more information!");
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
                            println("Length must be at least 12!");
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
                        println("Read messages have been removed!");
                        break;
                    case "remove":
                    case "r":
                        if (args.length <= 2) {
                            println("This command requires the use of 2 parameters! Check h for help.");
                        } else {
                            boolean correctArgument = false;
                            if (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("both")) {
                                ps = con.prepareStatement("DELETE FROM videolist WHERE VideoID = (?)");
                                ps.setString(1, args[2]);
                                ps.execute();
                                println("Successfully deleted the video of the list!");
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
                                    println("Successfully deleted every saved file from the given video.");
                                } else {
                                    println("Something went wrong while trying to delete the storage directory for the video.");
                                }
                                correctArgument = true;
                            }
                            if (!correctArgument) {
                                println("Your first argument needs to be either list, storage or both.");
                            }
                        }
                        break;
                    case "wipe":
                    case "w":
                        if (new File("wipe").exists()) {
                            con.prepareStatement("DROP TABLE " +
                                    "archivedlowvideoaudio, archivedaudio, archiveddescription, archivedthumbnail, archivedtitle," +
                                    "archivedvideo, messages, videolist").execute();
                            println("Database has been wiped!");
                            FileUtils.deleteDirectory(new File("storage"));
                            println("Storage has been wiped!");
                            println("Quitting YoutubeArchive...");
                            quit = true;
                        } else {
                            println("In order to complete a wipe, you must create a file called wipe.");
                        }
                        break;
                    case "help":
                    case "h":
                        println("Help:\n" +
                                "addvideo/av (videoId) | adds a video to the list.\n" +
                                "addplaylist/ap (playlistID) (minViews) | adds playlist\n" +
                                "addchannel/ac (channelId) (minViews) | adds the channel uploads playlist to the playlist list\n" +
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
                        println("Quitting YoutubeArchive...");
                        quit = true;
                        break;
                    default:
                        println("Unknown command! Use h for help!");
                        break;
                }
            } catch (YoutubeException | SQLException | VideoCodecNotFoundException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void printTable(ResultSet rs, int length) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
            print(rsmd.getColumnName(i));
            for (int j = 0; j < length - rsmd.getColumnName(i).length(); j++) {
                print(" ");
            }
            if (i != rsmd.getColumnCount()) print(" | ");
        }
        while (rs.next()) {
            println();
            for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
                String content = rs.getString(i);
                if (content == null) content = "null"; // To prevent Nullpointer in "content.length()"
                print(content);
                for (int j = 0; j < length - content.length(); j++) {
                    print(" ");
                }
                if (i != rsmd.getColumnCount()) print(" | ");
            }
        }
        println();
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
            println();
            for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
                if (length[i - 1] < rs.getString(i).length()) {
                    length[i - 1] = rs.getString(i).length();
                }
            }
        }
        for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
            print(rsmd.getColumnName(i));
            for (int j = 0; j < length[i - 1] - rsmd.getColumnName(i).length(); j++) {
                print(" ");
            }
            if (i != rsmd.getColumnCount()) print(" | ");
        }
        rs.beforeFirst();
        while (rs.next()) {
            println();
            for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
                String content = rs.getString(i);
                if (content == null) content = "null"; // To prevent Nullpointer in "content.length()"
                print(content);
                for (int j = 0; j < length[i - 1] - content.length(); j++) {
                    print(" ");
                }
                if (i != rsmd.getColumnCount()) print(" | ");
            }
        }
        println();
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
            Menu.println("Something went wrong while trying to save a message!");
            Menu.println("The message should have been: " + msg);
            e.printStackTrace();
        }
    }

    private static void checkUnreadMessages(Connection con) throws SQLException {
        ResultSet rs = con.prepareStatement("SELECT * FROM messages WHERE MessageRead = 0").executeQuery();
        int unreadMessages = 0;
        while (rs.next()) unreadMessages++;
        if (unreadMessages != 0) println("You have " + unreadMessages + " unread messages!");
    }

    public static void println(String s) {
        System.out.print(s + "\n");
        Server.sendMessage(s + "\n");
    }

    public static void println() {
        System.out.print("\n");
        Server.sendMessage("\n");
    }

    public static void print(String s) {
        System.out.print(s);
        Server.sendMessage(s);
    }

    public static String sendCommand(String s) {
        if (commandCounter <= 10) {
            commandQueue.add(s);
            commandCounter++;
            return "+cmd;/Command has been added!";
        } else {
            return "-cmd;/There can only be max. 10 commands in queue!";
        }
    }

}
