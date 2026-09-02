import java.util.*;

public class SimuladorFilaSimples {

    static long mclA    = 1_664_525L;
    static long mclC    = 1_013_904_223L;
    static long mclM    = 4_294_967_296L;
    static long mclPrev = 12_345L;
    static int  mclUsed = 0;

    static void resetLcg(long seed) {
        mclPrev = seed;
        mclUsed = 0;
    }

    static double nextRandom() {
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

    static class Evento {
        double tempo;
        int tipo;

        Evento(double tempo, int tipo) {
            this.tempo = tempo;
            this.tipo = tipo;
        }
    }

    static class Resultado {
        double tempoGlobal;
        double[] tempoEstados;
        int perdas;
        int randomsUsados;
    }

    static Resultado simular(
            int numServers,
            int K,
            double arrivalLo,
            double arrivalHi,
            double serviceLo,
            double serviceHi,
            int maxRandoms,
            long seed,
            double firstArrival
    ) {
        resetLcg(seed);

        int n = 0;
        double t = 0.0;
        int perdas = 0;
        double[] tempoEstados = new double[K + 1];

        PriorityQueue<Evento> heap = new PriorityQueue<>(Comparator.comparingDouble(e -> e.tempo));

        heap.add(new Evento(firstArrival, CHEGADA));

        while (randomsUsed() < maxRandoms && !heap.isEmpty()) {
            Evento ev = heap.poll();
            double evtT = ev.tempo;
            int tipo = ev.tipo;

            tempoEstados[n] += evtT - t;
            t = evtT;

            if (tipo == CHEGADA) {
                heap.add(new Evento(t + uniform(arrivalLo, arrivalHi), CHEGADA));

                if (n < K) {
                    n += 1;
                    if (n <= numServers) {
                        heap.add(new Evento(t + uniform(serviceLo, serviceHi), SAIDA));
                    }
                } else {
                    perdas += 1;
                }

            } else {
                n -= 1;
                if (n >= numServers) {
                    heap.add(new Evento(t + uniform(serviceLo, serviceHi), SAIDA));
                }
            }
        }

        double tempoGlobal = 0.0;
        for (double v : tempoEstados) tempoGlobal += v;

        Resultado res = new Resultado();
        res.tempoGlobal = tempoGlobal;
        res.tempoEstados = tempoEstados;
        res.perdas = perdas;
        res.randomsUsados = randomsUsed();
        return res;
    }

    static void imprimirResultadosSimples(String nomeFila, double arrLo, double arrHi,
                                           double servLo, double servHi, Resultado res, int K) {
        double tg = res.tempoGlobal;
        double[] te = res.tempoEstados;
        int perdas = res.perdas;

        System.out.println("*".repeat(54));
        System.out.println("Queue:    " + nomeFila);
        System.out.printf("Arrival: %.1f ... %.1f%n", arrLo, arrHi);
        System.out.printf("Service: %.1f ... %.1f%n", servLo, servHi);
        System.out.println("*".repeat(54));
        System.out.println("  State           Time        Probability");

        for (int i = 0; i <= K; i++) {
            double p = (tg > 0) ? (te[i] / tg * 100) : 0.0;
            System.out.printf("%7d     %10.4f         %5.2f%%%n", i, te[i], p);
        }

        System.out.println("\nNumber of losses:s " + perdas + "\n");
        System.out.printf("Tempo global da simulacao: %.4f%n", res.tempoGlobal);
        System.out.println("Numeros pseudoaleatorios utilizados: " + res.randomsUsados);
    }

    public static void main(String[] args) {
        Resultado resGG15 = simular(
                1, 5,
                3.0, 5.0,
                4.0, 5.0,
                100_000, 12_345L, 3.0
        );
        imprimirResultadosSimples("Queue1 (G/G/1/5)", 3.0, 5.0, 4.0, 5.0, resGG15, 5);

        Resultado resGG25 = simular(
                2, 5,
                3.0, 5.0,
                4.0, 5.0,
                100_000, 12_345L, 3.0
        );
        imprimirResultadosSimples("Queue2 (G/G/2/5)", 3.0, 5.0, 4.0, 5.0, resGG25, 5);
    }
}