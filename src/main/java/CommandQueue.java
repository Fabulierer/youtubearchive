public class CommandQueue {

    private Node head;
    private int limit;

    public CommandQueue(int limit) {
        this.limit = limit;
    }

    public void addCmd(String cmd) throws QueueLimitReachedException {
        if (countNodes() > limit) throw new QueueLimitReachedException(limit);

        Node node = new Node(cmd);
        if (head == null) {
            head = node;
        } else {
            Node lastNode = head.getLastNode();
            lastNode.setNextNode(node);
        }
    }

    public String getNextCmd() {
        String cmd = head.getCmd();
        head = head.getNextNode();
        return cmd;
    }

    public boolean hasNextCmd() {
        return head != null;
    }

    public int countNodes() {
        return head == null ? 0 : head.followingNodes();
    }

    private class Node {

        private Node nextNode;
        private String cmd;

        public Node(String cmd) {
            this.cmd = cmd;
        }

        public Node getNextNode() {
            return nextNode;
        }

        public String getCmd() {
            return cmd;
        }

        public void setNextNode(Node nextNode) {
            this.nextNode = nextNode;
        }

        public int followingNodes() {
            if (nextNode == null) {
                return 1;
            } else {
                return 1 + nextNode.followingNodes();
            }
        }

        public Node getLastNode() {
            if (nextNode == null) return this;
            else return nextNode.getLastNode();
        }

    }

}
