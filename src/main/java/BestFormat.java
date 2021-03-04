import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.formats.AudioFormat;
import com.github.kiulian.downloader.model.formats.AudioVideoFormat;
import com.github.kiulian.downloader.model.formats.Format;
import com.github.kiulian.downloader.model.formats.VideoFormat;
import com.github.kiulian.downloader.model.quality.AudioQuality;
import com.github.kiulian.downloader.model.quality.VideoQuality;

import java.util.List;

public class BestFormat {

    public static Integer[] getFormats(YoutubeVideo v) throws VideoCodecNotFoundException {
        List<AudioVideoFormat> audioVideo = v.videoWithAudioFormats();
        List<VideoFormat> video = v.videoFormats();
        List<AudioFormat> audio = v.audioFormats();

        VideoQuality bestVideoQuality = VideoQuality.noVideo;
        AudioQuality bestAudioQuality = AudioQuality.noAudio;
        VideoQuality worstVideoQuality = VideoQuality.hd2880p;
        int videoQualityRating = 0;

        Format bestVideo = null;
        Format bestAudio = null;
        Format worstVideoAudio = null;

        for (AudioVideoFormat audioVideoFormat : audioVideo) {
            if (bestVideo == null) {
                bestVideo = audioVideoFormat;
                bestVideoQuality = audioVideoFormat.videoQuality();
                videoQualityRating = rateVideoQuality(audioVideoFormat);
            } else if (audioVideoFormat.videoQuality().compareTo(bestVideoQuality) < 0) {
                bestVideo = audioVideoFormat;
                bestVideoQuality = audioVideoFormat.videoQuality();
                videoQualityRating = rateVideoQuality(audioVideoFormat);
            } else if (audioVideoFormat.videoQuality().compareTo(bestVideoQuality) == 0 &&
                    rateVideoQuality(audioVideoFormat) > videoQualityRating ) {
                bestVideo = audioVideoFormat;
                bestVideoQuality = audioVideoFormat.videoQuality();
                videoQualityRating = rateVideoQuality(audioVideoFormat);
            }

            if (bestAudio == null) {
                bestAudio = audioVideoFormat;
                bestAudioQuality = audioVideoFormat.audioQuality();
            } else if (audioVideoFormat.audioQuality().compareTo(bestAudioQuality) < 0) {
                bestAudio = audioVideoFormat;
                bestAudioQuality = audioVideoFormat.audioQuality();
            }

            if (audioVideoFormat.videoQuality().compareTo(worstVideoQuality) > 0) {
                worstVideoAudio = audioVideoFormat;
                worstVideoQuality = audioVideoFormat.videoQuality();
            }
        }

        for (VideoFormat videoFormat : video) {
            if (bestVideo == null) {
                bestVideo = videoFormat;
                bestVideoQuality = videoFormat.videoQuality();
                videoQualityRating = rateVideoQuality(videoFormat);
            } else if (videoFormat.videoQuality().compareTo(bestVideoQuality) < 0) {
                bestVideo = videoFormat;
                bestVideoQuality = videoFormat.videoQuality();
                videoQualityRating = rateVideoQuality(videoFormat);
            } else if (videoFormat.videoQuality().compareTo(bestVideoQuality) == 0 &&
                    rateVideoQuality(videoFormat) > videoQualityRating ) {
                bestVideo = videoFormat;
                bestVideoQuality = videoFormat.videoQuality();
                videoQualityRating = rateVideoQuality(videoFormat);
            }
        }

        for (AudioFormat audioFormat : audio) {
            if (bestAudio == null) {
                bestAudio = audioFormat;
                bestAudioQuality = audioFormat.audioQuality();
            } else if (audioFormat.audioQuality().compareTo(bestAudioQuality) < 0) {
                bestAudio = audioFormat;
                bestAudioQuality = audioFormat.audioQuality();
            }
        }

        assert bestVideo != null;
        assert bestAudio != null;
        assert worstVideoAudio != null;
        return new Integer[]{bestVideo.itag().id(), bestAudio.itag().id(), worstVideoAudio.itag().id()};
    }

    private static int rateVideoQuality(AudioVideoFormat v) throws VideoCodecNotFoundException {
        return mimeTypeToRating(v.mimeType());
    }

    private static int rateVideoQuality(VideoFormat v) throws VideoCodecNotFoundException {
        return mimeTypeToRating(v.mimeType());
    }

    private static int mimeTypeToRating(String s) throws VideoCodecNotFoundException {
        if (s.contains("webm")) return 5;
        if (s.contains("mp4")) return 4;
        if (s.contains("flv")) return 2;
        if (s.contains("hls")) return 3;
        if (s.contains("3gp")) return 1;
        throw new VideoCodecNotFoundException(s);
    }

}
