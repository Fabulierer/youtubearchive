import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.formats.Format;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

public class UpdateVideo {

    public static void checkAll(Connection con) {
        try {
            ResultSet rs = con.prepareStatement("SELECT VideoID FROM videolist").executeQuery();
            System.out.println("Checking every video.");
            int elements = 0;
            while (rs.next()) {
                elements++;
            }
            rs.beforeFirst();
            while (rs.next()) {
                System.out.println("Checking Video " + rs.getRow() + "/" + elements);
                checkVideo(rs.getString(1), con);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void checkVideo(String videoId, Connection con) {
        try {
            System.out.println("Checking Video with Video ID: " + videoId);
            YoutubeDownloader d = new YoutubeDownloader();
            YoutubeVideo v = d.getVideo(videoId);
            checkVideoFile(con, v);
            checkAudioFile(con, v);
            checkDescription(con, v);
            PreparedStatement ps = con.prepareStatement("UPDATE videolist SET lastchecked = (?) WHERE VideoID = (?)");
            ps.setTime(1, new Time(System.currentTimeMillis()));
            ps.setString(2, v.details().videoId());
            ps.execute();
        } catch (YoutubeException e) {
            Main.sendMessage(con, "Video with VideoID " + videoId + " couldn't be found. Did it get deleted?");
        } catch (VideoNotInDatabaseException | SQLException e) {
            e.printStackTrace();
        }
    }

    private static void checkVideoFile(Connection con, YoutubeVideo v) throws VideoNotInDatabaseException {
        try {
            PreparedStatement ps = con.prepareStatement("SELECT VideoItag FROM videolist " +
                    "WHERE VideoID = (?)");
            ps.setString(1, v.details().videoId());
            ResultSet rs = ps.executeQuery();
            int itag = downloadFile(v, rs);
            ps = con.prepareStatement("SELECT VideoVersionID FROM archivedvideo " +
                    "WHERE VideoID = (?) ORDER BY Time DESC");
            ps.setString(1, v.details().videoId());
            rs = ps.executeQuery();
            boolean videoChanged;
            String versionId = "";
            File downloadedFile = new File("./temp." + findAudioVideoFormatByMimeType(v.findFormatByItag(itag).mimeType()));
            if (rs.next()) {
                versionId = rs.getString(1);
                File newestBackup = new File("./storage/" + v.details().videoId() + "/video/"
                        + versionId + "." + findAudioVideoFormatByMimeType(v.findFormatByItag(itag).mimeType()));
                videoChanged = !FileUtils.contentEquals(newestBackup, downloadedFile);
            } else {
                System.out.println("Creating directories: ./storage/" + v.details().videoId() + "/video/");
                Path storage = Paths.get("./storage/" + v.details().videoId() + "/video/");
                Files.createDirectories(storage);
                videoChanged = true;
            }
            if (videoChanged) {
                System.out.println("Video change detected! Archiving video...");
                ps = con.prepareStatement("INSERT INTO archivedvideo VALUES(" +
                        "NULL," +
                        "(?)," +
                        "(?))");
                ps.setString(1, v.details().videoId());
                ps.setTime(2, new Time(System.currentTimeMillis()));
                ps.execute();
                ps = con.prepareStatement("SELECT VideoVersionID FROM archivedvideo " +
                        "WHERE VideoID = (?) ORDER BY Time DESC");
                ps.setString(1, v.details().videoId());
                rs = ps.executeQuery();

                rs.next();
                String newVersionId = rs.getString(1);
                if (versionId.equals(newVersionId)) {
                    throw new FileDownloadFailedException();
                }
                File copyTarget = new File("./storage/" + v.details().videoId() + "/video/" +
                        newVersionId + "." + findAudioVideoFormatByMimeType(v.findFormatByItag(itag).mimeType()));
                if (!downloadedFile.renameTo(copyTarget)) {
                    throw new FileDownloadFailedException();
                }
                System.out.println("Successfully archived the video!");
                Main.sendMessage(con, "Video with ID " + v.details().videoId() + " changed!");
            } else {
                System.out.println("There were no video changes detected!");
            }
        } catch (SQLException |
                VideoCodecNotFoundException |
                FileDownloadFailedException |
                IOException |
                YoutubeException e) {
            e.printStackTrace();
        }
    }

    private static void checkAudioFile(Connection con, YoutubeVideo v) throws VideoNotInDatabaseException {
        try {
            PreparedStatement ps = con.prepareStatement("SELECT AudioItag FROM videolist " +
                    "WHERE VideoID = (?)");
            ps.setString(1, v.details().videoId());
            ResultSet rs = ps.executeQuery();
            int itag = downloadFile(v, rs);
            ps = con.prepareStatement("SELECT AudioVersionID FROM archivedaudio " +
                    "WHERE VideoID = (?) ORDER BY Time DESC");
            ps.setString(1, v.details().videoId());
            rs = ps.executeQuery();
            boolean audioChanged;
            String versionId = "";
            File downloadedFile = new File("./temp." + findAudioVideoFormatByMimeType(v.findFormatByItag(itag).mimeType()));
            if (rs.next()) {
                versionId = rs.getString(1);
                File newestBackup = new File("./storage/" + v.details().videoId() + "/audio/"
                        + versionId + "." + findAudioVideoFormatByMimeType(v.findFormatByItag(itag).mimeType()));
                audioChanged = !FileUtils.contentEquals(newestBackup, downloadedFile);
            } else {
                System.out.println("Creating directories: ./storage/" + v.details().videoId() + "/audio/");
                Path storage = Paths.get("./storage/" + v.details().videoId() + "/audio/");
                Files.createDirectories(storage);
                audioChanged = true;
            }
            if (audioChanged) {
                System.out.println("Audio change detected! Archiving audio...");
                ps = con.prepareStatement("INSERT INTO archivedaudio VALUES(" +
                        "NULL," +
                        "(?)," +
                        "(?))");
                ps.setString(1, v.details().videoId());
                ps.setTime(2, new Time(System.currentTimeMillis()));
                ps.execute();
                ps = con.prepareStatement("SELECT AudioVersionID FROM archivedaudio " +
                        "WHERE VideoID = (?) ORDER BY Time DESC");
                ps.setString(1, v.details().videoId());
                rs = ps.executeQuery();
                rs.next();
                String newVersionId = rs.getString(1);
                if (versionId.equals(newVersionId)) {
                    throw new FileDownloadFailedException();
                }
                File copyTarget = new File("./storage/" + v.details().videoId() + "/audio/" +
                        newVersionId + "." + findAudioVideoFormatByMimeType(v.findFormatByItag(itag).mimeType()));
                if (!downloadedFile.renameTo(copyTarget)) {
                    throw new FileDownloadFailedException();
                }
                System.out.println("Successfully archived the audio!");
                Main.sendMessage(con, "Audio of video with ID " + v.details().videoId() + " changed!");
            } else {
                System.out.println("There were no audio changes detected!");
            }
        } catch (SQLException |
                VideoCodecNotFoundException |
                FileDownloadFailedException |
                IOException |
                YoutubeException e) {
            e.printStackTrace();
        }
    }

    private static void checkDescription(Connection con, YoutubeVideo v) {
        try {
            PreparedStatement ps = con.prepareStatement("SELECT Description FROM archiveddescription " +
                    "WHERE VideoID = (?) ORDER BY Time DESC");
            ps.setString(1, v.details().videoId());
            ResultSet rs = ps.executeQuery();

            boolean descriptionChanged;
            String oldDescription = ".";
            if (rs.next()) {
                oldDescription = rs.getString(1);
                descriptionChanged = !oldDescription.equals(v.details().description());
            } else {
                descriptionChanged = true;
            }
            if (descriptionChanged) {
                System.out.println("Description change detected! Archiving description...");
                ps = con.prepareStatement("INSERT INTO archiveddescription VALUES(" +
                        "NULL," +
                        "?," +
                        "(?)," +
                        "(?))");
                ps.setString(1, v.details().videoId());
                ps.setTime(2, new Time(System.currentTimeMillis()));
                ps.setString(3, v.details().description());
                ps.execute();
                rs = con.prepareStatement("SELECT Description FROM archiveddescription " +
                        "WHERE VideoID = '" + v.details().videoId() + "' ORDER BY Time DESC").executeQuery();
                rs.next();
                String newDescription = rs.getString(1);
                if (oldDescription.equals(newDescription)) {
                    throw new FileDownloadFailedException();
                }
                System.out.println("Successfully archived the description!");
                Main.sendMessage(con, "Description of video with ID " + v.details().videoId() + " changed!");
            } else {
                System.out.println("There were no description changes detected!");
            }
        } catch (SQLException |
                FileDownloadFailedException e) {
            e.printStackTrace();
        }
    }

    private static int downloadFile(YoutubeVideo v, ResultSet rs) throws SQLException, VideoNotInDatabaseException, IOException, YoutubeException {
        if (!rs.next()) throw new VideoNotInDatabaseException();
        int itag = Integer.parseInt(rs.getString(1));
        Format format = v.findFormatByItag(itag);
        System.out.println("Starting download. This may take a while...");
        v.download(format, new File("./"), "temp", true);
        System.out.println("Download complete!");
        return itag;
    }

    private static String findAudioVideoFormatByMimeType(String mimeType) throws VideoCodecNotFoundException {
        if (mimeType.contains("webm")) return "webm";
        if (mimeType.contains("mp4")) return "mp4";
        if (mimeType.contains("flv")) return "flv";
        if (mimeType.contains("hls")) return "hls";
        if (mimeType.contains("3gp")) return "3gp";
        throw new VideoCodecNotFoundException(mimeType);
    }

}
