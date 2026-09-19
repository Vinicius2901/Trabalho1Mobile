package cliente;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {

    private static final String IP_SERVIDOR = "127.0.0.1";
    private static final int PORTA_SERVIDOR = 12345;

    public static void main(String[] args) {
        try (var socket = new Socket(IP_SERVIDOR, PORTA_SERVIDOR);
             var teclado = new Scanner(System.in);
             var saida = new DataOutputStream(socket.getOutputStream());
             var entrada = new DataInputStream(socket.getInputStream())) {

            realizarCadastro(teclado, saida, entrada);
            System.out.println("Cadastro realizado com sucesso! Digite seus comandos.");

            iniciarThreadOuvinte(socket, entrada);
            processarEntradaUsuario(teclado, saida);

        } catch (IOException e) {
            System.err.println("Erro na conexão do cliente: " + e.getMessage());
        }
    }

    private static void realizarCadastro(Scanner teclado, DataOutputStream saida, DataInputStream entrada) throws IOException {
        System.out.println("Olá! Cadastre seu nome!");

        while (true) {
            var nome = teclado.nextLine().trim();
            saida.writeUTF("/cadastrar " + nome);
            saida.flush();

            boolean sucesso = entrada.readBoolean();
            if (sucesso) {
                break;
            }
            System.out.println("Esse nome já existe ou é inválido! Tente outro nome:");
        }
    }

    private static void processarEntradaUsuario(Scanner teclado, DataOutputStream saida) throws IOException {
        while (teclado.hasNextLine()) {
            String linha = teclado.nextLine().trim();
            if (linha.isEmpty()) continue;

            if (linha.equalsIgnoreCase("/sair")) {
                saida.writeUTF("/sair");
                saida.flush();
                break;
            } else if (linha.startsWith("/send file ")) {
                enviarArquivo(linha, saida);
            } else {
                saida.writeUTF(linha);
                saida.flush();
            }
        }
    }

    private static void enviarArquivo(String comando, DataOutputStream saida) {
        String[] partes = comando.split(" ", 4);
        if (partes.length < 4) {
            System.out.println("Uso: /send file <destinatario> <caminho do arquivo>");
            return;
        }

        String destinatario = partes[2];
        File arquivo = new File(partes[3]);

        if (!arquivo.exists() || !arquivo.isFile()) {
            System.out.println("Arquivo não encontrado no caminho informado!");
            return;
        }

        try {
            saida.writeUTF("/send file " + destinatario);
            saida.writeUTF(arquivo.getName());
            saida.writeLong(arquivo.length());

            try (var fis = new FileInputStream(arquivo)) {
                byte[] buffer = new byte[4096];
                int lidos;
                while ((lidos = fis.read(buffer)) != -1) {
                    saida.write(buffer, 0, lidos);
                }
            }
            saida.flush();
            System.out.println("Arquivo '" + arquivo.getName() + "' enviado com sucesso!");

        } catch (IOException e) {
            System.err.println("Erro ao transferir arquivo: " + e.getMessage());
        }
    }

    private static void iniciarThreadOuvinte(Socket socket, DataInputStream entrada) {
        new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    String cabecalho = entrada.readUTF();

                    if (cabecalho.startsWith("/msg ")) {
                        System.out.println(cabecalho.substring(5));
                    } else if (cabecalho.startsWith("/file ")) {
                        gravarArquivoRecebido(cabecalho, entrada);
                    }
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    System.out.println("Desconectado do servidor.");
                }
            }
        }).start();
    }

    private static void gravarArquivoRecebido(String cabecalho, DataInputStream entrada) throws IOException {
        String[] partes = cabecalho.split(" ");
        String remetente = partes[1];
        String nomeArquivo = partes[2];
        long tamanho = entrada.readLong();

        try (var fos = new FileOutputStream(nomeArquivo)) {
            byte[] buffer = new byte[4096];
            long restante = tamanho;

            while (restante > 0) {
                int lidos = entrada.read(buffer, 0, (int) Math.min(buffer.length, restante));
                if (lidos == -1) break;
                fos.write(buffer, 0, lidos);
                restante -= lidos;
            }
            fos.flush();
        }
        File arquivoLocal = new File(nomeArquivo);
        System.out.println("Arquivo '" + nomeArquivo + "' recebido de " + remetente + " e salvo com sucesso!");
        System.out.println("Arquivo salvo em: " + arquivoLocal.getAbsolutePath());
    }
}