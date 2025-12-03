# Trabalho de Redes de Computadores 2025

## Grupo

Ana Julia Ticianeli & João Victor Lopes

## Instalação e Utilização

No arquivo submetido, encontram-se no diretório 'out' todas as classes já compiladas. 
Para rodar o cliente de demonstração do UnicastService, basta rodar o programa 'SimpleUnicastApp' a partir da pasta raíz:

```
java -cp out projetoredes.app.SimpleUnicastApp <id_desejado>
```

Com um 'id_desejado' apropriadamente configurado em 'ucsaps.conf' (por padrão, todos os ids 0-3 já estão definidos para
o localhost). Isso iniciará um cliente com o ID inserido, capaz de mandar mensagens para outros clientes que estão
em execução.

## Atividades

A atividade consiste na implementação do serviço de transporte 'UnicastProtocol'
e do serviço de roteamento da camada de aplicação 'RoutingInformationProtocol', que utiliza os serviços do primeiro para troca de mensagens.

Será feita a implementação de aplicações que utilizam dos dois protocolos para demonstração.

### UnicastProtocol

O Protocolo Unicast implementa uma camada de transporte de datagramas não confiável. Sua principal responsabilidade é fornecer um serviço para a camada superior, permitindo-lhe enviar uma mensagem de texto (payload) para outra entidade da rede, utilizando um endereço lógico (`UCSAP_ID`). O protocolo lida com o **encapsulamento e o desencapsulamento das mensagens para transmissão sobre UDP**.

#### 1. Inputs

* **Da Camada Superior (Aplicação):** Para envio de dados, o protocolo recebe dois parâmetros:
    1.  O `ID de Destino` (`int`): O identificador lógico da entidade para a qual a mensagem deve ser enviada.
    2.  O `Payload` (`String`): Os dados que a aplicação deseja enviar.


#### 2. Processamento

O processamento é dividido em duas operações fundamentais:

* **Para Envio (Encapsulamento):**
    1.  O protocolo recebe o `Payload` e o `ID de Destino` da camada superior.
    2.  Ele calcula o tamanho do `Payload` em bytes.
    3.  Constrói a PDU (Protocol Data Unit) no formato de texto especificado: `<UPDREQPDU><espaço><tamanho_dados><espaço><dados>`. Exemplo: `UPDREQPDU 11 "Olá, mundo!"`.
    4.  Consulta seu arquivo de configuração interno para mapear o `ID de Destino` lógico para um endereço de rede físico (Endereço IP e Porta UDP).
    5.  Converte a string da PDU em um array de bytes e a encapsula em um `DatagramPacket` UDP, endereçado ao IP/Porta encontrado.

* **Para Recebimento (Desencapsulamento):**
    1.  O protocolo recebe um `DatagramPacket` da camada de rede.
    2.  Converte o conteúdo de bytes do pacote para uma `String`.
    3.  Realiza o *parse* da string para validar o formato da PDU:
        * Verifica se a mensagem começa com o identificador `"UPDREQPDU"`.
        * Extrai o campo `<tamanho_dados>`.
        * Extrai o `<dados>` (o payload original).
    4.  Se a PDU for inválida, ela pode ser descartada. Se for válida, o payload extraído está pronto para ser entregue.

#### 3. Outputs

* **Para a "Camada Inferior" (Rede UDP):** Após o encapsulamento, o protocolo entrega o `DatagramPacket` finalizado ao Socket UDP para transmissão pela rede.

* **Para a Camada Superior (Aplicação):** Após o recebimento e desencapsulamento bem-sucedido de uma PDU, o protocolo entrega **apenas o `Payload` original** (`String`) para a camada de aplicação que está aguardando para receber dados. A camada superior não tem conhecimento dos detalhes do protocolo, como os cabeçalhos `"UPDREQPDU"` ou o campo de tamanho.


### RoutingInformationProtocol (RIP)

O protocolo RIP implementado neste projeto é um sistema de roteamento baseado no algoritmo de **Vetor de Distâncias**. Ele opera sobre a camada de transporte `UnicastProtocol` descrita anteriormente e é responsável por manter as tabelas de roteamento dos nós da rede atualizadas dinamicamente em resposta a mudanças na topologia ou nos custos dos enlaces.

#### 1. Arquitetura e Funcionamento

O sistema é dividido em duas entidades principais:
1.  **Entidade Nó (`RIPNode`):** Representa um roteador na rede.
2.  **Entidade Gerente (`RIPManager`):** Entidade central (ID 0) responsável por monitorar e configurar a rede.

**Lógica do Nó (Algoritmo de Vetor de Distâncias):**
Cada nó mantém uma **Tabela de Distâncias** ($NxN$, onde $N$ é o número de nós), onde:
* A linha correspondente ao próprio nó (Linha 0 na implementação) representa o seu **Vetor de Distâncias** atual.
* As demais linhas armazenam os últimos vetores de distância recebidos de seus vizinhos diretos.

O cálculo da rota segue a equação de Bellman-Ford.

**Mecanismos de Atualização:**
* **Propagação Periódica:** Um `Timer` em cada nó envia periodicamente (padrão de 10s) o seu vetor de distâncias para todos os vizinhos diretos.
* **Propagação por Gatilho (Triggered Update):** Sempre que um nó recalcula seu vetor e detecta uma mudança de custo para qualquer destino, ele propaga imediatamente o novo vetor para seus vizinhos.
* **Gerenciamento de Enlaces:** O gerente pode alterar o custo de um enlace (simulando congestionamento ou falha). O nó detecta essa mudança via comando `RIPSET`, atualiza seu custo local $c(x,v)$ e recalcula as rotas.

#### 2. Protocol Data Units (PDUs)

O protocolo RIP define um conjunto de mensagens de texto para comunicação entre nós e com o gerente.

**PDUs de Roteamento (Entre Nós):**
* **`RIPIND <SourceID> <Vector>`**: *Indication*. Usada para propagar o vetor de distâncias.
    * Exemplo: `RIPIND 1 0:2:5` (Nó 1 informa que seus custos para os nós 0, 1 e 2 são 0, 2 e 5, respectivamente).

**PDUs de Gerenciamento (Gerente <-> Nó):**
* **`RIPGET <NodeA> <NodeB>`**: Gerente solicita o custo do enlace entre A e B.
* **`RIPSET <NodeA> <NodeB> <Cost>`**: Gerente define o custo do enlace entre A e B.
* **`RIPNTF <NodeA> <NodeB> <Cost>`**: Nó notifica o gerente sobre o custo atual de um enlace (resposta a GET ou SET).
* **`RIPRQT`**: Gerente solicita a tabela de distância completa de um nó.
* **`RIPRSP <NodeID> <Table>`**: Nó responde com sua tabela completa.

#### 3. Estrutura de Classes

* **`projetoredes.rip.RIPNode`**: A classe principal do roteador. Inicializa a camada Unicast, carrega a topologia, gerencia a tabela de distâncias e executa a thread de timer para propagação.
* **`projetoredes.rip.RIPManager`**: Implementa a interface de gerenciamento, traduzindo chamadas de método (como `setLinkCost`) em PDUs RIP enviadas via Unicast.
* **`projetoredes.rip.RIPConfig`**: Contém constantes globais, como o ID do gerente (0), o valor de infinito (-1) e limites da rede.
* **`projetoredes.app.NodeLauncher`**: Ponto de entrada para instanciar um nó RIP.
* **`projetoredes.app.RoutingManagementApp`**: Aplicação CLI que permite ao usuário interagir com o `RIPManager` via terminal.

#### 4. Execução da Solução

Para executar o sistema de roteamento, certifique-se de que os arquivos de configuração (`ucsaps.conf` e `ripids.conf`) estejam na pasta `config/`.

Para o funcionamento correto, **todos os nós descritos em ripids.conf precisam ter seus processos iniciados**.

**Passo 1: Iniciar os Nós**
Abra terminais separados para cada nó que deseja simular. O ID deve corresponder aos definidos na topologia.

```bash
# Sintaxe: java -jar NodeLauncher.jar <node_id> [tempo_propagacao_ms]

# Exemplo: Iniciar Nó 1
java -jar NodeLauncher.jar 1

# Exemplo: Iniciar Nó 2
java -jar NodeLauncher.jar 2
```

**Passo 2: Iniciar o Gerente**
Em outro terminal, inicie a aplicação de gerenciamento.

```bash
java -jar RoutingManagementApp.jar
```


