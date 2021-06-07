import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.VideoDetails;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.playlist.YoutubePlaylist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class AddVideo {

    public static void addPlaylist(String id, int minViews, Connection con) throws YoutubeException, SQLException {
        System.out.println("Adding to playlist table");
        //TODO check if playlist is already in table
        YoutubeDownloader ytdl = new YoutubeDownloader();
        YoutubePlaylist pl = ytdl.getPlaylist(id);
        String title = pl.details().title();
        PreparedStatement ps = con.prepareStatement("INSERT INTO playlists VALUES (NULL, (?), (?), (?))");
        ps.setString(1, title);
        ps.setString(2, id);
        ps.setInt(3, minViews);
        ps.execute();
    }

    public static void addChannel(String id, int minViews, Connection con) throws YoutubeException, SQLException {
        addPlaylist(new YoutubeDownloader().getChannelUploads(id).details().playlistId(), minViews, con);
    }

    public static void addVideo(String id, Connection con, boolean overwrite) throws SQLException, VideoCodecNotFoundException, YoutubeException {
        if (!con.prepareStatement("SELECT VideoID FROM videolist WHERE VideoID = '" + id + "'").executeQuery().next() || overwrite) {
            YoutubeDownloader downloader = new YoutubeDownloader();
            YoutubeVideo v = downloader.getVideo(id);
            VideoDetails details = v.details();
            System.out.println("Adding the video: " + details.title());
            Integer[] formats = BestFormat.getFormats(v);
            PreparedStatement ps = con.prepareStatement("REPLACE INTO videolist " +
                    "VALUES((?)," +
                    "(?)," +
                    "(?)," +
                    "(?)," +
                    "(?)," +
                    "(?)," +
                    "(?), " +
                    "1)");
            ps.setString(1, id);
            ps.setString(2, details.title());
            ps.setString(3, details.author());
            ps.setTimestamp(4, new Timestamp(1000));
            ps.setInt(5, formats[0]);
            ps.setInt(6, formats[1]);
            ps.setInt(7, formats[2]);
            ps.execute();
            if (con.prepareStatement("SELECT VideoID FROM videolist WHERE VideoID = '" + id + "'").executeQuery().next()) {
                System.out.println("Successfully added the video!");
            } else {
                System.out.println("An error occurred while trying to add the video!");
            }
        } else {
            System.out.println("Video already exists in Database!");
        }

    }

}
