package handist.glb.multiworker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

  public Server() {}

  public static boolean server() {
    boolean malleable = false;
    try (ServerSocket server = new ServerSocket()) {
      server.bind(new InetSocketAddress("localhost", 8080));
      try (Socket socket = server.accept(); ) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line = reader.readLine();
        if (line.equals("expand")) {
          return true;
        } else {
          return false;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return malleable;
  }
}
