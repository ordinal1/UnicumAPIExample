package dev.ordinal1.ru.Server;

import lombok.Getter;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.concurrent.Executors;

public class Server implements Runnable{

    @Override
    public void run() {
        try {
            initialize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initialize() throws IOException {
        //Создаём ServerSocket с портом 1234.
        try (var listener = new ServerSocket(1234)) {
            System.out.println("Server is running...");
            //Указываем количество потоков
            var pool = Executors.newFixedThreadPool(5);
            while (true) {
                //При подключении создаём экземпляр Socket
                pool.execute(new ClientHandler(listener.accept()));
            }
        }
    }


}
