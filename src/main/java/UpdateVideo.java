import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.formats.Format;
import com.github.kiulian.downloader.model.playlist.PlaylistVideoDetails;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

public class UpdateVideo {

    public static Timer t;

    public static void scheduleUpdate(final Connection con, int hours) {
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                Main.println("Starting Scheduled Update!");
                checkAll(con);
                Main.print("Scheduled Update complete!\nYoutubeArchive> ");
            }
        };
        t = new Timer();
        t.scheduleAtFixedRate(tt, 0L, 3600000L * hours);

    }

    public static void checkAll(Connection con) {
        try {
            // Update videolist (add playlists)
            Main.println("Updating videolist to include new videos found in playlists...");
            ResultSet rs = con.prepareStatement("SELECT Playlist, Views, Count(*) FROM playlists").executeQuery();
            YoutubeDownloader downloader = new YoutubeDownloader();
            for (int i = 0; rs.next(); i++) {
                try {
                    isYoutubeAvailable();
                    List<PlaylistVideoDetails> pl = downloader.getPlaylist(rs.getString(1)).videos();
                    for (int j = 0; j < pl.size(); j++) {
                        Main.println("Playlist: [" + i + "/" + rs.getInt(3) + "] (" + (int) (i * 10000.0 / rs.getInt(3)) / 100.0 + "%)" +
                                ", Video: [" + j + "/" + pl.size() + "] (" + (int) (j * 10000.0 / pl.size()) / 100.0 + "%)");
                        try {
                            String videoId = pl.get(j).videoId();
                            if (downloader.getVideo(videoId).details().viewCount() > rs.getInt(2)) {
                                AddVideo.addVideo(videoId, con);
                            } else {
                                Main.println("Video was skipped because it does not have the minimum amount of views required to be archived.");
                            }
                        } catch (SQLException | VideoCodecNotFoundException e) {
                            Main.println("Video with ID " + pl.get(j).videoId() + " is not available anymore.");
                            e.printStackTrace();
                        } catch (YoutubeException ytex) {
                            isYoutubeAvailable();
                            Main.println("Playlist: [" + i + "/" + rs.getInt(3) + "] (" + (int) (i * 10000.0 / rs.getInt(3)) / 100.0 + "%)" +
                                    ", Video: [" + j + "/" + pl.size() + "] (" + (int) (j * 10000.0 / pl.size()) / 100.0 + "%)");
                            try {
                                String videoId = pl.get(j).videoId();
                                if (downloader.getVideo(videoId).details().viewCount() > rs.getInt(2)) {
                                    AddVideo.addVideo(videoId, con);
                                } else {
                                    Main.println("Video was skipped because it does not have the minimum amount of views required to be archived.");
                                }
                            } catch (SQLException | VideoCodecNotFoundException | YoutubeException e) {
                                Main.println("Video with ID " + pl.get(j).videoId() + " is not available anymore.");
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (YoutubeException e) {
                    Main.println("Playlist with ID " + rs.getString(1) + " could not be found.");
                    e.printStackTrace();
                }
            }

            rs = con.prepareStatement("SELECT VideoID, Active FROM videolist").executeQuery();
            Main.println("Checking every video.");
            int elements = 0;
            while (rs.next()) {
                elements++;
            }
            rs.beforeFirst();
            ArrayList<String> brokenIds = new ArrayList<>();
            while (rs.next()) {
                if (rs.getInt(2) == 1) {
                    Main.println("Checking Video: [" + rs.getRow() + "/" + elements + "] (" + (int) (rs.getRow() * 10000.0 / elements) / 100.0 + "%)");
                    try {
                        checkVideo(rs.getString(1), con);
                    } catch (VideoCodecNotFoundException | YoutubeException | IOException ignored) {
                        Main.println("Video could not be found.");
                        // Second attempt, this time internet connection gets checked.
                        isYoutubeAvailable();
                        // When you get here, internet is confirmed to work.
                        try {
                            checkVideo(rs.getString(1), con); // Last attempt
                        } catch (VideoCodecNotFoundException | YoutubeException | IOException e) {
                            e.printStackTrace();
                            brokenIds.add(rs.getString(1));
                        }
                    }
                } else Main.println("Video skipped because it is not set active.");
            }
            Main.println(brokenIds.size() + " videos could not be found. Want to disable them? (Y/N)");
            Scanner scan = new Scanner(System.in);
            if (scan.next().equals("Y")) {
                for (int i = 0; i < brokenIds.size(); i++) {
                    PreparedStatement ps = con.prepareStatement("UPDATE videolist SET Active = 0 WHERE VideoID = (?)");
                    ps.setString(1, brokenIds.get(i));
                }
                Main.println("Not available videos are disabled!");
            }
            else {
                Main.println("Changed nothing about the enabled status of videos.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void checkVideo(String videoId, Connection con) throws VideoCodecNotFoundException, YoutubeException, IOException {
        try {
            Main.println("Checking Video with Video ID: " + videoId);
            YoutubeDownloader d = new YoutubeDownloader();
            YoutubeVideo v = d.getVideo(videoId);
            PreparedStatement ps = con.prepareStatement("SELECT VideoItag, AudioItag, LowVideoAudioItag FROM videolist WHERE VideoID = (?)");
            ps.setString(1, videoId);
            ResultSet rs = ps.executeQuery();
            rs.next();

            // LowVideoAudio download block
            int itagLowVideoAudio = rs.getInt(3);
            String extensionLowVideoAudio = findAudioVideoFormatByMimeType(v.findFormatByItag(itagLowVideoAudio).mimeType());
            checkFile("LowVideoAudio", extensionLowVideoAudio, videoId, con, new DownloadFormat() {
                @Override
                public File downloadFile(String videoId, int itag) throws IOException, YoutubeException {
                    return downloadYoutubeFile(videoId, itag);
                }
            }, new onFileUpdate() {
                @Override
                public void message(String format, String videoId) {
                    try {
                        // Video download block
                        int itagVideo = rs.getInt(1);
                        String extensionVideo = findAudioVideoFormatByMimeType(v.findFormatByItag(itagVideo).mimeType());
                        checkFile("Video", extensionVideo, videoId, con, new DownloadFormat() {
                            @Override
                            public File downloadFile(String videoId, int itag) throws IOException, YoutubeException {
                                return downloadYoutubeFile(videoId, itag);
                            }
                        }, new onFileUpdate() {
                            @Override
                            public void message(String format, String videoId) {
                                Main.sendMessage(con, "+change;" + videoId + ";" + format);
                            }
                        }, itagVideo);

                        // Audio download block
                        int itagAudio = rs.getInt(2);
                        String extensionAudio = findAudioVideoFormatByMimeType(v.findFormatByItag(itagAudio).mimeType());
                        checkFile("Audio", extensionAudio, videoId, con, new DownloadFormat() {
                            @Override
                            public File downloadFile(String videoId, int itag) throws IOException, YoutubeException {
                                return downloadYoutubeFile(videoId, itag);
                            }
                        }, new onFileUpdate() {
                            @Override
                            public void message(String format, String videoId) {
                                Main.sendMessage(con, "+change;" + videoId + ";" + format);
                            }
                        }, itagAudio);
                    } catch (IOException | SQLException | InvocationTargetException | IllegalAccessException | YoutubeException | VideoCodecNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }, itagLowVideoAudio);

            // Description download block
            checkFile("Description", "txt", videoId, con, new DownloadFormat() {
                @Override
                public File downloadFile(String videoId, int itag) throws IOException, YoutubeException {
                    File downloadedFile = new File("./temp.txt");
                    FileUtils.writeStringToFile(downloadedFile, v.details().description(), "UTF-8");
                    return downloadedFile;
                }
            }, new onFileUpdate() {
                @Override
                public void message(String format, String videoId) {
                    Main.sendMessage(con, "+change;" + videoId + ";" + format);
                }
            }, -1);

            // Thumbnail download block
            checkFile("Thumbnail", "webp", videoId, con, new DownloadFormat() {
                @Override
                public File downloadFile(String videoId, int itag) throws IOException, YoutubeException {
                    File downloadedFile = new File("./temp.webp");
                    List<String> th = v.details().thumbnails();
                    // Always takes the last entry in the thumbnails list since that is usually the one with the highest quality
                    FileUtils.copyURLToFile(new URL(th.get(th.size() - 1)), downloadedFile);
                    return downloadedFile;
                }
            }, new onFileUpdate() {
                // Thumbnails randomly change a lot without actual optical difference, so there will be no alerts when detected
                @Override
                public void message(String format, String videoId) {}
            }, -1);

            // Thumbnail download block
            checkFile("ChannelName", "txt", videoId, con, new DownloadFormat() {
                @Override
                public File downloadFile(String videoId, int itag) throws IOException, YoutubeException {
                    File downloadedFile = new File("./temp.txt");
                    FileUtils.writeStringToFile(downloadedFile, v.details().author(), "UTF-8");
                    return downloadedFile;
                }
            }, new onFileUpdate() {
                // Changes in name of channel can cause messages to be full of just channel name changes, so they are not reported.
                @Override
                public void message(String format, String videoId) {}
            }, -1);


            // Title download block
            checkFile("Title", "txt", videoId, con, new DownloadFormat() {
                @Override
                public File downloadFile(String videoId, int itag) throws IOException, YoutubeException {
                    File downloadedFile = new File("./temp.txt");
                    FileUtils.writeStringToFile(downloadedFile, v.details().title(), "UTF-8");
                    return downloadedFile;
                }
            }, new onFileUpdate() {
                @Override
                public void message(String format, String videoId) {
                    Main.sendMessage(con, "+change;" + videoId + ";" + format);
                }
            }, -1);

            // Tags download block
            checkFile("Tags", "txt", videoId, con, new DownloadFormat() {
                @Override
                public File downloadFile(String videoId, int itag) throws IOException, YoutubeException {
                    File downloadedFile = new File("./temp.txt");
                    List<String> tagsList = v.details().keywords();
                    StringBuilder tags = new StringBuilder();
                    for (String s : tagsList) {
                        tags.append(s).append(",");
                    }
                    FileUtils.writeStringToFile(downloadedFile, tags.toString(), "UTF-8");
                    return downloadedFile;
                }
            }, new onFileUpdate() {
                @Override
                public void message(String format, String videoId) {
                    Main.sendMessage(con, "+change;" + videoId + ";" + format);
                }
            }, -1);


            ps = con.prepareStatement("UPDATE videolist SET lastchecked = (?) WHERE VideoID = (?)");
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setString(2, videoId);
            ps.execute();
        } catch (SQLException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static File downloadYoutubeFile(String videoId, int itag) throws YoutubeException, IOException {
        YoutubeDownloader dl = new YoutubeDownloader();
        YoutubeVideo v = dl.getVideo(videoId);
        try {
            Format format = v.findFormatByItag(itag);
            String extension = findAudioVideoFormatByMimeType(String.valueOf(v.findFormatByItag(itag).mimeType()));
            v.download(format, new File("./"), "temp", true);
            return new File("./temp." + extension);
        } catch (VideoCodecNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param format The format the method should download
     * @param extension The file extension used for the downloaded file
     * @param videoId The Video ID of the video the file comes from
     * @param con Connection to a database
     * @param downloadFile Method for downloading the file
     * @param onFileUpdate Method for what should happen when the file actually gets an update
     * @param itag If needed, add itag. Gives info to <code>downloadFile()</code>
     * @throws IOException IOExceptions are thrown when the file download fails for some reason (no internet or video gone)
     * @throws SQLException SQLExceptions are thrown when something goes wrong with the SQL Server. Probably connection failure
     * @throws InvocationTargetException This Exception gets thrown when the <code>downloadFile</code> Method fails to get executed
     * @throws IllegalAccessException This Exception gets thrown when the "downloadFile" Method fails to get executed
     * @throws YoutubeException Download fails, probably offline or video removed
     */
    private static void checkFile(String format, String extension, String videoId, Connection con,
                                  DownloadFormat downloadFile, onFileUpdate onFileUpdate, int itag)
            throws IOException, SQLException, InvocationTargetException, IllegalAccessException, YoutubeException {
        // Download the new File (just by using the given Method)
        File newFile = downloadFile.downloadFile(videoId, itag);
        // Get the newest VersionID
        // All in all, the statement selects from the table of the given format from the VersionIDs the newest VersionID.
        //noinspection SqlResolve Disable inspection here because table will have "Time" column 100%
        PreparedStatement ps = con.prepareStatement("SELECT " + format + "VersionID" + " FROM archived" + format.toLowerCase() +
                " WHERE VideoID = (?) ORDER BY `Time` DESC");
        ps.setString(1, videoId);

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
        } else {

            // Create folders if not already done so
            Path storage = Paths.get("./storage/" + videoId + "/" + format.toLowerCase());
            Files.createDirectories(storage);
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
            ps = con.prepareStatement("SELECT " + format + "VersionID FROM archived" + format.toLowerCase() +
                    " WHERE VideoID = (?) ORDER BY `Time` DESC");
            ps.setString(1, videoId);
            rs = ps.executeQuery();
            rs.next();
            int versionId = rs.getInt(1);

            // Archive the file
            File copyTarget = new File("./storage/" + videoId + "/" + format.toLowerCase() + "/" +
                    versionId + "." + extension);
            FileUtils.copyFile(newFile, copyTarget);
            onFileUpdate.message(format, videoId);
        }
        // Delete temp file
        //noinspection ResultOfMethodCallIgnored
        newFile.delete();
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

    /**
     * @return tries to check "Me at the Zoo", continues to do so until video is found.
     */
    private static void isYoutubeAvailable() {
        for (int i = 0; !tryYoutubeDownload(); i++) {
            Main.println("Connection seems to be unstable or \"Me at the Zoo\" is offline. Attempt number " + i);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean tryYoutubeDownload() {
        YoutubeDownloader d = new YoutubeDownloader();
        File file = new File("./");
        try {
            YoutubeVideo v = d.getVideo("jNQXAC9IVRw");
            Format f = v.formats().get(0);
            v.download(f, file, "temp", true);
        } catch (YoutubeException | IOException e) {
            return false;
        } finally {
            file.delete();
        }
        return true;
    }

}
