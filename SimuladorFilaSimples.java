import java.util.*;

public class SimuladorFilaSimples {

    // MÓDULO 4

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

    static final int CHEGADA = 0;
    static final int SAIDA   = 1;

    static class StopSimulationException extends RuntimeException {
    }


    // MÓDULO 6

    static final int PASSAGEM = 2;


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


    static class Evento implements Comparable<Evento> {

        private final double tempo;
        private final int tipo;


        Evento(double tempo, int tipo) {
            this.tempo = tempo;
            this.tipo = tipo;
        }


        double tempo() {
            return tempo;
        }

        int tipo() {
            return tipo;
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


    static Resultado simular(
            Fila fila1,
            Fila fila2,
            int maxRandoms,
            long seed,
            double firstArrival
    ) {

        resetLcg(seed, maxRandoms);

        double t = 0.0;

        Escalonador escalonador = new Escalonador();

        escalonador.adicionar(
                new Evento(firstArrival, CHEGADA)
        );


        try {

            while (!escalonador.vazio()) {

                Evento ev = escalonador.proximo();

                double evtT = ev.tempo();

                double delta = evtT - t;


                fila1.acumulaTempo(delta);
                fila2.acumulaTempo(delta);


                t = evtT;


                if (ev.tipo() == CHEGADA) {

                    escalonador.adicionar(
                            new Evento(
                                    t + uniform(
                                            fila1.minArrival(),
                                            fila1.maxArrival()
                                    ),
                                    CHEGADA
                            )
                    );


                    if (fila1.status() < fila1.capacity()) {

                        fila1.in();


                        if (fila1.status() <= fila1.servers()) {

                            escalonador.adicionar(
                                    new Evento(
                                            t + uniform(
                                                    fila1.minService(),
                                                    fila1.maxService()
                                            ),
                                            PASSAGEM
                                    )
                            );
                        }

                    } else {

                        fila1.addLoss();
                    }


                } else if (ev.tipo() == PASSAGEM) {

                    fila1.out();


                    if (fila1.status() >= fila1.servers()) {

                        escalonador.adicionar(
                                new Evento(
                                        t + uniform(
                                                fila1.minService(),
                                                fila1.maxService()
                                        ),
                                        PASSAGEM
                                )
                        );
                    }


                    if (fila2.status() < fila2.capacity()) {

                        fila2.in();


                        if (fila2.status() <= fila2.servers()) {

                            escalonador.adicionar(
                                    new Evento(
                                            t + uniform(
                                                    fila2.minService(),
                                                    fila2.maxService()
                                            ),
                                            SAIDA
                                    )
                            );
                        }

                    } else {

                        fila2.addLoss();
                    }


                } else if (ev.tipo() == SAIDA) {

                    fila2.out();


                    if (fila2.status() >= fila2.servers()) {

                        escalonador.adicionar(
                                new Evento(
                                        t + uniform(
                                                fila2.minService(),
                                                fila2.maxService()
                                        ),
                                        SAIDA
                                )
                        );
                    }
                }
            }

        } catch (StopSimulationException ignored) {
        }


        Resultado res = new Resultado();

        res.tempoGlobal = t;

        res.fila1 = fila1;
        res.fila2 = fila2;

        res.randomsUsados = randomsUsed();

        return res;
    }


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

            System.out.println(
                    "Arrival: from Queue1"
            );
        }


        System.out.printf(
                "Service: %.1f ... %.1f%n",
                fila.minService(),
                fila.maxService()
        );


        System.out.println("*".repeat(54));

        System.out.println(
                "  State           Time        Probability"
        );


        for (int i = 0; i <= fila.capacity(); i++) {

            double p = (tempoGlobal > 0)
                    ? (fila.times()[i] / tempoGlobal * 100)
                    : 0.0;


            System.out.printf(
                    "%7d     %10.4f         %5.2f%%%n",
                    i,
                    fila.times()[i],
                    p
            );
        }


        System.out.println(
                "\nNumber of losses: " + fila.loss()
        );
    }


    static void imprimirResultados(Resultado res) {

        imprimirFila(
                res.fila1,
                res.tempoGlobal,
                true
        );


        System.out.println();


        imprimirFila(
                res.fila2,
                res.tempoGlobal,
                false
        );


        System.out.printf(
                "%nTempo global da simulacao: %.4f%n",
                res.tempoGlobal
        );


        System.out.println(
                "Numeros pseudoaleatorios utilizados: "
                        + res.randomsUsados
        );
    }


    public static void main(String[] args) {

        Fila fila1 = new Fila(
                "Queue1",
                2,
                3,
                1.0,
                5.0,
                4.0,
                5.0
        );


        Fila fila2 = new Fila(
                "Queue2",
                1,
                5,
                0.0,
                0.0,
                1.0,
                3.0
        );


        Resultado res = simular(
                fila1,
                fila2,
                100_000,
                12_345L,
                1.0
        );


        imprimirResultados(res);
    }
}
