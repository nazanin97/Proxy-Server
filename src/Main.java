import java.io.*;
import java.net.*;

public class Main {

    public static void main(String[] args) {

        //listen to client's requests
        Thread listen = new Thread(() -> {
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(8080);
                while (true) {
                    Socket client = serverSocket.accept();
                    createRequestHandlerThread(client);
                    System.out.println("connected");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listen.start();
    }

    private static void createRequestHandlerThread(Socket client) {
        new Thread(() -> handleClientRequest(client)).start();
    }

    private static void handleClientRequest(Socket client) {

        boolean block = false;
        File file = new File("./groups");
        DataInputStream in;
        BufferedInputStream markedStreamFromClient;
        try {
            markedStreamFromClient = new BufferedInputStream(client.getInputStream());
            markedStreamFromClient.mark(10);
            in = new DataInputStream(markedStreamFromClient);

            String msgContent = "";
            int k;
            do {
                k = in.read();
                msgContent = msgContent + (char) k;
                if (msgContent.contains(System.getProperty("line.separator")))
                    k = -1;
            } while (k != -1);

            String[] result = msgContent.split(System.lineSeparator());
            String destinationURL = result[0];
            destinationURL = destinationURL.replace("GET ", "");
            destinationURL = destinationURL.replace("/ HTTP/1.1", "");

            System.out.println("destination URL: " + destinationURL);
            PrintWriter out = new PrintWriter(client.getOutputStream());
            URL u1 = null;
            try {
                u1 = new URL(destinationURL);
            }catch (MalformedURLException e1){
                System.out.println(e1);
            }

            //find url in text files
            for (File child: file.listFiles()) {
                boolean flag = false;
                if (child.getName().equals(".DS_Store"))
                    continue;
                try {
                    BufferedReader br = new BufferedReader(new FileReader(child));
                    String st;
                    while ((st = br.readLine()) != null) {
                        URL u2 = new URL(st);
                        if (u1 != null && u1.equals(u2)) {
                            block = true;
                            flag = true;
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (flag)
                    break;
            }
            if (block){
                String responseToClient =  destinationURL + " is forbidden";
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: text/html");
                out.println("Content-Length: " + responseToClient.length());
                out.println();
                out.println(responseToClient);
                out.flush();
            } else {
                try {
                    InetAddress dest  = InetAddress.getByName(new URL(destinationURL).getHost());
                    Socket server = new Socket(dest.getHostAddress(), 80);

                    // Sending message to server
                    OutputStream outputToServer = server.getOutputStream();
                    markedStreamFromClient.reset();
                    copy(markedStreamFromClient, outputToServer, false);

                    // Receiving message from server
                    InputStream streamFromServer = server.getInputStream();
                    copy(streamFromServer, client.getOutputStream(), true);
                    client.close();
                    server.close();
                } catch (IOException e) {
                    System.out.println(e);
                }
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void copy(InputStream in, OutputStream out, boolean waitOnSmaller) throws IOException {
        // Read bytes and write to destination until eof
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) >= 0) {
            out.write(buf, 0, len);
            if (len < 4096 && !waitOnSmaller) {
                break;
            }
        }
        out.flush();
    }
}