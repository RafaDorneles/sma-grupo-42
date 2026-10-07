import java.util.*;

/**
 * M6 | Simulacao de Filas em Tandem
 *
 * CHEGADA -> [ Fila1 ] --PASSAGEM--> [ Fila2 ] --> SAIDA
 *
 * Fila1: G/G/2/3, chegadas entre 1..5, atendimento entre 4..5
 * Fila2: G/G/1/5, atendimento entre 1..3
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

    static void resetLcg(long seed, int maxRandoms) {
        mclPrev = seed;
        mclUsed = 0;
        mclMax = maxRandoms;
    }

    static double nextRandom() {

        if (mclUsed >= mclMax) {
            throw new StopSimulationException();
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

        private final double[] times;


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

            this.times = new double[capacidade + 1];
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

        void in() {
            customers++;
        }

        void out() {
            customers--;
        }

        void addLoss() {
            loss++;
        }

        void acumulaTempo(double delta) {
            times[customers] += delta;
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

        int randomsUsados;
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

        resetLcg(seed, maxRandoms);

        double t = 0.0;

        Escalonador escalonador = new Escalonador();

        escalonador.adicionar(
                new Evento(
                        firstArrival,
                        CHEGADA,
                        EXTERIOR,
                        filaChegadaExterna
                )
        );


        try {

            while (!escalonador.vazio()) {

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

                    if (filaDestino.status() < filaDestino.capacity()) {

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
                    if (filaDestino.status() < filaDestino.capacity()) {

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

        // Mantem o Resultado anterior intacto enquanto a parte de saida
        // ainda trabalha especificamente com fila1 e fila2.
        if (!listaDeFilas.isEmpty()) {
            res.fila1 = listaDeFilas.get(0);
        }

        if (listaDeFilas.size() > 1) {
            res.fila2 = listaDeFilas.get(1);
        }

        res.randomsUsados = randomsUsed();

        return res;
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


    public static void main(String[] args) {

        if (args.length > 0 && args[0].equals("--ordem-alternativa")) {
            ORDEM_PSEUDOCODIGO = false;
        }

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
}
