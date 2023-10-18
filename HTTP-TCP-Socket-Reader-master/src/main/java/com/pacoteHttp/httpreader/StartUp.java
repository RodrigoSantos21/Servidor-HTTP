package com.pacoteHttp.httpreader;

import com.cyecize.httpreader.util.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StartUp {
    public static void main(String[] args) throws Exception {
        final int port = 1090;
        final ServerSocket serverSocket = new ServerSocket(port);

        System.out.println(String.format("Começou a ouvir na porta %d", port));

        while (true) {
            final Socket client = serverSocket.accept();
            client.setSoTimeout(0);
            new Thread(() -> {
                try {
                    final InputStream inputStream = client.getInputStream();
                    final OutputStream outputStream = client.getOutputStream();

                    final SolicitacaoHttp solicitacaoHttp = parseMetadata(inputStream);
                    if(solicitacaoHttp.getMetodo().equalsIgnoreCase("GET")){
                        getSolicitacao(solicitacaoHttp, outputStream);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                } finally {
                    try {
                        client.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public static SolicitacaoHttp parseMetadata(InputStream data) throws IOException {
        final List<String> linhaMetaDados = new ArrayList<>();

        final StringBuilder lineBuilder = new StringBuilder();
        int b;
        boolean novaLinha = false;

        while ((b = data.read()) >= 0) {
            if (b == '\r') {
                int next = data.read();
                if (next == '\n') {
                    if (novaLinha) {
                        break;
                    }
                    novaLinha = true;
                    linhaMetaDados.add(lineBuilder.toString());
                    lineBuilder.setLength(0);
                }
            } else {
                lineBuilder.append((char) b);
                novaLinha = false;
            }
        }

        final String linha = linhaMetaDados.get(0);
        final String metodo = linha.split("\\s+")[0];
        final String url = linha.split("\\s+")[1];

        final Map<String, String> headers = new HashMap<>();

        for (int i = 1; i < linhaMetaDados.size(); i++) {
            String headerLine = linhaMetaDados.get(i);
            if (headerLine.trim().isEmpty()) {
                break;
            }

            String chave = headerLine.split(":\\s")[0];
            String valor = headerLine.split(":\\s")[1];

            headers.put(chave, valor);
        }

        return new SolicitacaoHttp(metodo, url, headers);
    }

    public static void getSolicitacao(SolicitacaoHttp solicitacao, OutputStream outputStream) throws IOException {
        String nomeArquivo = solicitacao.getUrl();
        if (FileUtils.exist("servidor" + nomeArquivo)) {
            nomeArquivo = "servidor" + nomeArquivo;
        }
        else {
            outputStream.write("HTTP/1.1 404 Erro\r\n\r\n<h1>Arquivo nao encontrado!</h1>".getBytes(StandardCharsets.UTF_8));
            return;
        }

        final StringBuilder responseMetadata = new StringBuilder();
        responseMetadata.append("HTTP/1.1 200 OK\r\n");

        responseMetadata.append(String.format("Tipo: %s\r\n", FileUtils.probeContentType(nomeArquivo)));

        final InputStream fileStream = FileUtils.getInputStream(nomeArquivo);
        responseMetadata.append(String.format("Tamanho: %d\r\n", fileStream.available()));
        responseMetadata.append("\r\n");

        outputStream.write(responseMetadata.toString().getBytes(StandardCharsets.UTF_8));
        try (fileStream) {
            fileStream.transferTo(outputStream);
        }
    }

    static class SolicitacaoHttp {
        private final String metodo;
        private final String url;
        private final Map<String, String> headers;

        SolicitacaoHttp(String metodo, String url, Map<String, String> headers) {
            this.metodo = metodo;
            this.url = url;
            this.headers = headers;
        }

        public String getMetodo() {
            return metodo;
        }

        public String getUrl() {
            return url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }
    }

}
