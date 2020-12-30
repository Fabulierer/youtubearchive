public class FileDownloadFailedException extends Exception {

    public FileDownloadFailedException() {
        super("An error occurred while trying to copy a newly downloaded Version of a Video.");
    }

}
