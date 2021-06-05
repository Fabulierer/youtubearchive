import com.github.kiulian.downloader.YoutubeException;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Scanner;

public class Main {

    public static String ver = "1.4.4";
    public static int drive;

    public static void main(String[] arguments) throws DatabaseConnectionFailedException, IOException {
        File[] computerDrives = File.listRoots();
        File settings = new File("./settings.txt");
        if (settings.createNewFile()) {
            Menu.println("New Settings file created!");
            FileWriter fileWriter = new FileWriter(settings);
            fileWriter.write("url \nuser \npassword \ndrive ");
            fileWriter.close();
            Menu.println("The following drives have been found. Please pick one in the \"settings.txt\" file.");
            for (int i = 0; i < computerDrives.length; i++) {
                Menu.println(i + ": " + computerDrives[i]);
            }
            return;
        } else Menu.println("Settings file found...");

        Scanner settingsScanner = new Scanner(settings);

            try {
                String url = settingsScanner.nextLine().split(" ")[1];
                String user = settingsScanner.nextLine().split(" ")[1];
                String password = settingsScanner.nextLine().split(" ")[1];
                drive = Integer.parseInt(settingsScanner.nextLine().split(" ")[1]);

                Menu.println("Settings successfully loaded!");
                Menu.println("Trying to connect to database...");

                Connection con = DriverManager.getConnection(url, user, password);
                con.prepareStatement("SET CHARACTER SET utf8").execute();
                Menu.println("Successfully connected to database!");

                checkTable("videolist", con);
                checkTable("archivedlowvideoaudio", con);
                checkTable("archivedvideo", con);
                checkTable("archivedaudio", con);
                checkTable("archiveddescription", con);
                checkTable("archivedthumbnail", con);
                checkTable("archivedtitle", con);
                checkTable("archivedtags", con);
                checkTable("archivedchannelname", con);
                checkTable("messages", con);
                checkTable("playlists", con);
                updateTables(con);

                Menu.println("Checking if 'storage' directory exists...");
                Path storage = Paths.get("./storage/");
                try {
                    Files.createDirectory(storage);
                    Menu.println("Directory 'storage' has been created.");
                } catch (FileAlreadyExistsException e) {
                    Menu.println("Directory 'storage' already exists.");
                }

                Menu.println("Starting server...");
                Server.startServer(con);

                Menu.println("Checking version...");
                Menu.println(checkVersion() ? "You're up to date!" : "A new version is available to download!");

                Menu.println();

                Menu.menuLoop(con, drive);

                // Shutdown
                if (UpdateVideo.t != null) UpdateVideo.t.cancel();
                Server.stopServer();
            } catch (IndexOutOfBoundsException | TableCreationFailedException | IOException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new DatabaseConnectionFailedException();
            }


    }

    private static void updateTables(Connection con) throws SQLException {
        // adds "lowVideoAudioItag" to videolist table
        try {
            con.prepareStatement("SELECT LowVideoAudioItag FROM videolist").execute();
        } catch (SQLSyntaxErrorException e) {
            Menu.println("Updating the 'videolist' table: adding 'lowVideoAudioItag'");
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
        // adds "Views" to playlist table
        try {
            con.prepareStatement("SELECT Views FROM playlists").execute();
        } catch (SQLSyntaxErrorException e) {
            Menu.println("Updating the 'playlist' table: adding 'Views'");
            con.prepareStatement("ALTER TABLE playlists ADD Views int").execute();
            ResultSet rs = con.prepareStatement("SELECT Playlist FROM playlists").executeQuery();
            while (rs.next()) {
                String playlist = rs.getString(1);
                PreparedStatement ps = con.prepareStatement("DELETE FROM playlists WHERE Playlist = (?)");
                ps.setString(1, playlist);
                ps.execute();
                try {
                    AddVideo.addPlaylist(playlist,0, con);
                } catch (YoutubeException videoCodecNotFoundException) {
                    videoCodecNotFoundException.printStackTrace();
                }
            }
        }
        try {
            con.prepareStatement("SELECT Active FROM videolist").execute();
        } catch (SQLSyntaxErrorException e) {
            Menu.println("Updating the 'videolist' table: adding 'Active'");
            con.prepareStatement("ALTER TABLE videolist ADD Active boolean").execute();
            con.prepareStatement("UPDATE videolist SET Active = 1").execute();
        }
    }

    private static void checkTable(String tableName, Connection con) throws SQLException, TableCreationFailedException {
        Menu.println("Checking if table " + tableName + " exists...");
        try {
            con.prepareStatement("SELECT * FROM " + tableName).execute();
            Menu.println("Found the " + tableName + " Table.");
        } catch (SQLSyntaxErrorException e) {
            Menu.println("Couldn't find table '" + tableName + "'. Creating a new one...");
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
                            "Active boolean," +
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
                case "archivedchannelname":
                    con.prepareStatement("CREATE TABLE archivedchannelname(" +
                            "ChannelNameVersionID int NOT NULL AUTO_INCREMENT," +
                            "VideoID varchar(255)," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (ChannelNameVersionID))").execute();
                    break;
                case "messages":
                    con.prepareStatement("CREATE TABLE messages(" +
                            "MessageId int NOT NULL AUTO_INCREMENT," +
                            "Message varchar(255)," +
                            "MessageRead BOOLEAN," +
                            "Time TIMESTAMP," +
                            "PRIMARY KEY (MessageId))").execute();
                    break;
                case "playlists":
                    con.prepareStatement("CREATE TABLE playlists(" +
                            "PlaylistId int NOT NULL AUTO_INCREMENT," +
                            "Playlist varchar(255)," +
                            "Views int," +
                            "PRIMARY KEY (PlaylistId))").execute();
                    break;
                default:
                    throw new TableCreationFailedException();
            }
            try {
                con.prepareStatement("SELECT * FROM " + tableName).executeQuery();
                Menu.println("Successfully created the new Table!");
            } catch (SQLSyntaxErrorException ee) {
                ee.printStackTrace();
                throw new TableCreationFailedException();
            }
        }
    }

    private static boolean checkVersion() {
        try {
            File json = new File("latest.json");
            FileUtils.copyURLToFile(new URL("https://api.github.com/repos/Fabulierer/youtubearchive/releases/latest"),
                    json);
            Scanner scan = new Scanner(json);
            scan.useDelimiter(",");
            while (scan.hasNext()) {
                String read = scan.next();
                if (read.startsWith("\"tag_name\":")) {
                    String newest = read.replace("\"", "").
                            replace("tag_name:", "");
                    return ver.equals(newest);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

}
