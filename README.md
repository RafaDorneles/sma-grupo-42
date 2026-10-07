T1 - M8 | Simulador para Rede de Filas
======================================

ARQUIVOS PRINCIPAIS
-------------------
SimuladorFilaSimples.java  -> codigo-fonte completo
modelo.yml                 -> modelo da T1 no MESMO ESTILO do arquivo do Modulo 3
resultado_simulacao.txt    -> resultado da T1 com 100.000 pseudoaleatorios

REQUISITOS
----------
Java 21. Nao e necessario instalar bibliotecas externas.

COMO COMPILAR
-------------
javac SimuladorFilaSimples.java

COMO EXECUTAR A T1
------------------
java SimuladorFilaSimples modelo.yml

Se modelo.yml estiver na mesma pasta, tambem funciona:
java SimuladorFilaSimples

FORMATO DO ARQUIVO - MODULO 3
-----------------------------
O leitor segue o estilo do arquivo fornecido no Modulo 3:

!PARAMETERS
arrivals:
   Q1: 2.0

queues:
   Q1:
      servers: 1
      minArrival: 2.0
      maxArrival: 4.0
      minService: 1.0
      maxService: 2.0

network:
-  source: Q1
   target: Q2
   probability: 0.2

rndnumbersPerSeed: 100000
seeds:
- 1

INTERPRETACAO DO FORMATO
------------------------
arrivals
  Informa a primeira chegada externa de cada fila.

queues
  Define servidores, capacidade (quando houver), intervalo de chegadas externas
  e intervalo de atendimento. A ausencia de capacity representa G/G/c sem limite
  de capacidade informado, como ocorre com Q1 na T1.

network
  Define apenas os roteamentos entre filas. A parcela de probabilidade que faltar
  para 1.0 representa a saida para o exterior. Assim, na T1:
  Q2 -> Q1 = 0.3 e Q2 -> Q3 = 0.5; os 0.2 restantes saem do sistema.
  Q3 -> Q2 = 0.7; os 0.3 restantes saem do sistema.

rndnumbersPerSeed + seeds
  Quando seeds e informado, o simulador gera a quantidade definida em
  rndnumbersPerSeed para cada seed. Se houver varias seeds, cada uma inicia uma
  simulacao independente, com todas as filas vazias.

rndnumbers
  Tambem e aceito o modo do Modulo 3 com uma lista pronta de pseudoaleatorios.
  Conforme a instrucao do arquivo original, quando seeds existe, rndnumbers e
  ignorado.

MODELO DA T1
------------
Q1: G/G/1, chegada 2..4, atendimento 1..2
Q2: G/G/2/5, atendimento 4..6
Q3: G/G/2/10, atendimento 5..15

Roteamento:
Q1 -> Q2 = 0.2
Q1 -> Q3 = 0.8
Q2 -> Q1 = 0.3
Q2 -> Q3 = 0.5
Q2 -> exterior = 0.2 (implicito)
Q3 -> Q2 = 0.7
Q3 -> exterior = 0.3 (implicito)

Primeira chegada: tempo 2.0
Pseudoaleatorios por seed: 100000
Seed usada no arquivo de entrega: 1


