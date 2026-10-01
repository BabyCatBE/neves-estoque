# Regras permanentes — Neves Estoque

Estas regras devem ser obedecidas por qualquer IA ou desenvolvedor que trabalhe no projeto.

## 1. Continuidade

- não recomeçar o projeto;
- iniciar pelo Contexto Mestre operacional atual + `task.md` + arquivos especializados aplicáveis;
- histórico antigo é fonte de aprofundamento seletivo, não leitura obrigatória integral;
- não ressuscitar ideias substituídas;
- não inventar informação ausente.

## 2. Autoridade por assunto

- Contexto Mestre: decisões aprovadas, continuidade, checkpoints e estado consolidado;
- GitHub: código, commits, branches, versões, migrations versionadas e CI;
- Supabase: schema, migrations realmente aplicadas, dados, RLS, funções e configurações;
- Notion: painel resumido;
- legado: dados/regras históricas do sistema antigo.

Se fontes divergirem:
1. identificar;
2. mostrar o que cada uma diz;
3. determinar a autoridade correta;
4. não corrigir silenciosamente;
5. marcar A VERIFICAR se faltar evidência;
6. pedir decisão de Elias quando houver impacto funcional/técnico relevante.

## 3. Estados

Usar quando fizer sentido:
`IDEIA`, `A DEFINIR`, `APROVADO`, `EM IMPLEMENTAÇÃO`, `IMPLEMENTADO`, `CI APROVADA`, `TESTADO`, `A VERIFICAR`.

Nunca confundir:
- ideia com decisão;
- aprovado com implementado;
- implementado com testado;
- CI com teste funcional;
- código alterado com ambiente atualizado;
- migration criada com migration aplicada.

## 4. Fluxo de trabalho

Preferência:
**ENTENDER → DECIDIR → IMPLEMENTAR → TESTAR → CONSOLIDAR**

Antes de mudança relevante:
- entender comportamento atual;
- ler os arquivos de contexto aplicáveis;
- conferir GitHub real;
- conferir Supabase quando houver impacto operacional;
- identificar dependências;
- preservar rollback e dados.

## 5. Git e branches

- `develop` = integração/DEV;
- `main` = produção;
- não mover `main`, publicar PROD ou criar Release sem instrução explícita;
- distinguir último HEAD funcional de commits documentais posteriores;
- não reintegrar PR histórico apenas porque continua aberto;
- preservar histórico de migrations.

## 6. Banco, dados e segurança

- operações destrutivas, limpeza, migração real ou alteração importante no banco exigem confirmação de Elias;
- nunca apagar/substituir/migrar dados reais sem autorização explícita;
- nunca armazenar senhas, tokens, chaves privadas ou secrets em Git, Drive, Notion ou arquivos de contexto;
- cliente Web/Android não é autoridade final de autorização;
- dados Offline nunca concedem privilégio;
- validar cliente para UX e banco/backend para integridade;
- preservar auditoria;
- migration versionada não prova aplicação.

## 7. Regras centrais do domínio

- Estoque Atual = última Conferência válida + Entradas posteriores;
- sem saída individual rotineira no V1;
- Entrada = mercadoria realmente recebida;
- Entrada exige quantidade > 0;
- Conferência aceita zero e proíbe negativo;
- preço em branco e bonificação são estados diferentes;
- IDs oficiais são UUIDs internos;
- histórico deve permanecer coerente após exclusões, mesclas e conversões;
- Compras é simulação temporária.

## 8. Offline

- nada é enviado automaticamente ao reconectar;
- envio exige confirmação consciente e revalidação online;
- usar idempotência;
- cache reconstruível e pendências separados;
- no Android, manter arquivos JSON internos até nova decisão;
- limpar cache não apaga pendências;
- troca de usuário com pendências continua A DEFINIR.

## 9. Paridade entre plataformas

Toda nova função adicionada ao aplicativo mobile deve ser avaliada para Web e, quando aplicável, implementada também na Web.

Paridade significa preservar comportamento/regra de negócio; não exige copiar literalmente um componente específico de plataforma.

Exemplo: teclado numérico personalizado Android não precisa ser reproduzido no navegador.

## 10. Entrada de dados

- campos exclusivamente numéricos no Android usam o teclado numérico próprio Neves;
- campos de texto, datas e outros formatos usam a entrada apropriada;
- aplicar essa regra globalmente a novos campos;
- botões +/− não possuem regra global única por módulo: Conferência e Compras possuem usos vigentes; não aplicar automaticamente a outros fluxos.

## 11. Navegação

- preservar a navegação atual até decisão explícita;
- barra inferior Android está **A VERIFICAR** por conflito histórico;
- não adicionar/remover barra inferior durante outra tarefa sem decisão específica.

## 12. Testes

- Web: checks aplicáveis de typecheck, lint, testes e build;
- Android: lint, testes unitários/build/CI;
- CI aprovada não significa TESTADO;
- aparelho deve ser registrado separadamente;
- testar fluxos, erros, persistência, dados existentes e integrações conforme impacto.

## 13. Consolidação documental

Durante desenvolvimento:
- priorizar código, CI e banco quando necessário;
- não atualizar Drive/Notion a cada tentativa.

Ao final de bloco importante:
- produzir checkpoint completo.

Quando Elias pedir consolidação:
1. validar checkpoint;
2. conferir GitHub;
3. conferir CI;
4. conferir Supabase se relevante;
5. confirmar versões/migrations/dados;
6. atualizar Contexto Mestre e arquivos especializados;
7. atualizar Notion como resumo.

## 14. Seis arquivos especializados

- produto/escopo → `prd.md`;
- arquitetura/stack/banco → `architecture.md`;
- regras permanentes → `rules.md`;
- padrão visual/UX → `design.md`;
- fase/prioridade/trabalho atual → `task.md`;
- decisão/aprendizado duradouro → `memory.md`.

Não transformar esses seis arquivos em cópia integral do Contexto Mestre.

## 15. Critério mínimo de conclusão

Uma tarefa só é concluída quando:
1. mudança real foi implementada;
2. testes aplicáveis passaram ou falhas ficaram registradas;
3. banco/ambiente foi conferido quando impactado;
4. divergências relevantes não ficaram escondidas;
5. continuidade ficou suficiente para outro chat/IA.

## 16. Modelo de IA nos checkpoints

Quando disponível, registrar no checkpoint:
- modelo utilizado;
- nível de esforço;
- resultado;
- limitações do ambiente;
- aprendizado útil para calibração futura.
