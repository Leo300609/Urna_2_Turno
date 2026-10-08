package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Set<String> CANDIDATOS_VALIDOS = Set.of("13", "22");
    private static final Map<String, Integer> VOTOS = new HashMap<>();
    private static final Pattern NUMERO_PATTERN = Pattern.compile("\"numero\"\\s*:\\s*\"([^\"]+)\"");

    static {
        limparVotos();
    }

    public static void limparVotos() {
        VOTOS.clear();
        for (String numero : CANDIDATOS_VALIDOS) {
            VOTOS.put(numero, 0);
        }
        VOTOS.put("BRANCO", 0);
        VOTOS.put("NULO", 0);
    }

    public static void resetarVotos() {
        limparVotos();
    }

    public static void registrarVoto(String numero) {
        String valor = numero == null ? "" : numero.trim().toUpperCase();

        if (valor.isEmpty()) {
            return;
        }

        if (CANDIDATOS_VALIDOS.contains(valor) || "BRANCO".equals(valor) || "NULO".equals(valor)) {
            VOTOS.put(valor, VOTOS.getOrDefault(valor, 0) + 1);
        }
    }

    public static Map<String, Integer> getResultadoVotos() {
        return new HashMap<>(VOTOS);
    }

    public static int getTotalVotos() {
        return VOTOS.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static void main(String[] args) throws IOException {
        int porta = Integer.parseInt(System.getProperty("server.port", "8080"));
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);

        servidor.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();

            try {
                if (path.startsWith("/api/")) {
                    tratarApi(exchange);
                    return;
                }

                Path diretorioRaiz = resolverDiretorioRaiz();
                Path arquivo = resolverArquivo(diretorioRaiz, path);
                byte[] conteudo = Files.readAllBytes(arquivo);

                exchange.getResponseHeaders().add("Content-Type", tipoConteudo(arquivo));
                exchange.sendResponseHeaders(200, conteudo.length);

                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(conteudo);
                }
            } catch (Exception e) {
                enviarJson(exchange, 500, "{\"erro\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
            }
        });

        servidor.setExecutor(null);
        servidor.start();
        System.out.println("Servidor da urna rodando em http://localhost:" + porta);
    }

    private static void tratarApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/resultados".equals(path)) {
            enviarJson(exchange, 200, construirResultadoJson());
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/votar".equals(path)) {
            String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String numero = extrairNumero(corpo);

            registrarVoto(numero);
            enviarJson(exchange, 200, "{\"status\":\"ok\",\"voto\":" + citar(numero) + ",\"total\":" + getTotalVotos() + "}");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/reset".equals(path)) {
            resetarVotos();
            enviarJson(exchange, 200, "{\"status\":\"reset\",\"total\":0,\"candidatos\":{\"13\":0,\"22\":0,\"BRANCO\":0,\"NULO\":0}}");
            return;
        }

        enviarJson(exchange, 404, "{\"erro\":\"Endpoint nao encontrado\"}");
    }

    private static String construirResultadoJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"total\":").append(getTotalVotos()).append(",\"candidatos\":{");

        String[] chaves = {"13", "22", "BRANCO", "NULO"};
        for (int i = 0; i < chaves.length; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(chaves[i]).append("\":").append(VOTOS.getOrDefault(chaves[i], 0));
        }

        json.append("}}");
        return json.toString();
    }

    private static String extrairNumero(String corpo) {
        Matcher matcher = NUMERO_PATTERN.matcher(corpo);
        if (matcher.find()) {
            return matcher.group(1).trim().toUpperCase();
        }
        return "";
    }

    private static void enviarJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] resposta = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, resposta.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(resposta);
        }
    }

    private static String citar(String texto) {
        if (texto == null) {
            return "\"\"";
        }
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Path resolverDiretorioRaiz() {
        Path atual = Paths.get("").toAbsolutePath();
        if (atual.getFileName() != null && "demo".equals(atual.getFileName().toString())) {
            return atual.getParent();
        }
        return atual;
    }

    private static Path resolverArquivo(Path diretorioRaiz, String path) throws IOException {
        if ("/".equals(path) || "".equals(path)) {
            return diretorioRaiz.resolve("index.html");
        }

        String subcaminho = path.startsWith("/") ? path.substring(1) : path;
        Path arquivo = diretorioRaiz.resolve(subcaminho).normalize();

        if (!arquivo.startsWith(diretorioRaiz) || !Files.exists(arquivo)) {
            return diretorioRaiz.resolve("index.html");
        }

        return arquivo;
    }

    private static String tipoConteudo(Path arquivo) {
        String nome = arquivo.getFileName().toString().toLowerCase();

        if (nome.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }

        if (nome.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }

        if (nome.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }

        return "application/octet-stream";
    }
}
