<p align="center">
  <img src="../assets/readme/banners/springboot.png" alt="Spring Boot" width="100%">
</p>

# Visão de produto

A central de mensagens é onde as pessoas da plataforma conversam. Ela atende clientes, profissionais e empresas, que combinam serviços e acompanham projetos sem trocar de aplicativo. Também guarda as conversas de cada pessoa com o assistente virtual, para que a equipe acompanhe a qualidade das respostas.

## O que a pessoa consegue fazer

- Abrir uma conversa individual com qualquer outra pessoa ativa da plataforma.
- Criar um grupo com outras pessoas e dar um título a ele.
- Participar da comunidade de um projeto e criar subgrupos de conversa com os membros dela.
- Mandar, editar e apagar as próprias mensagens.
- Ver o histórico da conversa e quantas mensagens ainda não leu.
- Silenciar ou arquivar uma conversa só para si.
- Ver quem está conectado e quem está digitando.
- Receber um aviso no celular quando chega mensagem e ela está desconectada.
- Conversar com o assistente virtual e dar uma nota de 0 a 10 ao atendimento.
- Avaliar um profissional ou um produto com uma nota de 0 a 5.

## Regras das conversas

- Uma conversa individual entre duas pessoas é única. Se as duas já conversaram, a conversa antiga reabre com o histórico, em vez de nascer outra.
- Um grupo precisa de quem cria e de mais uma pessoa ativa.
- Ao criar um grupo, adicionar pessoas a ele, criar uma comunidade ou adicionar membros, quem tem a conta inativa ou inexistente fica de fora, e os demais entram. Num subgrupo, quem não é da comunidade também fica de fora. A resposta diz quem não entrou e por quê. Se ninguém puder entrar, nada muda.
- Quem cria o grupo é o gestor. Só o gestor adiciona e remove pessoas. Para sair, o gestor indica quem fica no lugar, porque um grupo com gente sempre tem gestor. Se ele é o último participante, sai sem indicar ninguém.
- O grupo que fica sem ninguém é apagado de vez, com todas as mensagens.
- Qualquer participante pode sair do grupo, mesmo quando o grupo ou a comunidade está parado.
- Quem entra num grupo lê o histórico inteiro. As mensagens anteriores à entrada não contam como não lidas.
- Quem sai ou é removido perde o acesso na hora, inclusive nos aparelhos que estavam conectados.
- Silenciar e arquivar valem só para a própria pessoa. Os outros participantes não veem essa escolha.
- A conversa com o assistente é só da pessoa e do assistente. Nela não há edição de mensagem, silêncio nem arquivo.

## Regras das mensagens

- A mensagem tem até 8000 caracteres.
- A pessoa edita ou apaga a própria mensagem até 30 minutos depois do envio. Depois disso, a mensagem fica como está.
- A mensagem apagada continua no histórico como "mensagem apagada", sem o texto, e não volta.
- Mensagens apagadas e as que a própria pessoa mandou não contam como não lidas.
- O aviso no celular vai só para quem está desconectado e não silenciou nem arquivou a conversa. Ele mostra um trecho de até 100 caracteres.
- Quando o envio falha sem certeza de que a mensagem foi gravada, o aplicativo consulta o histórico antes de mandar de novo, porque o serviço não descarta mensagens repetidas.

## Regras da comunidade do projeto

- Cada projeto tem no máximo uma comunidade, que hoje é criada por quem pediu o projeto e passa a ser dele. A regra definida é que a comunidade nasça junto com o projeto e reúna a equipe e o cliente.
- A comunidade tem três papéis. O dono faz tudo. O administrador adiciona membros, promove membros a administrador, remove membros comuns e arquiva ou reativa a comunidade. Só o dono rebaixa ou remove um administrador. O membro conversa e cria subgrupos.
- O dono não pode ser removido nem sair, e não existe troca de dono.
- A comunidade pertence ao projeto e nunca é apagada.
- Os subgrupos só têm membros da comunidade. Quem sai da comunidade sai também de todos os subgrupos, e o acesso a eles acaba no mesmo instante. Se a pessoa geria algum subgrupo, a gestão passa para quem foi indicado.
- Quem saiu da comunidade só volta depois de sair de todos os subgrupos.
- Na comunidade arquivada ninguém manda mensagem nem administra. Ler, sair e reativar continuam possíveis.
- Administradores veem todos os subgrupos. Membros veem só os de que participam.

## Avaliações e nota do assistente

- A nota do atendimento do assistente vale uma vez por conversa e vai de 0 a 10. Notas de 0 a 6 contam como detratoras, 7 e 8 como neutras e 9 e 10 como promotoras.
- A avaliação de um profissional ou de um produto vai de 0 a 5, com comentário opcional, e ninguém avalia a si mesmo. Essa capacidade vai sair deste serviço, e as avaliações passam a ficar apenas no cadastro central.

## Onde aparece para a pessoa

- Na tela de conversas do aplicativo web e do aplicativo mobile, com a lista de conversas, as não lidas, a prévia da última mensagem e os indicadores de conectado e digitando.
- Na comunidade de cada projeto, com os subgrupos.
- No celular, como aviso de mensagem nova.
- No painel interno da equipe, que mostra as conversas com o assistente, as etapas de cada resposta e o custo de uso dos modelos.

## Relação com o resto da plataforma

O serviço de acesso confirma quem é a pessoa e se a conta está ativa. O cadastro central diz quem pediu cada projeto. O serviço de notificações entrega o aviso no celular. Os assistentes virtuais gravam as próprias respostas e o registro de cada etapa aqui, e a central não chama os assistentes.
