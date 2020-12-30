import com.github.kiulian.downloader.YoutubeException;
import com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException;

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

            System.out.println("");
            while (true) {
                Scanner scan = new Scanner(System.in);
                System.out.print("YoutubeArchive> ");
                String cmd = scan.nextLine();
                String[] args = cmd.split(" ");
                args[0] = args[0].toLowerCase();

                if (args[0].equals("addvideo") || args[0].equals("av")) {
                    if (args.length > 1) {
                        AddVideo.addVideo(args[1], con);
                    } else {
                        System.out.println("You need to use at least 1 Parameter! Use h for more information!");
                    }
                }

                else if (args[0].equals("update") || args[0].equals("u")) {
                    if (args.length > 1) {
                        UpdateVideo.checkVideo(args[1], con);
                    } else {
                        System.out.println("You need to use at least 1 Parameter! Use h for more information!");
                    }
                }

                else if (args[0].equals("updateall") || args[0].equals("ua")) {
                    UpdateVideo.checkAll(con);
                }

                else if (args[0].equals("help") || args[0].equals("h")) {
                    System.out.println("Help:\n" +
                            "addvideo/av (videoId) | adds a video to the list.\n" +
                            "update/u (videoId) | manually update a video\n" +
                            "updateall/ua | manually update every video\n" +
                            "help/h | this\n" +
                            "quit/q | obvious");
                }

                else if (args[0].equals("quit") || args[0].equals("q")) {
                    System.out.println("Quitting YoutubeArchive...");
                    break;
                } else {
                    System.out.println("Unknown command! Use h for help!");
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
            if (tableName.equals("VideoList")) {
                con.prepareStatement("CREATE TABLE VideoList(" +
                        "VideoID varchar(255)," +
                        "VideoTitle varchar(255)," +
                        "ChannelLink varchar(255)," +
                        "LastChecked BIGINT," +
                        "VideoItag int," +
                        "AudioItag int)").execute();
            } else if (tableName.equals("ArchivedVideo")) {
                con.prepareStatement("CREATE TABLE ArchivedVideo(" +
                        "VideoVersionID int NOT NULL AUTO_INCREMENT," +
                        "VideoID varchar(255)," +
                        "Time BIGINT," +
                        "PRIMARY KEY (VideoVersionID))").execute();
            } else if (tableName.equals("ArchivedAudio")) {
                con.prepareStatement("CREATE TABLE ArchivedAudio(" +
                        "AudioVersionID int NOT NULL AUTO_INCREMENT," +
                        "VideoID varchar(255)," +
                        "Time BIGINT," +
                        "PRIMARY KEY (AudioVersionID))").execute();
            } else if (tableName.equals("ArchivedDescription")) {
                con.prepareStatement("CREATE TABLE ArchivedDescription(" +
                        "DescriptionVersionID int NOT NULL AUTO_INCREMENT," +
                        "VideoID varchar(255)," +
                        "Time BIGINT," +
                        "Description varchar(5000)," +
                        "PRIMARY KEY (DescriptionVersionID))").execute();
            } else if (tableName.equals("Messages")) {
                con.prepareStatement("CREATE TABLE Messages(" +
                        "MessageId int NOT NULL AUTO_INCREMENT," +
                        "Message varchar(1023)," +
                        "Time BIGINT," +
                        "PRIMARY KEY (MessageId))").execute();
            } else throw new TableCreationFailedException();
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
            con.prepareStatement("INSERT INTO messages " +
                    "VALUES(NULL," +
                    "'" + msg +"',"
                    + System.currentTimeMillis() +")").execute();
        } catch (SQLException throwables) {
            System.out.println("Something went wrong while trying to save a message!");
            System.out.println("The message should have been: " + msg);
            throwables.printStackTrace();
        }
    }

}
