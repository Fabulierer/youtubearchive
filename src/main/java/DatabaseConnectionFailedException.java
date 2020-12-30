public class DatabaseConnectionFailedException extends Exception {

    public DatabaseConnectionFailedException() {
        super("Something went wrong while trying to connect to the Database!");
    }

}
