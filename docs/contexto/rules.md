# Regras permanentes — Neves Estoque

Estas regras devem ser obedecidas por qualquer IA ou desenvolvedor que trabalhe no projeto.

## 1. Continuidade e fontes

- não recomeçar o projeto;
- antes de alteração técnica relevante, consultar os arquivos aplicáveis em `docs/contexto/` e, quando necessário, Contexto Mestre, GitHub, Notion e Supabase real;
- usar a informação mais recente e consolidada; não ressuscitar ideias substituídas;
- não inventar informação ausente;
- quando fontes divergirem, identificar a divergência, determinar a autoridade correta e não corrigir silenciosamente.

### Autoridade por assunto

- Contexto Mestre: comportamento esperado, decisões aprovadas, estado consolidado e checkpoints;
- GitHub: código, estrutura, commits, CI e migrations versionadas;
- Supabase: schema, migrations realmente aplicadas, dados e configurações operacionais;
- Notion: painel resumido;
- sistema legado: dados e regras históricas para referência/migração.

## 2. Estados

Usar quando fizer sentido: `IDEIA`, `A DEFINIR`, `APROVADO`, `EM IMPLEMENTAÇÃO`, `IMPLEMENTADO`, `CI APROVADA`, `TESTADO`, `A VERIFICAR`.

Nunca confundir aprovação com implementação, implementação com teste, CI com teste funcional, código alterado com ambiente atualizado ou migration criada com migration aplicada.

## 3. Escopo de mudanças

- entender o comportamento atual antes de modificar;
- fazer mudanças pequenas e focadas;
- não alterar arquivos sem relação com a tarefa;
- preservar funcionalidades existentes;
- reutilizar componentes, helpers, RPCs e padrões existentes quando apropriado;
- investigar antes de sobrescrever código;
- não trocar stack, banco, autenticação, hospedagem ou arquitetura importante sem necessidade e decisão consciente;
- não misturar refatoração ampla com correção pequena sem motivo.

## 4. Git e migrations

- respeitar a branch operacional vigente registrada no Contexto Mestre e em `task.md`;
- `develop` é integração/DEV e `main` é produção;
- não fazer merge para `develop`/`main` nem preparar release sem instrução explícita;
- manter lockfiles e versões controladas;
- preservar histórico de migrations já aplicadas;
- não renomear retroativamente migrations apenas para fazer Git e banco parecerem iguais.

## 5. Banco, dados e segurança

- mudanças destrutivas, limpeza de dados, migração real ou alteração importante de banco exigem confirmação de Elias;
- nunca apagar, substituir ou migrar dados reais sem confirmação explícita;
- nunca armazenar senhas, tokens, chaves privadas ou outros segredos em Git, Contexto Mestre, Notion ou arquivos de contexto;
- cliente Web/Android não é autoridade final de autorização;
- manter as proteções de acesso existentes no banco;
- validar dados no cliente para UX e novamente no backend/banco;
- dados Offline nunca concedem privilégio;
- ações críticas devem preservar auditoria adequada;
- antes de produção, executar a auditoria e os retestes previstos no projeto.

## 6. Supabase

- confirmar sempre o projeto **Neves Estoque**, não o projeto de Etiquetas;
- migration versionada não prova aplicação;
- Supabase real é autoridade do histórico aplicado;
- reutilizar os fluxos transacionais existentes para operações sensíveis;
- não contornar regras de acesso para resolver erro de permissão;
- após mudanças estruturais no banco, validar o ambiente real e os avisos de segurança/performance.

## 7. Regras do domínio

- Estoque Atual = última Conferência válida + Entradas posteriores;
- não criar saída individual rotineira no V1;
- Entrada representa mercadoria realmente recebida;
- Conferência aceita zero; Entrada exige quantidade maior que zero;
- preço vazio e bonificação são estados diferentes;
- IDs oficiais são UUIDs internos e invisíveis;
- histórico deve permanecer coerente mesmo após exclusões;
- Compras é simulação temporária.

## 8. Offline

- nada deve sincronizar automaticamente ao reconectar;
- envio de pendência exige confirmação consciente e revalidação online;
- usar idempotência para evitar duplicação;
- cache reconstruível e pendências devem permanecer separados;
- no Android, manter a arquitetura atual de arquivos JSON internos até nova decisão;
- não apagar pendências ao limpar cache;
- comportamento das pendências ao trocar de usuário no mesmo aparelho está `A DEFINIR`; não decidir por conta própria.

## 9. Design e compatibilidade

- novas telas devem respeitar `design.md` e os componentes/padrões existentes;
- priorizar toque confortável no Android/celular e uso eficiente de largura/teclado no desktop;
- preservar acessibilidade básica e estados claros;
- evitar animações longas que prejudiquem o uso operacional.

## 10. Testes

- alteração Web relevante: executar os checks aplicáveis de tipagem, lint, testes e build;
- alteração Android relevante: lint, testes unitários e build/CI;
- CI aprovada não promove automaticamente para `TESTADO`;
- testes em aparelho devem ser registrados separadamente;
- verificar fluxos principais, erros, persistência, dados existentes e integrações conforme o impacto.

## 11. Manutenção dos seis arquivos

Quando uma alteração relevante mudar uma área, atualizar na mesma etapa:

- produto, escopo ou funcionalidades → `prd.md`;
- arquitetura, stack, fluxo ou banco → `architecture.md`;
- regra permanente → `rules.md`;
- padrão visual → `design.md`;
- fase, prioridade ou trabalho atual → `task.md`;
- decisão, erro recorrente ou aprendizado duradouro → `memory.md`.

O Contexto Mestre continua sendo a continuidade consolidada geral. Os seis arquivos não devem virar uma cópia integral dele.

## 12. Critério mínimo de conclusão

Uma tarefa técnica só pode ser tratada como concluída quando:

1. a mudança real foi implementada no lugar correto;
2. os testes aplicáveis passaram ou as falhas ficaram registradas;
3. banco/ambiente foram verificados quando impactados;
4. nenhuma divergência relevante ficou escondida;
5. documentação aplicável foi sincronizada;
6. o próximo passo ficou claro.


## 13. Trabalho com múltiplas IAs e branches paralelas

- é normal existir trabalho novo em branch isolada depois do último checkpoint integrado;
- separar sempre **último ponto funcional integrado/comprovado** de **trabalho atual ainda não integrado**;
- não assumir que o HEAD mais novo invalida o checkpoint seguro anterior;
- antes de escrever ou integrar, verificar se outra IA avançou uma branch ou atualizou Contexto Mestre/Notion;
- se a branch operacional avançar por documentação enquanto outra branch funcional estiver em andamento, não forçar fast-forward nem reescrever histórico;
- preservar as duas linhas de trabalho e verificar o método seguro de integração no momento apropriado;
- integração de trabalho funcional isolado que dependa de decisão/autoridade de Elias não deve ser antecipada apenas para “sincronizar” documentação.
