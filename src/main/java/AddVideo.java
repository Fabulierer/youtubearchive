import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.VideoDetails;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.formats.Format;

import java.sql.Connection;
import java.sql.SQLException;

public class AddVideo {

    public static void addVideo(String id, Connection con) {
        try {
            if (!con.prepareStatement("SELECT VideoID FROM VideoList WHERE VideoID = '" + id + "'").executeQuery().next()){
                YoutubeDownloader downloader = new YoutubeDownloader();
                YoutubeVideo v = downloader.getVideo(id);
                VideoDetails details = v.details();
                System.out.println("Adding the video: " + details.title());
                Integer[] formate = BestFormat.getFormats(v);
                con.prepareStatement("INSERT INTO VideoList " +
                        "VALUES('" + id + "'," +
                        "'" + details.title() + "'," +
                        "'" + details.author() + "'," +
                        "NULL," +
                        "'" + formate[0] + "'," +
                        "'" + formate[1] + "')").execute();
                if (con.prepareStatement("SELECT VideoID FROM VideoList WHERE VideoID = '" + id + "'").executeQuery().next()){
                    System.out.println("Successfully added the video!");
                } else {
                    System.out.println("An error occurred while trying to add the video!");
                }
            } else {
                System.out.println("Video already exists in Database!");
            }
        } catch (YoutubeException e) {
            System.out.println(id);
            e.printStackTrace();
        } catch (VideoCodecNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

    }

}
