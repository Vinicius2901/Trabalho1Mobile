package servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {

    private static final int PORTA = 12345;
    
    private static final Map<String, Socket> clientesConectados = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (var server = new ServerSocket(PORTA)) {
            System.out.println("Servidor iniciado na porta " + PORTA + "!");

            while (true) {
                Socket cliente = server.accept();
                System.out.println("Nova conexão recebida: " + cliente.getInetAddress().getHostAddress());

                var tratador = new TratadorCliente(cliente, clientesConectados);
                new Thread(tratador).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }
}