package com.fundacao.aualmoxarifado.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolve a configuração do banco EXCLUSIVAMENTE a partir de variáveis de
 * ambiente — contrato do deploy em container:
 *
 *   DATABASE_URL  postgresql://usuario:senha@host:5432/banco   (tem precedência)
 *   — ou —
 *   DB_HOST, DB_PORT (opcional, default 5432), DB_NAME, DB_USER, DB_PASSWORD
 *
 * Comportamento intencional:
 *  - NÃO existe fallback para localhost: se faltar variável obrigatória, o
 *    boot FALHA com uma mensagem listando exatamente o que está ausente.
 *    Uma aplicação que sobe apontando para lugar nenhum esconde erro de
 *    configuração — preferimos o erro imediato e legível.
 *  - Os perfis "dev" e "test" são ignorados aqui: eles usam H2 em memória
 *    declarado nos respectivos application-{profile}.properties e nunca
 *    tocam em banco externo.
 *
 * Registrado em {@code META-INF/spring.factories}. Roda com prioridade mínima
 * para executar DEPOIS do carregamento dos arquivos de configuração — assim os
 * perfis ativos já estão resolvidos quando a checagem acontece.
 */
public class BancoDeDadosEnvPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Set<String> PERFIS_SEM_BANCO_EXTERNO = Set.of("dev", "test");

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        for (String perfil : env.getActiveProfiles()) {
            if (PERFIS_SEM_BANCO_EXTERNO.contains(perfil)) {
                return;
            }
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");

        String databaseUrl = env.getProperty("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            aplicarDatabaseUrl(databaseUrl.trim(), props);
        } else {
            aplicarVariaveisSeparadas(env, props);
        }

        env.getPropertySources().addFirst(
                new MapPropertySource("bancoDeDadosViaVariaveisDeAmbiente", props));
    }

    /** Converte postgresql://usuario:senha@host:porta/banco em propriedades JDBC. */
    private void aplicarDatabaseUrl(String url, Map<String, Object> props) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(erroDatabaseUrl(url), e);
        }

        String esquema = uri.getScheme();
        if (esquema == null || !(esquema.equals("postgresql") || esquema.equals("postgres"))) {
            throw new IllegalStateException(erroDatabaseUrl(url));
        }
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() <= 1) {
            throw new IllegalStateException(erroDatabaseUrl(url));
        }

        String userInfo = uri.getUserInfo();
        if (userInfo == null || userInfo.isBlank()) {
            throw new IllegalStateException(erroDatabaseUrl(url));
        }
        int separador = userInfo.indexOf(':');
        String usuario = separador < 0 ? userInfo : userInfo.substring(0, separador);
        String senha = separador < 0 ? "" : userInfo.substring(separador + 1);

        int porta = uri.getPort() > 0 ? uri.getPort() : 5432;
        String banco = uri.getPath().substring(1);

        props.put("spring.datasource.url",
                "jdbc:postgresql://" + uri.getHost() + ":" + porta + "/" + banco);
        props.put("spring.datasource.username", usuario);
        props.put("spring.datasource.password", senha);
    }

    private void aplicarVariaveisSeparadas(ConfigurableEnvironment env, Map<String, Object> props) {
        List<String> faltando = new ArrayList<>();
        String host = exigir(env, "DB_HOST", faltando);
        String nome = exigir(env, "DB_NAME", faltando);
        String usuario = exigir(env, "DB_USER", faltando);
        String senha = exigir(env, "DB_PASSWORD", faltando);

        if (!faltando.isEmpty()) {
            throw new IllegalStateException("""

                    ==========================================================================
                    CONFIGURAÇÃO DE BANCO AUSENTE — a aplicação não vai subir.

                    Variáveis de ambiente faltando: %s

                    Defina DATABASE_URL (postgresql://usuario:senha@host:5432/banco)
                    OU o conjunto: DB_HOST, DB_PORT (opcional, default 5432),
                    DB_NAME, DB_USER, DB_PASSWORD.

                    Para desenvolvimento local sem PostgreSQL, use o perfil dev:
                    SPRING_PROFILES_ACTIVE=dev (o `gradlew bootRun` já faz isso).
                    ==========================================================================
                    """.formatted(String.join(", ", faltando)));
        }

        String porta = env.getProperty("DB_PORT", "5432");
        props.put("spring.datasource.url",
                "jdbc:postgresql://" + host + ":" + porta + "/" + nome);
        props.put("spring.datasource.username", usuario);
        props.put("spring.datasource.password", senha);
    }

    private String exigir(ConfigurableEnvironment env, String nome, List<String> faltando) {
        String valor = env.getProperty(nome);
        if (valor == null || valor.isBlank()) {
            faltando.add(nome);
            return null;
        }
        return valor.trim();
    }

    private static String erroDatabaseUrl(String url) {
        // Nunca ecoamos a URL inteira — ela contém a senha.
        int fimEsquema = url.indexOf("://");
        String esquema = fimEsquema > 0 ? url.substring(0, fimEsquema) : "(sem esquema)";
        return "DATABASE_URL inválida (esperado: postgresql://usuario:senha@host:5432/banco). "
             + "Valor recebido tem " + url.length() + " caracteres e esquema \"" + esquema + "\".";
    }
}
