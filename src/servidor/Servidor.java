package servidor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {

    private static final int PORTA = 12345;
    private static final String ARQUIVO_LOG = "conexoes.log";
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final Map<String, Socket> clientesConectados = new ConcurrentHashMap<>();

    private static final Object lockLogs = new Object();
    private static final Queue<String> filaLogs = new LinkedList<>();
    private static volatile boolean encerrando = false;

    public static void main(String[] args) {
        Thread threadLogs = new Thread(Servidor::processarFilaLogs, "log-conexoes");
        threadLogs.start();

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
        } finally {
            synchronized (lockLogs) {
                encerrando = true;
                lockLogs.notifyAll();
            }
            try {
                threadLogs.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void registrarConexao(String nome, String ip) {
        String linha = String.format("[%s] Cliente '%s' conectado a partir de %s",
                LocalDateTime.now().format(FORMATO_DATA), nome, ip);

        synchronized (lockLogs) {
            filaLogs.add(linha);
            lockLogs.notify();
        }
    }

    private static void processarFilaLogs() {
        try (var escritor = new BufferedWriter(new FileWriter(ARQUIVO_LOG, true))) {
            while (true) {
                String linha;

                synchronized (lockLogs) {
                    while (filaLogs.isEmpty() && !encerrando) {
                        try {
                            lockLogs.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    if (filaLogs.isEmpty() && encerrando) {
                        return;
                    }

                    linha = filaLogs.poll();
                }

                escritor.write(linha);
                escritor.newLine();
                escritor.flush();
            }
        } catch (IOException e) {
            System.err.println("Erro ao escrever log de conexões: " + e.getMessage());
        }
    }
}
