import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.VideoDetails;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.playlist.PlaylistVideoDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class AddVideo {

    public static void addPlaylist(String id, Connection con) throws YoutubeException, VideoCodecNotFoundException, SQLException {
        YoutubeDownloader downloader = new YoutubeDownloader();
        List<PlaylistVideoDetails> pl = downloader.getPlaylist(id).videos();
        for (PlaylistVideoDetails playlistVideoDetails : pl) {
            addVideo(playlistVideoDetails.videoId(), con);
        }
    }

    public static void addVideo(String id, Connection con) throws SQLException, VideoCodecNotFoundException, YoutubeException {
        if (!con.prepareStatement("SELECT VideoID FROM videolist WHERE VideoID = '" + id + "'").executeQuery().next()) {
            YoutubeDownloader downloader = new YoutubeDownloader();
            YoutubeVideo v = downloader.getVideo(id);
            VideoDetails details = v.details();
            System.out.println("Adding the video: " + details.title());
            Integer[] formats = BestFormat.getFormats(v);
            PreparedStatement ps = con.prepareStatement("INSERT INTO videolist " +
                    "VALUES((?)," +
                    "(?)," +
                    "(?)," +
                    "(?)," +
                    "(?)," +
                    "(?)," +
                    "(?))");
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
