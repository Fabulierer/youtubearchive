import com.github.kiulian.downloader.YoutubeException;

import java.io.File;
import java.io.IOException;

public interface DownloadFormat {
    File downloadFile(String videoId, int itag) throws IOException, YoutubeException;
}
