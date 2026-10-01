# Memória técnica — Neves Estoque

Este arquivo registra decisões e aprendizados duradouros. O histórico completo permanece no arquivo histórico do Contexto Mestre.

## Eficiência de contexto — consolidado em 30/09/2026

O Contexto Mestre operacional não deve voltar a crescer como transcrição cumulativa.

Regra:
- ponto de entrada pequeno e vigente;
- checkpoints/histórico preservados fora da camada operacional;
- leitura seletiva do histórico;
- `docs/contexto/` especializados, sem duplicar tudo;
- se um checkpoint não permitir retomada segura, melhorar o checkpoint em vez de exigir leitura de centenas de páginas.

A reorganização de 30/09/2026 preservou literalmente o documento antigo e reduziu o ponto de entrada operacional de 373 páginas para uma versão compacta.

## Modelo central do estoque

**Última Conferência válida + Entradas posteriores = Estoque Atual.**

O legado possui regra histórica diferente. Não alterar o legado para fazê-lo coincidir com o aplicativo novo e não ressuscitar a regra antiga no app sem nova decisão.

## Identidade e autorização

- UUIDs oficiais internos;
- autenticação e autorização são separadas;
- `app_users` controla acesso interno;
- dados Offline nunca concedem privilégio.

## Offline Android

Decisão vigente:
- JSON internos privados;
- cache, pendências e acesso separados;
- nada é enviado automaticamente ao reconectar;
- envio exige confirmação consciente;
- revalidação online + idempotência;
- Room/SQLite não adotado.

A ideia histórica de sincronização automática “quando segura” foi substituída.

Ponto em aberto:
- pendências ao trocar de usuário no mesmo aparelho: A DEFINIR.

## Migrações — divergência histórica conhecida

As três migrations iniciais têm timestamps diferentes no Git e no Supabase aplicado.

Os nomes lógicos correspondem.

Regra:
- Supabase é autoridade do histórico aplicado;
- não renomear retroativamente migrations já aplicadas.

## Arquivos especializados

Manter:
- `prd.md`;
- `architecture.md`;
- `rules.md`;
- `design.md`;
- `task.md`;
- `memory.md`.

Cada um deve conter somente sua camada de informação.

## Android — padrões duradouros

- app nativo Kotlin + Jetpack Compose;
- Home como destino inicial autenticado;
- abertura pode usar acesso local validado com revalidação em segundo plano;
- preload enxuto de áreas mais usadas;
- datas visíveis em `DD/MM/AAAA`, ISO internamente;
- Claro/Escuro manual e local;
- campos exclusivamente numéricos usam teclado próprio Neves;
- texto, data e outros formatos usam entrada apropriada.

### Controles de quantidade

A antiga frase “menos/mais somente Conferência” não é mais uma regra global.

Estado:
- Conferência usa controles de menos/mais;
- Entrada não usa;
- Compras usa no fluxo atual.

### Barra inferior

Há conflito histórico:
- decisão antiga sem barra inferior;
- decisão posterior propondo barra para áreas mais usadas;
- implementação atual sem barra inferior.

Estado: **A VERIFICAR**.

Não alterar a navegação até decisão explícita de Elias.

## Paridade Web/Mobile

Nova função mobile deve ser avaliada para Web e implementada quando aplicável.

Paridade é funcional, não visual/literal.

Exemplo: teclado Android próprio não precisa existir no navegador.

## CHECKPOINT 254 — aprendizado de implementação

Bloco consolidado:
- GPT-6.1 Sol / Alto foi adequado para implementação ampla Android + Web;
- GPT-6.1 Sol / Médio foi suficiente para correções determinísticas/localizadas;
- Work sem Gradle/Android SDK deslocou compilação/Lint Android para CI;
- falhas intermediárias relevantes devem permanecer no checkpoint;
- integração final foi comprovada por CI pós-merge.

## Versões — cuidado

Web/package está em `0.26.1`.

Android está em `0.35.0-alpha01`, mas `SYSTEM_VERSION` ainda é `0.26.0`.

Estado: A VERIFICAR.

Não mudar código apenas para alinhar documentação.

## Repositório

O repositório está público no GitHub na consolidação de 30/09/2026.

Referências históricas que o chamam de privado devem ser tratadas como snapshots antigos.

## Regra de consolidação

Durante blocos técnicos:
- implementar e testar primeiro;
- não sincronizar Drive/Notion a cada tentativa.

Quando Elias pedir consolidação:
- conferir GitHub;
- conferir CI;
- conferir Supabase quando necessário;
- confirmar versões/migrations/dados;
- só então atualizar as fontes permanentes.

## Continuidade entre IAs

- múltiplas IAs podem trabalhar no mesmo projeto;
- nunca confiar automaticamente no texto de outra IA;
- validar nas fontes reais;
- distinguir último checkpoint seguro de trabalho posterior;
- branches simultâneas devem permanecer separadas até integração consciente.
