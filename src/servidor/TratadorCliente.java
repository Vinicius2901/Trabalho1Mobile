package servidor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;

public class TratadorCliente implements Runnable {

    private final Socket socket;
    private final Map<String, Socket> clientesConectados;
    private String nomeCliente;
    private DataInputStream entrada;
    private DataOutputStream saida;

    public TratadorCliente(Socket socket, Map<String, Socket> clientesConectados) {
        this.socket = socket;
        this.clientesConectados = clientesConectados;
    }

    @Override
    public void run() {
        try {
            this.entrada = new DataInputStream(socket.getInputStream());
            this.saida = new DataOutputStream(socket.getOutputStream());

            realizarCadastro();
            processarComandos();

        } catch (IOException e) {
            System.err.println("Conexão encerrada com " + (nomeCliente != null ? nomeCliente : socket.getRemoteSocketAddress()));
        } finally {
            desconectar();
        }
    }

    private void realizarCadastro() throws IOException {
        while (true) {
            String linha = entrada.readUTF().trim();

            if (linha.startsWith("/cadastrar ")) {
                String nomeTentativa = linha.substring(11).trim();

                boolean sucesso = !nomeTentativa.isEmpty()
                        && clientesConectados.putIfAbsent(nomeTentativa, socket) == null;

                if (sucesso) {
                    this.nomeCliente = nomeTentativa;
                }

                saida.writeBoolean(sucesso);
                saida.flush();

                if (sucesso) {
                    String ip = socket.getInetAddress().getHostAddress();
                    System.out.println("Cliente cadastrado: " + nomeCliente + " [" + ip + "]");
                    Servidor.registrarConexao(nomeCliente, ip);
                    break;
                }
            }
        }
    }

    private void processarComandos() throws IOException {
        while (true) {
            String comando = entrada.readUTF();
            System.out.println(nomeCliente + " enviou: " + comando);

            if (comando.equalsIgnoreCase("/sair")) {
                break;
            } else if (comando.equalsIgnoreCase("/users")) {
                listarUsuarios();
            } else if (comando.startsWith("/send ")) {
                rotearEnvio(comando);
            }
        }
    }

    private void listarUsuarios() throws IOException {
        StringBuilder users = new StringBuilder("Clientes conectados:\n");
        for (String key : clientesConectados.keySet()) {
            users.append("- ").append(key).append("\n");
        }
        enviarParaMim("/msg " + users);
    }

    private void rotearEnvio(String comando) throws IOException {
        String[] partes = comando.split(" ", 4);
        if (partes.length < 3) return;

        String subComando = partes[1];
        String destinatario = partes[2];
        Socket sockDest = clientesConectados.get(destinatario);

        if (sockDest == null) {
            enviarParaMim("/msg Usuário '" + destinatario + "' não encontrado.");

            if (subComando.equalsIgnoreCase("file")) {
                descartarArquivoRemetente();
            }
            return;
        }

        try {
            if (subComando.equalsIgnoreCase("message") && partes.length >= 4) {
                enviarMensagemTexto(sockDest, partes[3]);
            } else if (subComando.equalsIgnoreCase("file")) {
                repassarArquivo(sockDest);
            }
        } catch (IOException e) {
            // Falha ao escrever no socket do destinatário não pode derrubar
            // a conexão de quem está enviando.
            System.err.println("Falha ao entregar para " + destinatario + ": " + e.getMessage());
            enviarParaMim("/msg Falha ao entregar mensagem para '" + destinatario + "'.");
        }
    }

    private void enviarMensagemTexto(Socket sockDest, String mensagem) throws IOException {
        // Mutex: sincroniza no próprio Socket de destino, serializando
        // escritas concorrentes que podem vir de várias threads de clientes.
        synchronized (sockDest) {
            var saidaDest = new DataOutputStream(sockDest.getOutputStream());
            saidaDest.writeUTF("/msg " + nomeCliente + ": " + mensagem);
            saidaDest.flush();
        }
    }

    private void repassarArquivo(Socket sockDest) throws IOException {
        String nomeArquivo = entrada.readUTF();
        long tamanho = entrada.readLong();

        // Mutex: mantém o cabeçalho e os bytes do arquivo como uma única
        // escrita atômica no socket de destino, evitando que outra
        // mensagem/arquivo se intercale no meio da transferência.
        synchronized (sockDest) {
            var saidaDest = new DataOutputStream(sockDest.getOutputStream());
            saidaDest.writeUTF("/file " + nomeCliente + " " + nomeArquivo);
            saidaDest.writeLong(tamanho);

            byte[] buffer = new byte[4096];
            long restante = tamanho;

            while (restante > 0) {
                int lidos = entrada.read(buffer, 0, (int) Math.min(buffer.length, restante));
                if (lidos == -1) break;
                saidaDest.write(buffer, 0, lidos);
                restante -= lidos;
            }
            saidaDest.flush();
        }
    }

    private void descartarArquivoRemetente() throws IOException {
        entrada.readUTF();
        long tamanho = entrada.readLong();
        byte[] buffer = new byte[4096];
        long restante = tamanho;

        while (restante > 0) {
            int lidos = entrada.read(buffer, 0, (int) Math.min(buffer.length, restante));
            if (lidos == -1) break;
            restante -= lidos;
        }
    }

    private void enviarParaMim(String mensagem) throws IOException {
        synchronized (socket) {
            saida.writeUTF(mensagem);
            saida.flush();
        }
    }

    private void desconectar() {
        if (nomeCliente != null) {
            clientesConectados.remove(nomeCliente);
            System.out.println(nomeCliente + " desconectou.");
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
