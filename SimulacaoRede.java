import java.util.*;

public class SimulacaoRede {

    static class StopSimulation extends RuntimeException {
    }

    static long a = 1664525L;
    static long c = 1013904223L;
    static long m = 4294967296L;
    static long seed = 12345L;
    static int rndUsed = 0;
    static final int MAX_RND = 100000;

    static void resetGenerator() {
        seed = 12345L;
        rndUsed = 0;
    }

    static double rnd() {
        if (rndUsed >= MAX_RND) {
            throw new StopSimulation();
        }
        seed = (a * seed + c) % m;
        rndUsed++;
        return (double) seed / (double) m;
    }

    static double uniform(double minVal, double maxVal) {
        return minVal + (maxVal - minVal) * rnd();
    }

    static class FilaConfig {
        String id;
        int servidores;
        int capacidade;
        double atendMin;
        double atendMax;

        FilaConfig(String id, int servidores, int capacidade, double atendMin, double atendMax) {
            this.id = id;
            this.servidores = servidores;
            this.capacidade = capacidade;
            this.atendMin = atendMin;
            this.atendMax = atendMax;
        }
    }

    static class ChegadaExterna {
        double chegMin;
        double chegMax;
        double primeiraChegada;

        ChegadaExterna(double chegMin, double chegMax, double primeiraChegada) {
            this.chegMin = chegMin;
            this.chegMax = chegMax;
            this.primeiraChegada = primeiraChegada;
        }
    }

    static class Rota {
        String destino;
        double prob;

        Rota(String destino, double prob) {
            this.destino = destino;
            this.prob = prob;
        }
    }

    static LinkedHashMap<String, FilaConfig> filasCfg = new LinkedHashMap<>();
    static LinkedHashMap<String, ChegadaExterna> chegadasExternas = new LinkedHashMap<>();
    static LinkedHashMap<String, List<Rota>> roteamento = new LinkedHashMap<>();

    static void montarConfiguracaoRede() {
        filasCfg.put("q1", new FilaConfig("q1", 1, 15, 2.0, 5.0));
        filasCfg.put("q2", new FilaConfig("q2", 2, 10, 4.0, 10.0));
        filasCfg.put("q3", new FilaConfig("q3", 1, 5, 8.0, 15.0));

        chegadasExternas.put("q1", new ChegadaExterna(1.0, 4.0, 2.0));

        roteamento.put("q1", Arrays.asList(
                new Rota("q2", 0.7),
                new Rota("q3", 0.3)
        ));
        roteamento.put("q2", Arrays.asList(
                new Rota("q3", 0.4),
                new Rota("OUT", 0.6)
        ));
        roteamento.put("q3", Arrays.asList(
                new Rota("q2", 1.0)
        ));
    }

    static final int CHEGADA_EXTERNA = 1;
    static final int CHEGADA_INTERNA = 2;
    static final int SAIDA = 3;

    static class Evento {
        double tempo;
        int tipo;
        String fid;

        Evento(double tempo, int tipo, String fid) {
            this.tempo = tempo;
            this.tipo = tipo;
            this.fid = fid;
        }
    }

    static class ResultadoRede {
        double tempoGlobal;
        Map<String, double[]> tempos;
        Map<String, Integer> perdas;
        Map<String, Integer> saidas;
    }

    static ResultadoRede simularRede() {
        resetGenerator();

        Map<String, Integer> estado = new HashMap<>();
        Map<String, Integer> perdas = new HashMap<>();
        Map<String, double[]> tempos = new HashMap<>();
        Map<String, Integer> saidas = new HashMap<>();

        for (String fid : filasCfg.keySet()) {
            estado.put(fid, 0);
            perdas.put(fid, 0);
            tempos.put(fid, new double[filasCfg.get(fid).capacidade + 1]);
            saidas.put(fid, 0);
        }

        PriorityQueue<Evento> heap = new PriorityQueue<>(Comparator.comparingDouble(e -> e.tempo));
        double tAtual = 0.0;

        for (Map.Entry<String, ChegadaExterna> entry : chegadasExternas.entrySet()) {
            String fid = entry.getKey();
            ChegadaExterna cfg = entry.getValue();
            double tInicial = !Double.isNaN(cfg.primeiraChegada)
                    ? cfg.primeiraChegada
                    : uniform(cfg.chegMin, cfg.chegMax);
            heap.add(new Evento(tInicial, CHEGADA_EXTERNA, fid));
        }

        try {
            while (!heap.isEmpty()) {
                Evento ev = heap.poll();
                double tEvento = ev.tempo;
                int tipo = ev.tipo;
                String fid = ev.fid;

                double delta = tEvento - tAtual;
                for (String filaId : filasCfg.keySet()) {
                    tempos.get(filaId)[estado.get(filaId)] += delta;
                }
                tAtual = tEvento;

                if (tipo == CHEGADA_EXTERNA) {
                    ChegadaExterna cfgCh = chegadasExternas.get(fid);
                    double proxChegada = tAtual + uniform(cfgCh.chegMin, cfgCh.chegMax);
                    heap.add(new Evento(proxChegada, CHEGADA_EXTERNA, fid));

                    FilaConfig fc = filasCfg.get(fid);
                    if (estado.get(fid) < fc.capacidade) {
                        estado.put(fid, estado.get(fid) + 1);
                        if (estado.get(fid) <= fc.servidores) {
                            double tSaida = tAtual + uniform(fc.atendMin, fc.atendMax);
                            heap.add(new Evento(tSaida, SAIDA, fid));
                        }
                    } else {
                        perdas.put(fid, perdas.get(fid) + 1);
                    }

                } else if (tipo == CHEGADA_INTERNA) {
                    FilaConfig fc = filasCfg.get(fid);
                    if (estado.get(fid) < fc.capacidade) {
                        estado.put(fid, estado.get(fid) + 1);
                        if (estado.get(fid) <= fc.servidores) {
                            double tSaida = tAtual + uniform(fc.atendMin, fc.atendMax);
                            heap.add(new Evento(tSaida, SAIDA, fid));
                        }
                    } else {
                        perdas.put(fid, perdas.get(fid) + 1);
                    }

                } else if (tipo == SAIDA) {
                    saidas.put(fid, saidas.get(fid) + 1);

                    FilaConfig fc = filasCfg.get(fid);
                    estado.put(fid, estado.get(fid) - 1);
                    if (estado.get(fid) >= fc.servidores) {
                        double tSaida = tAtual + uniform(fc.atendMin, fc.atendMax);
                        heap.add(new Evento(tSaida, SAIDA, fid));
                    }

                    List<Rota> rotas = roteamento.getOrDefault(fid, Collections.emptyList());
                    double r = rnd();
                    double acc = 0.0;
                    String destino = "OUT";

                    for (Rota rota : rotas) {
                        acc += rota.prob;
                        if (r <= acc) {
                            destino = rota.destino;
                            break;
                        }
                    }

                    if (!destino.equals("OUT")) {
                        heap.add(new Evento(tAtual, CHEGADA_INTERNA, destino));
                    }
                }
            }
        } catch (StopSimulation e) {
        }

        ResultadoRede r = new ResultadoRede();
        r.tempoGlobal = tAtual;
        r.tempos = tempos;
        r.perdas = perdas;
        r.saidas = saidas;
        return r;
    }

    static class Indices {
        double populacao;
        double vazao;
        double utilizacao;
        double tempoResposta;
    }

    static Map<String, Indices> calcularIndicesDesempenho(double tGlobal, Map<String, double[]> tempos,
                                                            Map<String, Integer> saidas) {
        Map<String, Indices> indices = new HashMap<>();

        for (Map.Entry<String, FilaConfig> entry : filasCfg.entrySet()) {
            String fid = entry.getKey();
            FilaConfig cfg = entry.getValue();

            double populacao = 0.0;
            double ocupacaoServidores = 0.0;

            double[] temposFila = tempos.get(fid);
            for (int nClientes = 0; nClientes < temposFila.length; nClientes++) {
                double tempoNoEstado = temposFila[nClientes];
                populacao += nClientes * tempoNoEstado;
                ocupacaoServidores += Math.min(nClientes, cfg.servidores) * tempoNoEstado;
            }

            populacao = (tGlobal > 0) ? populacao / tGlobal : 0.0;
            double vazao = (tGlobal > 0) ? saidas.get(fid) / tGlobal : 0.0;
            double utilizacao = (tGlobal > 0 && cfg.servidores > 0)
                    ? ocupacaoServidores / (cfg.servidores * tGlobal)
                    : 0.0;
            double tempoResposta = (vazao > 0) ? populacao / vazao : 0.0;

            Indices ind = new Indices();
            ind.populacao = populacao;
            ind.vazao = vazao;
            ind.utilizacao = utilizacao;
            ind.tempoResposta = tempoResposta;
            indices.put(fid, ind);
        }

        return indices;
    }

    static void imprimirResultados(ResultadoRede res) {
        Map<String, Indices> indices = calcularIndicesDesempenho(res.tempoGlobal, res.tempos, res.saidas);

        for (Map.Entry<String, FilaConfig> entry : filasCfg.entrySet()) {
            String fid = entry.getKey();
            FilaConfig cfg = entry.getValue();

            System.out.println("\n------------- Queue Information ---------------");
            System.out.printf("Queue: (G/G/%d/%d)%n", cfg.servidores, cfg.capacidade);

            if (chegadasExternas.containsKey(fid)) {
                ChegadaExterna ch = chegadasExternas.get(fid);
                System.out.printf("Arrivals between: %.1f ... %.1f%n", ch.chegMin, ch.chegMax);
            } else {
                System.out.println("Arrivals between: Routed from network");
            }

            System.out.printf("Service between: %.1f ... %.1f%n", cfg.atendMin, cfg.atendMax);
            System.out.println("-------------- Time Distribution ---------------");

            double[] temposFila = res.tempos.get(fid);
            for (int i = 0; i < temposFila.length; i++) {
                double t = temposFila[i];
                double p = (res.tempoGlobal > 0) ? (t / res.tempoGlobal * 100) : 0;
                System.out.printf("%d: %.2f (%.2f%%)%n", i, t, p);
            }

            System.out.println("------------- Lost Clients --------------");
            System.out.printf("Lost Clients: %d%n", res.perdas.get(fid));

            Indices ind = indices.get(fid);
            System.out.println("---------- Indices de Desempenho -----------");
            System.out.printf("Populacao: %.4f%n", ind.populacao);
            System.out.printf("Vazao: %.4f clientes/unidade de tempo%n", ind.vazao);
            System.out.printf("Utilizacao: %.2f%%%n", ind.utilizacao * 100);
            System.out.printf("Tempo de resposta: %.4f unidades de tempo%n", ind.tempoResposta);
            System.out.println("-------- Simulation Time ----------");
            System.out.printf("Total Time: %.2f%n", res.tempoGlobal);
            System.out.println("=================================================================");
        }

        System.out.printf("Total Simulation Time: %.2f%n", res.tempoGlobal);
    }

    public static void main(String[] args) {
        montarConfiguracaoRede();
        ResultadoRede resultado = simularRede();
        imprimirResultados(resultado);
    }
}
