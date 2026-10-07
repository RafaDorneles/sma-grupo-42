import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * M6 | Simulacao de Filas em Tandem
 *
 * CHEGADA -> [ Fila1 ] --PASSAGEM--> [ Fila2 ] --> SAIDA
 *
 * Fila1: G/G/2/3, chegadas entre 1..5, atendimento entre 4..5
 * Fila2: G/G/1/5, atendimento entre 1..3
 *
 * M8 | Generalizacao para Rede de Filas
 *
 * O codigo original foi preservado sempre que possivel. As extensoes do M8
 * permitem carregar n filas a partir de um arquivo .yml, realizar roteamento
 * probabilistico entre elas e reportar os resultados de todas as filas.
 */
public class SimuladorFilaSimples {

    /**
     * Ordem em que a proxima chegada e agendada dentro do tratamento de CHEGADA.
     *
     * true  (padrao) - conforme o pseudocodigo do modulo M5: o agendamento da
     *                  proxima chegada e a LINHA 9, isto e, a ultima instrucao,
     *                  depois do teste de capacidade.
     * false          - agenda a proxima chegada antes do teste de capacidade.
     *
     * As duas variantes sao simulacoes validas; o que muda e a ordem em que os
     * numeros pseudoaleatorios sao consumidos, e portanto os resultados.
     * Ver comparacao no README.
     */
    static boolean ORDEM_PSEUDOCODIGO = true;


    // ---------------------------------------------------------------
    // Gerador congruente linear (reaproveitado do M4)
    // ---------------------------------------------------------------

    static long mclA    = 1_664_525L;
    static long mclC    = 1_013_904_223L;
    static long mclM    = 4_294_967_296L;
    static long mclPrev = 12_345L;
    static int  mclUsed = 0;
    static int  mclMax  = Integer.MAX_VALUE;

    // Quando o arquivo do Modulo 3 fornece a lista "rndnumbers",
    // estes valores sao consumidos no lugar do gerador congruente linear.
    static List<Double> rndnumbersFornecidos = null;

    static void resetLcg(long seed, int maxRandoms) {
        rndnumbersFornecidos = null;
        mclPrev = seed;
        mclUsed = 0;
        mclMax = maxRandoms;
    }

    static void resetRndnumbers(List<Double> rndnumbers) {
        rndnumbersFornecidos = new ArrayList<>(rndnumbers);
        mclUsed = 0;
        mclMax = rndnumbersFornecidos.size();
    }

    static double nextRandom() {

        if (mclUsed >= mclMax) {
            throw new StopSimulationException();
        }

        if (rndnumbersFornecidos != null) {
            return rndnumbersFornecidos.get(mclUsed++);
        }

        mclPrev = (mclA * mclPrev + mclC) % mclM;
        mclUsed++;

        return (double) mclPrev / (double) mclM;
    }

    static int randomsUsed() {
        return mclUsed;
    }

    static double uniform(double lo, double hi) {
        return lo + (hi - lo) * nextRandom();
    }


    // ---------------------------------------------------------------
    // Tipos de evento
    // ---------------------------------------------------------------

    static final int CHEGADA  = 0;
    static final int SAIDA    = 1;
    static final int PASSAGEM = 2;
    static final int EXTERIOR = -1;

    static class StopSimulationException extends RuntimeException {
    }


    // ---------------------------------------------------------------
    // Fila
    // ---------------------------------------------------------------

    static class Fila {

        private final String nome;

        private final int servidores;
        private final int capacidade;

        private final double minArrival;
        private final double maxArrival;

        private final double minService;
        private final double maxService;

        private int customers;
        private int loss;

        private double[] times;
        private int maxCustomersObserved;


        Fila(
                String nome,
                int servidores,
                int capacidade,
                double minArrival,
                double maxArrival,
                double minService,
                double maxService
        ) {

            this.nome = nome;

            this.servidores = servidores;
            this.capacidade = capacidade;

            this.minArrival = minArrival;
            this.maxArrival = maxArrival;

            this.minService = minService;
            this.maxService = maxService;

            this.customers = 0;
            this.loss = 0;

            // No M8, capacidade -1 representa uma fila sem limite informado
            // (por exemplo G/G/1). O vetor cresce somente se novos estados forem
            // realmente visitados. Para filas G/G/c/K, continua com K + 1 estados.
            this.times = new double[capacidade < 0 ? 16 : capacidade + 1];
            this.maxCustomersObserved = 0;
        }


        String name() {
            return nome;
        }

        int status() {
            return customers;
        }

        int capacity() {
            return capacidade;
        }

        int servers() {
            return servidores;
        }

        int loss() {
            return loss;
        }

        double minArrival() {
            return minArrival;
        }

        double maxArrival() {
            return maxArrival;
        }

        double minService() {
            return minService;
        }

        double maxService() {
            return maxService;
        }

        double[] times() {
            return times;
        }

        int maxState() {
            return capacidade < 0 ? maxCustomersObserved : capacidade;
        }

        boolean unlimited() {
            return capacidade < 0;
        }

        void in() {
            customers++;
            maxCustomersObserved = Math.max(maxCustomersObserved, customers);
            garantirEstado(customers);
        }

        void out() {
            customers--;
        }

        void addLoss() {
            loss++;
        }

        void acumulaTempo(double delta) {
            garantirEstado(customers);
            times[customers] += delta;
        }

        private void garantirEstado(int estado) {
            if (estado < times.length) {
                return;
            }

            int novoTamanho = times.length;
            while (novoTamanho <= estado) {
                novoTamanho *= 2;
            }

            times = Arrays.copyOf(times, novoTamanho);
        }
    }


    // ---------------------------------------------------------------
    // Evento e escalonador
    // ---------------------------------------------------------------

    static class Evento implements Comparable<Evento> {

        private final double tempo;
        private final int tipo;
        private final int filaOrigem;
        private final int filaDestino;


        Evento(double tempo, int tipo) {
            this(tempo, tipo, EXTERIOR, EXTERIOR);
        }


        Evento(double tempo, int tipo, int filaOrigem, int filaDestino) {
            this.tempo = tempo;
            this.tipo = tipo;
            this.filaOrigem = filaOrigem;
            this.filaDestino = filaDestino;
        }


        double tempo() {
            return tempo;
        }

        int tipo() {
            return tipo;
        }

        int filaOrigem() {
            return filaOrigem;
        }

        int filaDestino() {
            return filaDestino;
        }


        @Override
        public int compareTo(Evento outro) {
            return Double.compare(this.tempo, outro.tempo);
        }
    }


    static class Escalonador {

        private final PriorityQueue<Evento> eventos =
                new PriorityQueue<>();


        void adicionar(Evento evento) {
            eventos.add(evento);
        }


        Evento proximo() {
            return eventos.poll();
        }


        boolean vazio() {
            return eventos.isEmpty();
        }
    }


    static class Resultado {

        double tempoGlobal;

        Fila fila1;
        Fila fila2;

        ArrayList<Fila> filas;

        int randomsUsados;
    }


    // ---------------------------------------------------------------
    // Configuracao do modelo M8
    // ---------------------------------------------------------------

    static class ChegadaExterna {

        private final int indiceFila;
        private final double primeiraChegada;

        ChegadaExterna(int indiceFila, double primeiraChegada) {
            this.indiceFila = indiceFila;
            this.primeiraChegada = primeiraChegada;
        }
    }


    static class Modelo {

        ArrayList<Fila> filas = new ArrayList<>();
        ArrayList<ChegadaExterna> chegadasExternas = new ArrayList<>();
        double[][] probabilidadesRoteamento;

        // Formato utilizado pelo simulador do Modulo 3.
        int rndnumbersPerSeed = 100_000;
        ArrayList<Long> seeds = new ArrayList<>();
        ArrayList<Double> rndnumbers = new ArrayList<>();

        // Mantidos para compatibilidade com chamadas anteriores do codigo.
        int maxRandoms = 100_000;
        long seed = 12_345L;
    }


    static class FilaLida {
        String nome;
        Integer servidores;
        Integer capacidade;
        Double chegadaMin = 0.0;
        Double chegadaMax = 0.0;
        Double servicoMin;
        Double servicoMax;
        Double primeiraChegada;
    }


    static class RotaLida {
        String origem;
        String destino;
        Double probabilidade;
    }


    // ---------------------------------------------------------------
    // Simulacao
    // ---------------------------------------------------------------

    static Resultado simular(
            Fila fila1,
            Fila fila2,
            int maxRandoms,
            long seed,
            double firstArrival
    ) {

        // Mantem a chamada usada no codigo anterior.
        // Fila1 -> Fila2 -> exterior, ambos com probabilidade 1.
        ArrayList<Fila> listaDeFilas = new ArrayList<>();

        listaDeFilas.add(fila1);
        listaDeFilas.add(fila2);

        double[][] probabilidadesRoteamento = {
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        };

        return simular(
                listaDeFilas,
                probabilidadesRoteamento,
                0,
                maxRandoms,
                seed,
                firstArrival
        );
    }


    static Resultado simular(
            ArrayList<Fila> listaDeFilas,
            double[][] probabilidadesRoteamento,
            int filaChegadaExterna,
            int maxRandoms,
            long seed,
            double firstArrival
    ) {

        ArrayList<ChegadaExterna> chegadasExternas = new ArrayList<>();
        chegadasExternas.add(new ChegadaExterna(filaChegadaExterna, firstArrival));

        return simular(
                listaDeFilas,
                probabilidadesRoteamento,
                chegadasExternas,
                maxRandoms,
                seed
        );
    }


    static Resultado simular(Modelo modelo) {

        // Conforme o arquivo do Modulo 3: quando "seeds" existe,
        // "rndnumbers" deve ser ignorado. Esta sobrecarga executa a
        // primeira semente; o main executa todas quando houver mais de uma.
        if (!modelo.seeds.isEmpty()) {
            return simular(modelo, modelo.seeds.get(0));
        }

        if (!modelo.rndnumbers.isEmpty()) {
            return simularComRndnumbers(modelo);
        }

        return simular(
                modelo.filas,
                modelo.probabilidadesRoteamento,
                modelo.chegadasExternas,
                modelo.maxRandoms,
                modelo.seed,
                true,
                null
        );
    }


    static Resultado simular(Modelo modelo, long seed) {
        return simular(
                modelo.filas,
                modelo.probabilidadesRoteamento,
                modelo.chegadasExternas,
                modelo.rndnumbersPerSeed,
                seed,
                true,
                null
        );
    }


    static Resultado simularComRndnumbers(Modelo modelo) {
        return simular(
                modelo.filas,
                modelo.probabilidadesRoteamento,
                modelo.chegadasExternas,
                modelo.rndnumbers.size(),
                0L,
                true,
                modelo.rndnumbers
        );
    }


    static Resultado simular(
            ArrayList<Fila> listaDeFilas,
            double[][] probabilidadesRoteamento,
            ArrayList<ChegadaExterna> chegadasExternas,
            int maxRandoms,
            long seed
    ) {
        return simular(
                listaDeFilas,
                probabilidadesRoteamento,
                chegadasExternas,
                maxRandoms,
                seed,
                false,
                null
        );
    }


    static Resultado simular(
            ArrayList<Fila> listaDeFilas,
            double[][] probabilidadesRoteamento,
            ArrayList<ChegadaExterna> chegadasExternas,
            int maxRandoms,
            long seed,
            boolean pararImediatamenteNoLimite,
            List<Double> rndnumbers
    ) {

        if (rndnumbers == null) {
            resetLcg(seed, maxRandoms);
        } else {
            resetRndnumbers(rndnumbers);
        }

        double t = 0.0;

        Escalonador escalonador = new Escalonador();

        for (ChegadaExterna chegada : chegadasExternas) {
            escalonador.adicionar(
                    new Evento(
                            chegada.primeiraChegada,
                            CHEGADA,
                            EXTERIOR,
                            chegada.indiceFila
                    )
            );
        }


        try {

            while (!escalonador.vazio()) {

                if (pararImediatamenteNoLimite && randomsUsed() >= maxRandoms) {
                    break;
                }

                Evento ev = escalonador.proximo();

                double evtT = ev.tempo();

                double delta = evtT - t;


                // AcumulaTempo: o estado de TODAS as filas permaneceu inalterado
                // durante todo o intervalo entre o evento anterior e este.
                for (Fila fila : listaDeFilas) {
                    fila.acumulaTempo(delta);
                }


                t = evtT;


                if (ev.tipo() == CHEGADA) {

                    Fila filaDestino = listaDeFilas.get(ev.filaDestino());

                    if (!ORDEM_PSEUDOCODIGO) {
                        agendarProximaChegada(
                                escalonador,
                                filaDestino,
                                ev.filaDestino(),
                                t
                        );
                    }

                    if (temEspaco(filaDestino)) {

                        filaDestino.in();

                        if (filaDestino.status() <= filaDestino.servers()) {

                            agendarFimAtendimento(
                                    escalonador,
                                    listaDeFilas,
                                    probabilidadesRoteamento,
                                    ev.filaDestino(),
                                    t
                            );
                        }

                    } else {

                        filaDestino.addLoss();
                    }

                    // Linha 9 do pseudocodigo.
                    if (ORDEM_PSEUDOCODIGO) {
                        agendarProximaChegada(
                                escalonador,
                                filaDestino,
                                ev.filaDestino(),
                                t
                        );
                    }


                } else if (ev.tipo() == PASSAGEM) {

                    Fila filaOrigem = listaDeFilas.get(ev.filaOrigem());
                    Fila filaDestino = listaDeFilas.get(ev.filaDestino());

                    // "saida" da fila de origem
                    filaOrigem.out();

                    if (filaOrigem.status() >= filaOrigem.servers()) {

                        agendarFimAtendimento(
                                escalonador,
                                listaDeFilas,
                                probabilidadesRoteamento,
                                ev.filaOrigem(),
                                t
                        );
                    }

                    // "chegada" na fila de destino (sem agendar nova chegada externa)
                    if (temEspaco(filaDestino)) {

                        filaDestino.in();

                        if (filaDestino.status() <= filaDestino.servers()) {

                            agendarFimAtendimento(
                                    escalonador,
                                    listaDeFilas,
                                    probabilidadesRoteamento,
                                    ev.filaDestino(),
                                    t
                            );
                        }

                    } else {

                        filaDestino.addLoss();
                    }


                } else if (ev.tipo() == SAIDA) {

                    Fila filaOrigem = listaDeFilas.get(ev.filaOrigem());

                    filaOrigem.out();

                    if (filaOrigem.status() >= filaOrigem.servers()) {

                        agendarFimAtendimento(
                                escalonador,
                                listaDeFilas,
                                probabilidadesRoteamento,
                                ev.filaOrigem(),
                                t
                        );
                    }
                }
            }

        } catch (StopSimulationException ignored) {
            // fim da simulacao: esgotou a quota de numeros pseudoaleatorios
        }


        Resultado res = new Resultado();

        res.tempoGlobal = t;
        res.filas = listaDeFilas;

        // Mantem o Resultado anterior disponivel para codigo dos modulos anteriores.
        if (!listaDeFilas.isEmpty()) {
            res.fila1 = listaDeFilas.get(0);
        }

        if (listaDeFilas.size() > 1) {
            res.fila2 = listaDeFilas.get(1);
        }

        res.randomsUsados = randomsUsed();

        return res;
    }


    static boolean temEspaco(Fila fila) {
        return fila.unlimited() || fila.status() < fila.capacity();
    }


    static void agendarFimAtendimento(
            Escalonador escalonador,
            ArrayList<Fila> listaDeFilas,
            double[][] probabilidadesRoteamento,
            int indiceFilaOrigem,
            double t
    ) {

        Fila filaOrigem = listaDeFilas.get(indiceFilaOrigem);

        double tempoDoEvento =
                t + uniform(
                        filaOrigem.minService(),
                        filaOrigem.maxService()
                );

        int indiceFilaDestino = sortearDestino(
                probabilidadesRoteamento[indiceFilaOrigem],
                listaDeFilas.size()
        );

        if (indiceFilaDestino == EXTERIOR) {

            escalonador.adicionar(
                    new Evento(
                            tempoDoEvento,
                            SAIDA,
                            indiceFilaOrigem,
                            EXTERIOR
                    )
            );

        } else {

            escalonador.adicionar(
                    new Evento(
                            tempoDoEvento,
                            PASSAGEM,
                            indiceFilaOrigem,
                            indiceFilaDestino
                    )
            );
        }
    }


    static int sortearDestino(
            double[] probabilidades,
            int quantidadeDeFilas
    ) {

        // Se existe apenas uma rota com 100% de probabilidade, nao e necessario
        // consumir um numero pseudoaleatorio. Isso preserva o comportamento
        // do simulador anterior nos casos de roteamento deterministico.
        int quantidadeDeDestinos = 0;
        int destinoUnico = EXTERIOR;
        double probabilidadeDestinoUnico = 0.0;

        for (int i = 0; i <= quantidadeDeFilas; i++) {

            if (probabilidades[i] > 0.0) {

                quantidadeDeDestinos++;
                probabilidadeDestinoUnico = probabilidades[i];

                destinoUnico =
                        (i == quantidadeDeFilas)
                                ? EXTERIOR
                                : i;
            }
        }

        if (quantidadeDeDestinos == 1
                && Math.abs(probabilidadeDestinoUnico - 1.0) < 0.0000001) {
            return destinoUnico;
        }


        double sum = 0.0;
        double prob = nextRandom();

        for (int i = 0; i <= quantidadeDeFilas; i++) {

            sum += probabilidades[i];

            if (prob < sum) {

                if (i == quantidadeDeFilas) {
                    return EXTERIOR;
                }

                return i;
            }
        }

        return EXTERIOR;
    }


    static void agendarProximaChegada(
            Escalonador escalonador,
            Fila fila,
            int indiceFila,
            double t
    ) {

        escalonador.adicionar(
                new Evento(
                        t + uniform(
                                fila.minArrival(),
                                fila.maxArrival()
                        ),
                        CHEGADA,
                        EXTERIOR,
                        indiceFila
                )
        );
    }


    static void agendarProximaChegada(
            Escalonador escalonador,
            Fila fila1,
            double t
    ) {

        escalonador.adicionar(
                new Evento(
                        t + uniform(
                                fila1.minArrival(),
                                fila1.maxArrival()
                        ),
                        CHEGADA
                )
        );
    }


    // ---------------------------------------------------------------
    // Leitura do arquivo .yml - mesmo estilo do simulador do Modulo 3
    // ---------------------------------------------------------------

    static Modelo carregarModelo(String caminho) throws IOException {

        List<String> linhas = Files.readAllLines(Path.of(caminho));

        Map<String, Double> primeirasChegadas = new LinkedHashMap<>();
        LinkedHashMap<String, FilaLida> filasLidas = new LinkedHashMap<>();
        ArrayList<RotaLida> rotasLidas = new ArrayList<>();
        ArrayList<Double> rndnumbers = new ArrayList<>();
        ArrayList<Long> seeds = new ArrayList<>();

        int rndnumbersPerSeed = 100_000;

        String secao = "";
        FilaLida filaAtual = null;
        RotaLida rotaAtual = null;

        for (String linhaOriginal : linhas) {

            String semComentario = removerComentario(linhaOriginal);
            String linha = semComentario.trim();

            if (linha.isEmpty() || linha.startsWith("!")) {
                continue;
            }

            int indentacao = contarIndentacao(semComentario);

            // Secoes e parametros de primeiro nivel do arquivo do Modulo 3.
            if (indentacao == 0 && !linha.startsWith("-")) {

                int separador = linha.indexOf(':');
                if (separador < 0) {
                    continue;
                }

                String chave = normalizarChave(linha.substring(0, separador));
                String valor = limparValor(linha.substring(separador + 1));

                if (chave.equals("arrivals")
                        || chave.equals("queues")
                        || chave.equals("network")
                        || chave.equals("rndnumbers")
                        || chave.equals("seeds")) {
                    secao = chave;
                    filaAtual = null;
                    rotaAtual = null;
                    continue;
                }

                if (chave.equals("rndnumbersperseed")) {
                    rndnumbersPerSeed = Integer.parseInt(valor);
                    secao = "";
                    continue;
                }
            }

            if (secao.equals("arrivals")) {

                int separador = linha.indexOf(':');
                if (separador < 0) {
                    throw new IllegalArgumentException("Chegada invalida no .yml: " + linhaOriginal);
                }

                String nomeFila = limparValor(linha.substring(0, separador));
                double primeiraChegada = Double.parseDouble(
                        limparValor(linha.substring(separador + 1))
                );
                primeirasChegadas.put(nomeFila, primeiraChegada);

            } else if (secao.equals("queues")) {

                if (!linha.startsWith("-") && linha.endsWith(":")) {
                    String nomeFila = limparValor(linha.substring(0, linha.length() - 1));
                    filaAtual = new FilaLida();
                    filaAtual.nome = nomeFila;
                    filasLidas.put(nomeFila, filaAtual);
                    continue;
                }

                if (filaAtual == null) {
                    throw new IllegalArgumentException("Propriedade de fila sem nome: " + linhaOriginal);
                }

                int separador = linha.indexOf(':');
                if (separador < 0) {
                    throw new IllegalArgumentException("Propriedade de fila invalida: " + linhaOriginal);
                }

                preencherFilaLida(
                        filaAtual,
                        normalizarChave(linha.substring(0, separador)),
                        limparValor(linha.substring(separador + 1))
                );

            } else if (secao.equals("network")) {

                if (linha.startsWith("-")) {
                    rotaAtual = new RotaLida();
                    rotasLidas.add(rotaAtual);
                    linha = linha.substring(1).trim();

                    if (linha.isEmpty()) {
                        continue;
                    }
                }

                if (rotaAtual == null) {
                    throw new IllegalArgumentException("Rota sem item '-': " + linhaOriginal);
                }

                int separador = linha.indexOf(':');
                if (separador < 0) {
                    throw new IllegalArgumentException("Rota invalida: " + linhaOriginal);
                }

                preencherRotaLida(
                        rotaAtual,
                        normalizarChave(linha.substring(0, separador)),
                        limparValor(linha.substring(separador + 1))
                );

            } else if (secao.equals("rndnumbers")) {

                if (!linha.startsWith("-")) {
                    throw new IllegalArgumentException("Numero pseudoaleatorio invalido: " + linhaOriginal);
                }

                double numero = Double.parseDouble(limparValor(linha.substring(1)));
                if (numero < 0.0 || numero >= 1.0) {
                    throw new IllegalArgumentException("rndnumber fora do intervalo [0,1): " + numero);
                }
                rndnumbers.add(numero);

            } else if (secao.equals("seeds")) {

                if (!linha.startsWith("-")) {
                    throw new IllegalArgumentException("Seed invalida: " + linhaOriginal);
                }

                seeds.add(Long.parseLong(limparValor(linha.substring(1))));
            }
        }

        if (filasLidas.isEmpty()) {
            throw new IllegalArgumentException("O modelo deve possuir pelo menos uma fila em 'queues'.");
        }

        Modelo modelo = new Modelo();
        modelo.rndnumbersPerSeed = rndnumbersPerSeed;
        modelo.maxRandoms = rndnumbersPerSeed;
        modelo.seeds.addAll(seeds);
        modelo.rndnumbers.addAll(rndnumbers);

        Map<String, Integer> indicePorNome = new LinkedHashMap<>();
        int indice = 0;

        for (FilaLida f : filasLidas.values()) {

            // No arquivo do Modulo 3, a ausencia de capacity representa
            // uma fila sem limite de capacidade informado (G/G/c).
            if (f.capacidade == null) {
                f.capacidade = -1;
            }

            validarFilaLida(f);
            indicePorNome.put(f.nome, indice++);

            Double primeiraChegada = primeirasChegadas.get(f.nome);
            f.primeiraChegada = primeiraChegada;

            modelo.filas.add(
                    new Fila(
                            f.nome,
                            f.servidores,
                            f.capacidade,
                            f.chegadaMin,
                            f.chegadaMax,
                            f.servicoMin,
                            f.servicoMax
                    )
            );
        }

        for (Map.Entry<String, Double> chegada : primeirasChegadas.entrySet()) {

            Integer indiceFila = indicePorNome.get(chegada.getKey());
            if (indiceFila == null) {
                throw new IllegalArgumentException(
                        "Fila declarada em arrivals nao existe em queues: " + chegada.getKey()
                );
            }

            Fila fila = modelo.filas.get(indiceFila);
            if (fila.minArrival() < 0.0 || fila.maxArrival() < fila.minArrival()) {
                throw new IllegalArgumentException(
                        "Intervalo de chegada invalido na fila " + chegada.getKey()
                );
            }

            modelo.chegadasExternas.add(
                    new ChegadaExterna(indiceFila, chegada.getValue())
            );
        }

        if (modelo.chegadasExternas.isEmpty()) {
            throw new IllegalArgumentException(
                    "O modelo deve indicar pelo menos uma chegada externa em 'arrivals'."
            );
        }

        int n = modelo.filas.size();
        modelo.probabilidadesRoteamento = new double[n][n + 1];

        for (RotaLida rota : rotasLidas) {

            validarRotaLida(rota);

            Integer origem = indicePorNome.get(rota.origem);
            Integer destino = indicePorNome.get(rota.destino);

            if (origem == null) {
                throw new IllegalArgumentException("Fila de origem inexistente: " + rota.origem);
            }

            if (destino == null) {
                throw new IllegalArgumentException("Fila de destino inexistente: " + rota.destino);
            }

            modelo.probabilidadesRoteamento[origem][destino] += rota.probabilidade;
        }

        // No formato do Modulo 3, a probabilidade nao descrita em 'network'
        // representa a saida do sistema. Ex.: soma 0.8 => 0.2 para o exterior.
        for (int i = 0; i < n; i++) {

            double soma = 0.0;
            for (int j = 0; j < n; j++) {
                soma += modelo.probabilidadesRoteamento[i][j];
            }

            if (soma > 1.0 + 0.0000001) {
                throw new IllegalArgumentException(
                        "A soma das probabilidades de roteamento da fila "
                                + modelo.filas.get(i).name()
                                + " nao pode ultrapassar 1.0, mas foi " + soma
                );
            }

            modelo.probabilidadesRoteamento[i][n] = Math.max(0.0, 1.0 - soma);
        }

        validarModelo(modelo);

        // Conforme a documentacao do arquivo do Modulo 3: se 'seeds' estiver
        // presente, a lista 'rndnumbers' e ignorada.
        if (!modelo.seeds.isEmpty()) {
            modelo.rndnumbers.clear();
        }

        return modelo;
    }


    static int contarIndentacao(String linha) {
        int i = 0;
        while (i < linha.length() && Character.isWhitespace(linha.charAt(i))) {
            i++;
        }
        return i;
    }


    static void preencherFilaLida(FilaLida fila, String chave, String valor) {

        switch (chave) {
            case "servers", "servidores" -> fila.servidores = Integer.parseInt(valor);
            case "capacity", "capacidade" -> fila.capacidade = parseCapacidade(valor);
            case "minarrival", "chegadamin" -> fila.chegadaMin = Double.parseDouble(valor);
            case "maxarrival", "chegadamax" -> fila.chegadaMax = Double.parseDouble(valor);
            case "minservice", "servicomin" -> fila.servicoMin = Double.parseDouble(valor);
            case "maxservice", "servicomax" -> fila.servicoMax = Double.parseDouble(valor);
            default -> {
                // Propriedades adicionais do arquivo nao alteram a simulacao.
            }
        }
    }


    static void preencherRotaLida(RotaLida rota, String chave, String valor) {

        switch (chave) {
            case "source", "origem", "from" -> rota.origem = valor;
            case "target", "destino", "to" -> rota.destino = valor;
            case "probability", "probabilidade", "prob" -> rota.probabilidade = Double.parseDouble(valor);
            default -> {
                // Mesmo criterio de tolerancia utilizado nas filas.
            }
        }
    }


    static int parseCapacidade(String valor) {
        String v = valor.trim().toLowerCase(Locale.ROOT);
        if (v.equals("inf") || v.equals("infinita") || v.equals("infinite") || v.equals("-1")) {
            return -1;
        }
        return Integer.parseInt(valor);
    }


    static String removerComentario(String linha) {
        int pos = linha.indexOf('#');
        return pos >= 0 ? linha.substring(0, pos) : linha;
    }


    static String limparValor(String valor) {
        String v = valor.trim();

        if ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'"))) {
            v = v.substring(1, v.length() - 1);
        }

        return v.trim();
    }


    static String normalizarChave(String chave) {
        return chave
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
    }


    static void validarFilaLida(FilaLida fila) {

        if (fila.nome == null || fila.nome.isBlank()) {
            throw new IllegalArgumentException("Toda fila deve possuir nome.");
        }

        if (fila.servidores == null || fila.servidores <= 0) {
            throw new IllegalArgumentException("Servidores invalidos na fila " + fila.nome);
        }

        if (fila.capacidade == null || fila.capacidade == 0 || fila.capacidade < -1) {
            throw new IllegalArgumentException("Capacidade invalida na fila " + fila.nome);
        }

        if (fila.capacidade > 0 && fila.capacidade < fila.servidores) {
            throw new IllegalArgumentException(
                    "A capacidade da fila " + fila.nome + " nao pode ser menor que o numero de servidores."
            );
        }

        if (fila.servicoMin == null || fila.servicoMax == null
                || fila.servicoMin < 0.0 || fila.servicoMax < fila.servicoMin) {
            throw new IllegalArgumentException("Intervalo de atendimento invalido na fila " + fila.nome);
        }

        if (fila.chegadaMin == null) {
            fila.chegadaMin = 0.0;
        }
        if (fila.chegadaMax == null) {
            fila.chegadaMax = 0.0;
        }
    }


    static void validarRotaLida(RotaLida rota) {

        if (rota.origem == null || rota.origem.isBlank()) {
            throw new IllegalArgumentException("Toda rota deve possuir source.");
        }

        if (rota.destino == null || rota.destino.isBlank()) {
            throw new IllegalArgumentException("Toda rota deve possuir target.");
        }

        if (rota.probabilidade == null
                || rota.probabilidade < 0.0
                || rota.probabilidade > 1.0) {
            throw new IllegalArgumentException(
                    "Probabilidade invalida na rota " + rota.origem + " -> " + rota.destino
            );
        }
    }


    static void validarModelo(Modelo modelo) {

        if (modelo.rndnumbersPerSeed <= 0) {
            throw new IllegalArgumentException("rndnumbersPerSeed deve ser positivo.");
        }

        int n = modelo.filas.size();

        for (int i = 0; i < n; i++) {

            double soma = 0.0;
            for (int j = 0; j <= n; j++) {
                soma += modelo.probabilidadesRoteamento[i][j];
            }

            if (Math.abs(soma - 1.0) > 0.0000001) {
                throw new IllegalArgumentException(
                        "A soma das probabilidades da fila "
                                + modelo.filas.get(i).name()
                                + " deve resultar em 1.0, mas foi " + soma
                );
            }
        }
    }


    // ---------------------------------------------------------------
    // Saida
    // ---------------------------------------------------------------

    static void imprimirFila(
            Fila fila,
            double tempoGlobal,
            boolean chegadaExterna
    ) {

        System.out.println("*".repeat(54));

        System.out.printf(
                "Queue: %s (G/G/%d/%d)%n",
                fila.name(),
                fila.servers(),
                fila.capacity()
        );


        if (chegadaExterna) {

            System.out.printf(
                    "Arrival: %.1f ... %.1f%n",
                    fila.minArrival(),
                    fila.maxArrival()
            );

        } else {

            System.out.println("Arrival: from Queue1");
        }


        System.out.printf(
                "Service: %.1f ... %.1f%n",
                fila.minService(),
                fila.maxService()
        );


        System.out.println("*".repeat(54));

        System.out.println("  State           Time        Probability");


        double somaTempos = 0.0;
        double somaProb   = 0.0;

        for (int i = 0; i <= fila.capacity(); i++) {

            double p = (tempoGlobal > 0)
                    ? (fila.times()[i] / tempoGlobal * 100)
                    : 0.0;

            somaTempos += fila.times()[i];
            somaProb   += p;

            System.out.printf(
                    "%7d     %10.4f         %5.2f%%%n",
                    i,
                    fila.times()[i],
                    p
            );
        }


        System.out.println("\nNumber of losses: " + fila.loss());

        // Validacao sugerida no material: a soma dos tempos acumulados de cada
        // fila deve ser igual ao tempo global, e as probabilidades devem somar 100%.
        System.out.printf(
                "Validacao: soma dos tempos = %.4f | soma das probabilidades = %.2f%%%n",
                somaTempos,
                somaProb
        );
    }


    static void imprimirResultados(Resultado res) {

        imprimirFila(res.fila1, res.tempoGlobal, true);

        System.out.println();

        imprimirFila(res.fila2, res.tempoGlobal, false);

        System.out.printf(
                "%nTempo global da simulacao: %.4f%n",
                res.tempoGlobal
        );

        System.out.println(
                "Numeros pseudoaleatorios utilizados: " + res.randomsUsados
        );
    }


    static void imprimirResultadosRede(Resultado res) {

        for (Fila fila : res.filas) {

            System.out.println("*".repeat(62));

            if (fila.unlimited()) {
                System.out.printf(
                        "Queue: %s (G/G/%d)%n",
                        fila.name(),
                        fila.servers()
                );
            } else {
                System.out.printf(
                        "Queue: %s (G/G/%d/%d)%n",
                        fila.name(),
                        fila.servers(),
                        fila.capacity()
                );
            }

            if (fila.maxArrival() > 0.0 || fila.minArrival() > 0.0) {
                System.out.printf(
                        "Arrival: %.1f ... %.1f%n",
                        fila.minArrival(),
                        fila.maxArrival()
                );
            } else {
                System.out.println("Arrival: routed from network");
            }

            System.out.printf(
                    "Service: %.1f ... %.1f%n",
                    fila.minService(),
                    fila.maxService()
            );

            System.out.println("*".repeat(62));
            System.out.println("  State           Time        Probability");

            double somaTempos = 0.0;
            double somaProb = 0.0;

            for (int i = 0; i <= fila.maxState(); i++) {

                double p = (res.tempoGlobal > 0)
                        ? (fila.times()[i] / res.tempoGlobal * 100)
                        : 0.0;

                somaTempos += fila.times()[i];
                somaProb += p;

                System.out.printf(
                        "%7d     %12.4f       %8.4f%%%n",
                        i,
                        fila.times()[i],
                        p
                );
            }

            System.out.println("\nNumber of losses: " + fila.loss());

            // Validacao sugerida no material: a soma dos tempos acumulados de cada
            // fila deve ser igual ao tempo global, e as probabilidades devem somar 100%.
            System.out.printf(
                    "Validacao: soma dos tempos = %.4f | soma das probabilidades = %.4f%%%n%n",
                    somaTempos,
                    somaProb
            );
        }

        System.out.printf(
                "Tempo global da simulacao: %.4f%n",
                res.tempoGlobal
        );

        System.out.println(
                "Numeros pseudoaleatorios utilizados: " + res.randomsUsados
        );
    }


    static void executarModeloLegado() {

        System.out.println(
                "Ordem de agendamento da chegada: "
                        + (ORDEM_PSEUDOCODIGO
                            ? "linha 9 (pseudocodigo do M5)"
                            : "antes do teste de capacidade")
        );
        System.out.println();


        Fila fila1 = new Fila(
                "Queue1",
                2,      // servidores
                3,      // capacidade
                1.0,    // chegada min
                5.0,    // chegada max
                4.0,    // atendimento min
                5.0     // atendimento max
        );

        Fila fila2 = new Fila(
                "Queue2",
                1,      // servidores
                5,      // capacidade
                0.0,    // sem chegada externa
                0.0,
                1.0,    // atendimento min
                3.0     // atendimento max
        );

        Resultado res = simular(
                fila1,
                fila2,
                100_000,    // numeros pseudoaleatorios
                12_345L,    // semente
                1.0         // primeira chegada
        );

        imprimirResultados(res);
    }


    public static void main(String[] args) {

        String arquivoModelo = "modelo.yml";
        boolean legado = false;

        for (String arg : args) {

            if (arg.equals("--ordem-alternativa")) {
                ORDEM_PSEUDOCODIGO = false;
            } else if (arg.equals("--legacy")) {
                legado = true;
            } else {
                arquivoModelo = arg;
            }
        }

        if (legado) {
            executarModeloLegado();
            return;
        }

        try {

            Modelo modelo = carregarModelo(arquivoModelo);

            System.out.println(
                    "Ordem de agendamento da chegada: "
                            + (ORDEM_PSEUDOCODIGO
                                ? "linha 9 (pseudocodigo do M5)"
                                : "antes do teste de capacidade")
            );
            System.out.println("Modelo carregado de: " + arquivoModelo);
            System.out.println();

            if (!modelo.seeds.isEmpty()) {

                for (int i = 0; i < modelo.seeds.size(); i++) {

                    long seed = modelo.seeds.get(i);

                    // Cada semente representa uma simulacao independente;
                    // recarrega o modelo para iniciar todas as filas vazias.
                    Modelo execucao = (i == 0) ? modelo : carregarModelo(arquivoModelo);

                    System.out.println("Seed: " + seed);
                    Resultado res = simular(execucao, seed);
                    imprimirResultadosRede(res);

                    if (i < modelo.seeds.size() - 1) {
                        System.out.println();
                        System.out.println("#".repeat(70));
                        System.out.println();
                    }
                }

            } else {

                if (!modelo.rndnumbers.isEmpty()) {
                    System.out.println("Pseudoaleatorios: lista rndnumbers do arquivo");
                }

                Resultado res = simular(modelo);
                imprimirResultadosRede(res);
            }

        } catch (IOException | IllegalArgumentException e) {

            System.err.println("Erro ao carregar/executar o modelo: " + e.getMessage());
            System.err.println("Uso: java SimuladorFilaSimples [modelo.yml] [--ordem-alternativa]");
            System.err.println("Para executar o modelo antigo: java SimuladorFilaSimples --legacy");
        }
    }
}
