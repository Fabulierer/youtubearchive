public class WrongSettingsFileException extends Exception {

    public WrongSettingsFileException() {
        super("Something went wrong while trying to load the settings!");
    }

}
