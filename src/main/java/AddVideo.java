import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.VideoDetails;
import com.github.kiulian.downloader.model.YoutubeVideo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AddVideo {

    public static void addVideo(String id, Connection con) {
        try {
            if (!con.prepareStatement("SELECT VideoID FROM VideoList WHERE VideoID = '" + id + "'").executeQuery().next()){
                YoutubeDownloader downloader = new YoutubeDownloader();
                YoutubeVideo v = downloader.getVideo(id);
                VideoDetails details = v.details();
                System.out.println("Adding the video: " + details.title());
                Integer[] formats = BestFormat.getFormats(v);
                PreparedStatement ps = con.prepareStatement("INSERT INTO VideoList " +
                        "VALUES((?)," +
                        "(?)," +
                        "(?)," +
                        "NULL," +
                        "(?)," +
                        "(?))");
                ps.setString(1, id);
                ps.setString(2, details.title());
                ps.setString(3, details.author());
                ps.setInt(4, formats[0]);
                ps.setInt(5, formats[1]);
                ps.execute();
                if (con.prepareStatement("SELECT VideoID FROM VideoList WHERE VideoID = '" + id + "'").executeQuery().next()){
                    System.out.println("Successfully added the video!");
                } else {
                    System.out.println("An error occurred while trying to add the video!");
                }
            } else {
                System.out.println("Video already exists in Database!");
            }
        } catch (YoutubeException | SQLException | VideoCodecNotFoundException e) {
            e.printStackTrace();
        }

    }

}
