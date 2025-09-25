# Trabalho de Redes de Computadores 2025

## Grupo

Ana Julia Ticianeli - NROUSP & João Victor Lopes - 13672982

## Atividades

A atividade consiste na implementação do serviço de transporte 'UnicastProtocol'
e do serviço de roteamento da camada de aplicação 'RoutingInformationProtocol', que utiliza os serviços do primeiro para troca de mensagens.

Além disso, será feita a implementação de aplicações que utilizam dos dois protocolos para
demonstração.

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





