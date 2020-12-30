public class VideoCodecNotFoundException extends Exception {

    public VideoCodecNotFoundException(String mime) {
        super("Codec couldn't be found: " + mime);
    }

}
