public class QueueLimitReachedException extends Exception {

    public QueueLimitReachedException(int limit) {
        super("The Queue is full! The maximum amount of commands that can be in the queue is " + limit + "!");
    }

}
