import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.formats.Format;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class UpdateVideo {

    public static Timer t;

    public static void scheduleUpdate(final Connection con, int hours) {
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                System.out.println("Starting Scheduled Update!");
                checkAll(con);
                System.out.print("Scheduled Update complete!\nYoutubeArchive> ");
            }
        };
        t = new Timer();
        t.scheduleAtFixedRate(tt, 0L, 3600000L * hours);

    }

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
            PreparedStatement ps = con.prepareStatement("SELECT VideoItag, AudioItag FROM videolist WHERE VideoID = (?)");
            ps.setString(1, videoId);
            checkVideoFile(con, v);
            ResultSet rs = ps.executeQuery();
            rs.next();
            if (!rs.getString(1).equals(rs.getString(2))) checkAudioFile(con, v);
            checkDescription(con, v);
            checkThumbnail(con, v);
            checkTitle(con, v);
            checkTags(con, v);
            ps = con.prepareStatement("UPDATE videolist SET lastchecked = (?) WHERE VideoID = (?)");
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setString(2, videoId);
            ps.execute();
        } catch (YoutubeException e) {
            Main.sendMessage(con, "Video with VideoID " + videoId + " couldn't be found. Did it get deleted?");
        } catch (VideoNotInDatabaseException | SQLException e) {
            e.printStackTrace();
        }
    }

    private static void checkFile(String format, String extension, String videoId, Connection con, Method downloadFile, boolean message)
            throws IOException, FileDownloadFailedException, SQLException, InvocationTargetException, IllegalAccessException {
        // Download the new File (just by using the given Method)
        File newFile = (File) downloadFile.invoke(null);

        // Get the newest VersionID
        // All in all, the statement selects from the table of the given format from the VersionIDs the newest VersionID.
        //noinspection SqlResolve Disable inspection here because table will have "Time" column 100%
        PreparedStatement ps = con.prepareStatement("SELECT (?) FROM (?) WHERE (?) = (?) ORDER BY `Time` DESC");
        ps.setString(1, format + "VersionID");
        ps.setString(2, format.toLowerCase() + "list");
        ps.setString(3, format + "ID");
        ps.setString(4, videoId);

        // Get information out of Result
        ResultSet rs = ps.executeQuery();
        boolean firstSave = !rs.next(); // If this is the first download, there will be no entry in the result set
        boolean archiveFile = false;
        if (!firstSave) {
            // If this is not the first download, this part will check if the two files (newest archived file and
            // new download) are identical

            // Get newest archived File
            File newestArchive = new File("./storage/" + videoId + "/" + format.toLowerCase() + "/"
                    + rs.getString(1) + "." + extension);

            // Check if files are identical, if no, archiveFile = true
            archiveFile = !FileUtils.contentEquals(newFile, newestArchive);
        }

        if (firstSave || archiveFile) {
            // Save new version

            // Add new entry in database
            //noinspection SqlResolve
            ps = con.prepareStatement("INSERT INTO archived" + format.toLowerCase() + " VALUES(" +
                    "NULL," +
                    "(?)," +
                    "(?))");
            ps.setString(1, videoId);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.execute();

            // Get the VersionID for the new entry
            //noinspection SqlResolve
            ps = con.prepareStatement("SELECT (?) FROM (?) WHERE (?) = (?) ORDER BY `Time` DESC");
            ps.setString(1, format + "VersionID");
            ps.setString(2, format.toLowerCase() + "list");
            ps.setString(3, format + "ID");
            ps.setString(4, videoId);
            rs = ps.executeQuery();
            rs.next();
            int versionId = rs.getInt(1);

            // Archive the file
            File copyTarget = new File("./storage/" + videoId + "/" + format.toLowerCase() + "/" +
                    versionId + "." + extension);
            if (!newFile.renameTo(copyTarget)) {
                throw new FileDownloadFailedException();
            }

            if (message) {
                Main.sendMessage(con, "+change;" + videoId + ";" + format + ";" + firstSave);
            }
        }
        // Delete temp file
        //noinspection ResultOfMethodCallIgnored
        newFile.delete();
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
            boolean firstSave = false;
            if (rs.next()) {
                versionId = rs.getString(1);
                File newestBackup = new File("./storage/" + v.details().videoId() + "/video/"
                        + versionId + "." + findAudioVideoFormatByMimeType(v.findFormatByItag(itag).mimeType()));
                videoChanged = !FileUtils.contentEquals(newestBackup, downloadedFile);
            } else {
                firstSave = true;
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
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
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
                if (!firstSave) {
                    if (v.details().title().length() >= 20) {
                        Main.sendMessage(con, "Video \"" + v.details().title().substring(0, 20) + "\" changed!");
                    } else {
                        Main.sendMessage(con, "Video \"" + v.details().title() + "\" changed!");
                    }
                }
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
            boolean firstSave = false;
            if (rs.next()) {
                versionId = rs.getString(1);
                File newestBackup = new File("./storage/" + v.details().videoId() + "/audio/"
                        + versionId + "." + findAudioVideoFormatByMimeType(v.findFormatByItag(itag).mimeType()));
                audioChanged = !FileUtils.contentEquals(newestBackup, downloadedFile);
            } else {
                firstSave = true;
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
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
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
                System.out.println(copyTarget.getAbsolutePath());
                System.out.println(downloadedFile.getAbsolutePath());
                if (!downloadedFile.renameTo(copyTarget)) {
                    throw new FileDownloadFailedException();
                }
                System.out.println("Successfully archived the audio!");
                if (!firstSave) {
                    if (v.details().title().length() >= 20) {
                        Main.sendMessage(con, "Audio of video \"" + v.details().title().substring(0, 20) + "\" changed!");
                    } else {
                        Main.sendMessage(con, "Audio of video \"" + v.details().title() + "\" changed!");
                    }
                }
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
            PreparedStatement ps = con.prepareStatement("SELECT DescriptionVersionID FROM archiveddescription " +
                    "WHERE VideoID = (?) ORDER BY Time DESC");
            ps.setString(1, v.details().videoId());
            ResultSet rs = ps.executeQuery();
            boolean descriptionChanged;
            String versionId = "";
            File downloadedFile = new File("./temp.txt");
            FileUtils.writeStringToFile(downloadedFile, v.details().description(), "UTF-8");
            boolean firstSave = false;
            if (rs.next()) {
                versionId = rs.getString(1);
                File newestBackup = new File("./storage/" + v.details().videoId() + "/description/"
                        + versionId + ".txt");
                descriptionChanged = !FileUtils.contentEquals(newestBackup, downloadedFile);
            } else {
                firstSave = true;
                System.out.println("Creating directories: ./storage/" + v.details().videoId() + "/description/");
                Path storage = Paths.get("./storage/" + v.details().videoId() + "/description/");
                Files.createDirectories(storage);
                descriptionChanged = true;
            }
            if (descriptionChanged) {
                System.out.println("Description change detected! Archiving description...");
                ps = con.prepareStatement("INSERT INTO archiveddescription VALUES(" +
                        "NULL," +
                        "(?)," +
                        "(?))");
                ps.setString(1, v.details().videoId());
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ps.execute();
                ps = con.prepareStatement("SELECT DescriptionVersionID FROM archiveddescription " +
                        "WHERE VideoID = (?) ORDER BY Time DESC");
                ps.setString(1, v.details().videoId());
                rs = ps.executeQuery();
                rs.next();
                String newVersionId = rs.getString(1);
                if (versionId.equals(newVersionId)) {
                    throw new FileDownloadFailedException();
                }
                File copyTarget = new File("./storage/" + v.details().videoId() + "/description/" +
                        newVersionId + ".txt");
                if (!downloadedFile.renameTo(copyTarget)) {
                    throw new FileDownloadFailedException();
                }
                System.out.println("Successfully archived the description!");
                if (!firstSave) {
                    if (v.details().title().length() >= 20) {
                        Main.sendMessage(con, "Description of video \"" + v.details().title().substring(0, 20) + "\" changed!");
                    } else {
                        Main.sendMessage(con, "Description of video \"" + v.details().title() + "\" changed!");
                    }
                }
            } else {
                System.out.println("There were no description changes detected!");
            }
        } catch (SQLException |
                FileDownloadFailedException |
                IOException e) {
            e.printStackTrace();
        }
    }

    private static void checkThumbnail(Connection con, YoutubeVideo v) {
        try {
            List<String> th = v.details().thumbnails();
            File downloadedFile = new File("./temp.webp");
            FileUtils.copyURLToFile(new URL(th.get(th.size() - 1)), downloadedFile);
            PreparedStatement ps = con.prepareStatement("SELECT ThumbnailVersionID FROM archivedthumbnail " +
                    "WHERE VideoID = (?) ORDER BY Time DESC");
            ps.setString(1, v.details().videoId());
            ResultSet rs = ps.executeQuery();
            boolean thumbnailChanged;
            String versionId = "";
            boolean firstSave = false;
            if (rs.next()) {
                versionId = rs.getString(1);
                File newestBackup = new File("./storage/" + v.details().videoId() + "/thumbnail/"
                        + versionId + ".webp");
                thumbnailChanged = !FileUtils.contentEquals(newestBackup, downloadedFile);
            } else {
                firstSave = true;
                System.out.println("Creating directories: ./storage/" + v.details().videoId() + "/thumbnail/");
                Path storage = Paths.get("./storage/" + v.details().videoId() + "/thumbnail/");
                Files.createDirectories(storage);
                thumbnailChanged = true;
            }
            if (thumbnailChanged) {
                System.out.println("Thumbnail change detected! Thumbnail audio...");
                ps = con.prepareStatement("INSERT INTO archivedthumbnail VALUES(" +
                        "NULL," +
                        "(?)," +
                        "(?))");
                ps.setString(1, v.details().videoId());
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ps.execute();
                ps = con.prepareStatement("SELECT ThumbnailVersionID FROM archivedthumbnail " +
                        "WHERE VideoID = (?) ORDER BY Time DESC");
                ps.setString(1, v.details().videoId());
                rs = ps.executeQuery();
                rs.next();
                String newVersionId = rs.getString(1);
                if (versionId.equals(newVersionId)) {
                    throw new FileDownloadFailedException();
                }
                File copyTarget = new File("./storage/" + v.details().videoId() + "/thumbnail/" +
                        newVersionId + ".webp");
                if (!downloadedFile.renameTo(copyTarget)) {
                    throw new FileDownloadFailedException();
                }
                System.out.println("Successfully archived the thumbnail!");
                if (!firstSave) {
                    if (v.details().title().length() >= 20) {
                        Main.sendMessage(con, "Thumbnail of video \"" + v.details().title().substring(0, 20) + "\" changed!");
                    } else {
                        Main.sendMessage(con, "Thumbnail of video \"" + v.details().title() + "\" changed!");
                    }
                }
            } else {
                System.out.println("There were no thumbnail changes detected!");
            }
        } catch (SQLException |
                FileDownloadFailedException |
                IOException e) {
            e.printStackTrace();
        }
    }

    private static void checkTitle(Connection con, YoutubeVideo v) {
        try {
            PreparedStatement ps = con.prepareStatement("SELECT TitleVersionID FROM archivedtitle " +
                    "WHERE VideoID = (?) ORDER BY Time DESC");
            ps.setString(1, v.details().videoId());
            ResultSet rs = ps.executeQuery();
            boolean titleChanged;
            String versionId = "";
            File downloadedFile = new File("./temp.txt");
            FileUtils.writeStringToFile(downloadedFile, v.details().title(), "UTF-8");
            boolean firstSave = false;
            if (rs.next()) {
                versionId = rs.getString(1);
                File newestBackup = new File("./storage/" + v.details().videoId() + "/title/"
                        + versionId + ".txt");
                titleChanged = !FileUtils.contentEquals(newestBackup, downloadedFile);
            } else {
                firstSave = true;
                System.out.println("Creating directories: ./storage/" + v.details().videoId() + "/title/");
                Path storage = Paths.get("./storage/" + v.details().videoId() + "/title/");
                Files.createDirectories(storage);
                titleChanged = true;
            }
            if (titleChanged) {
                System.out.println("Title change detected! Archiving title...");
                ps = con.prepareStatement("INSERT INTO archivedtitle VALUES(" +
                        "NULL," +
                        "(?)," +
                        "(?))");
                ps.setString(1, v.details().videoId());
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ps.execute();
                ps = con.prepareStatement("SELECT TitleVersionID FROM archivedtitle " +
                        "WHERE VideoID = (?) ORDER BY Time DESC");
                ps.setString(1, v.details().videoId());
                rs = ps.executeQuery();
                rs.next();
                String newVersionId = rs.getString(1);
                if (versionId.equals(newVersionId)) {
                    throw new FileDownloadFailedException();
                }
                File copyTarget = new File("./storage/" + v.details().videoId() + "/title/" +
                        newVersionId + ".txt");
                if (!downloadedFile.renameTo(copyTarget)) {
                    throw new FileDownloadFailedException();
                }
                System.out.println("Successfully archived the title!");
                if (!firstSave) {
                    if (v.details().title().length() >= 20) {
                        Main.sendMessage(con, "Title of video \"" + v.details().title().substring(0, 20) + "\" changed!");
                    } else {
                        Main.sendMessage(con, "Title of video \"" + v.details().title() + "\" changed!");
                    }
                }
            } else {
                System.out.println("There were no title changes detected!");
            }
        } catch (SQLException |
                FileDownloadFailedException |
                IOException e) {
            e.printStackTrace();
        }
    }

    private static void checkTags(Connection con, YoutubeVideo v) {
        try {
            PreparedStatement ps = con.prepareStatement("SELECT TagsVersionID FROM archivedtags " +
                    "WHERE VideoID = (?) ORDER BY Time DESC");
            ps.setString(1, v.details().videoId());
            ResultSet rs = ps.executeQuery();
            boolean tagsChanged;
            String versionId = "";
            File downloadedFile = new File("./temp.txt");
            StringBuilder tags = new StringBuilder();
            List<String> keywords = v.details().keywords();
            for (String keyword : keywords) {
                tags.append(keyword).append(",");
            }
            FileUtils.writeStringToFile(downloadedFile, tags.toString(), "UTF-8");
            boolean firstSave = false;
            if (rs.next()) {
                versionId = rs.getString(1);
                File newestBackup = new File("./storage/" + v.details().videoId() + "/tags/"
                        + versionId + ".txt");
                tagsChanged = !FileUtils.contentEquals(newestBackup, downloadedFile);
            } else {
                firstSave = true;
                System.out.println("Creating directories: ./storage/" + v.details().videoId() + "/tags/");
                Path storage = Paths.get("./storage/" + v.details().videoId() + "/tags/");
                Files.createDirectories(storage);
                tagsChanged = true;
            }
            if (tagsChanged) {
                System.out.println("Tags change detected! Archiving tags...");
                ps = con.prepareStatement("INSERT INTO archivedtags VALUES(" +
                        "NULL," +
                        "(?)," +
                        "(?))");
                ps.setString(1, v.details().videoId());
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ps.execute();
                ps = con.prepareStatement("SELECT TagsVersionID FROM archivedtags " +
                        "WHERE VideoID = (?) ORDER BY Time DESC");
                ps.setString(1, v.details().videoId());
                rs = ps.executeQuery();
                rs.next();
                String newVersionId = rs.getString(1);
                if (versionId.equals(newVersionId)) {
                    throw new FileDownloadFailedException();
                }
                File copyTarget = new File("./storage/" + v.details().videoId() + "/tags/" +
                        newVersionId + ".txt");
                if (!downloadedFile.renameTo(copyTarget)) {
                    throw new FileDownloadFailedException();
                }
                System.out.println("Successfully archived the tags!");
                if (!firstSave) {
                    if (v.details().title().length() >= 20) {
                        Main.sendMessage(con, "Tags of video \"" + v.details().title().substring(0, 20) + "\" changed!");
                    } else {
                        Main.sendMessage(con, "Tags of video \"" + v.details().title() + "\" changed!");
                    }
                }
            } else {
                System.out.println("There were no tag changes detected!");
            }
        } catch (SQLException |
                FileDownloadFailedException |
                IOException e) {
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

    public static String findAudioVideoFormatByMimeType(String mimeType) throws VideoCodecNotFoundException {
        if (mimeType.equals("audio/mp4; codecs=\"mp4a.40.2\"")) return "m4a";
        if (mimeType.contains("m4a")) return "m4a";
        if (mimeType.contains("webm")) return "webm";
        if (mimeType.contains("mp4")) return "mp4";
        if (mimeType.contains("flv")) return "flv";
        if (mimeType.contains("hls")) return "hls";
        if (mimeType.contains("3gp")) return "3gp";
        throw new VideoCodecNotFoundException(mimeType);
    }

}
