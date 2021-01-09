import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public class Server {

    private static ServerSocket server;
    private static Socket client;
    private static Connection con;

    public static void startServer(Connection con) {
        Server.con = con;
        try {
            prepareServer(256);
            serverLoop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void prepareServer(int port) throws IOException {
        server = new ServerSocket(port);
        System.out.println("Server started! Waiting for client...");
        client = server.accept();
        System.out.println("Client with IP " + client.getInetAddress() + " connected!");
    }

    private static void serverLoop() {
        DataInputStream input;
        DataOutputStream output;
        try {
            input = new DataInputStream(client.getInputStream());
            output = new DataOutputStream(client.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        while (!server.isClosed() || client.isConnected()) {
            String command;
            try {
                command = input.readUTF();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            String[] args = command.split(";/");
            switch (args[0]) {
                case "?status": {
                    try {
                        ResultSet rs = con.prepareStatement("SELECT VideoTitle," +
                                "ChannelName," +
                                "LastChecked," +
                                "(SELECT COUNT(y.VideoID) - 1 FROM archivedaudio y WHERE x.VideoID = y.VideoID)," +
                                "(SELECT COUNT(y.VideoID) - 1 FROM archiveddescription y WHERE x.VideoID = y.VideoID)," +
                                "(SELECT COUNT(y.VideoID) - 1 FROM archivedtags y WHERE x.VideoID = y.VideoID)," +
                                "(SELECT COUNT(y.VideoID) - 1 FROM archivedthumbnail y WHERE x.VideoID = y.VideoID)," +
                                "(SELECT COUNT(y.VideoID) - 1 FROM archivedtitle y WHERE x.VideoID = y.VideoID)," +
                                "(SELECT COUNT(y.VideoID) - 1 FROM archivedvideo y WHERE x.VideoID = y.VideoID) " +
                                "FROM videolist x").executeQuery();
                        StringBuilder answer = new StringBuilder("+status;/");
                        while (rs.next()) {
                            for (int i = 1; i <= 9; i++) {
                                answer.append(rs.getString(i)).append(";");
                            }
                            answer.append("/");
                        }
                        output.writeUTF(answer.toString());
                    } catch (SQLException | IOException e) {
                        e.printStackTrace();
                        try {
                            output.writeUTF("-status;/" + e.toString());
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                    break;
                }
                case "?messages": {
                    try {
                        ResultSet rs = con.prepareStatement("SELECT Time, Message, MessageRead FROM messages ORDER BY Time DESC").executeQuery();
                        StringBuilder answer = new StringBuilder("+messages;/");
                        while (rs.next()) {
                            for (int i = 1; i <= 3; i++) {
                                answer.append(rs.getString(i)).append(";");
                            }
                            answer.append("/");
                        }
                        output.writeUTF(answer.toString());
                        con.prepareStatement("UPDATE messages SET MessageRead = 1 WHERE MessageRead = 0").execute();
                    } catch (SQLException | IOException e) {
                        e.printStackTrace();
                        try {
                            output.writeUTF("-messages;/" + e.toString());
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                    break;
                }
                case "?clearmessages": {
                    try {
                        con.prepareStatement("DELETE FROM messages WHERE MessageRead = 1").execute();
                        output.writeUTF("+clearmessages");
                    } catch (SQLException | IOException e) {
                        e.printStackTrace();
                        try {
                            output.writeUTF(e.toString());
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                    break;
                }
                case "?add": {
                    if (args.length == 3) {
                        try {
                            switch (args[1]) {
                                case "v": {
                                    AddVideo.addVideo(args[2], con);
                                }
                                case "p": {
                                    AddVideo.addPlaylist(args[2], con);
                                }
                            }
                            output.writeUTF("+add");
                        } catch (YoutubeException | VideoCodecNotFoundException | SQLException | IOException e) {
                            e.printStackTrace();
                            try {
                                output.writeUTF("-add;/" + e.toString());
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                        }
                    } else {
                        try {
                            output.writeUTF("-add;/Not enough parameters!");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
                case "?resource": {
                    if (args.length == 4) {
                        try {
                            String type = args[1];
                            String id = args[2];
                            int version = Integer.parseInt(args[3]);
                            Collection<File> files = FileUtils.listFiles(new File("./storage/" + id + "/" + type + "/"), null, false);
                            String fileExtension = ((File) files.toArray()[0]).getName().split(".")[1];
                            File toSend = new File("./storage/" + id + "/" + type + "/" + version + "." + fileExtension);

                            output.writeUTF("+resource;/");

                            BufferedOutputStream bufferedOutput = new BufferedOutputStream(client.getOutputStream());
                            bufferedOutput.write(FileUtils.readFileToByteArray(toSend));
                            output.writeUTF(";/END");
                            break;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            output.writeUTF("-resource;/Not enough parameters!");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
                case "?update": {
                    Thread t = new Thread(() -> {
                        try {
                            if (args.length == 1) {
                                ResultSet rs = con.prepareStatement("SELECT COUNT(*) from videolist").executeQuery();
                                rs.next();
                                int videos = rs.getInt(1);
                                rs = con.prepareStatement("SELECT VideoID from videolist").executeQuery();
                                output.writeUTF("+update;/" + videos);
                                while (rs.next()) {
                                    UpdateVideo.checkVideo(rs.getString(1), con);
                                    output.writeUTF("+update;/" + rs.getRow());
                                }
                            } else {
                                output.writeUTF("+update;/1");
                                UpdateVideo.checkVideo(args[1], con);
                                output.writeUTF("+update;/1");
                            }
                        } catch (SQLException | IOException e) {
                            e.printStackTrace();
                            try {
                                output.writeUTF("-update;/" + e.toString());
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                        }
                    });
                    t.start();
                    break;
                }
                case "?space": {
                    File[] computerDrives = File.listRoots();
                    try {
                        output.writeUTF("+space;/" + computerDrives[Main.drive].getFreeSpace() + ";/" +
                                computerDrives[Main.drive].getTotalSpace());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }

            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

    }
}
