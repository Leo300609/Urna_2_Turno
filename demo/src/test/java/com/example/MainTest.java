package com.example;

public class MainTest {

    public static void main(String[] args) {
        testeVotoValido();
        testeVotoInvalido();
        System.out.println("Testes da urna concluídos com sucesso.");
    }

    private static void testeVotoValido() {
        Main.limparVotos();
        Main.registrarVoto("22");

        if (Main.getResultadoVotos().get("22") != 1) {
            throw new AssertionError("Voto válido para Flávio não foi registrado.");
        }

        if (Main.getTotalVotos() != 1) {
            throw new AssertionError("Total de votos inválido.");
        }
    }

    private static void testeVotoInvalido() {
        Main.limparVotos();
        Main.registrarVoto("99");
        Main.registrarVoto("BRANCO");

        if (Main.getResultadoVotos().getOrDefault("99", 0) != 0) {
            throw new AssertionError("Voto inválido foi aceito.");
        }

        if (Main.getResultadoVotos().get("BRANCO") != 1) {
            throw new AssertionError("Voto em branco não foi contabilizado.");
        }
    }
}
