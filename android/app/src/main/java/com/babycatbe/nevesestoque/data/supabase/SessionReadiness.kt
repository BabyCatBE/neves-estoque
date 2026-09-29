package com.babycatbe.nevesestoque.data.supabase

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Espera a sessão salva neste aparelho terminar de carregar antes de uma leitura.
 *
 * Com a abertura imediata, a interface pode aparecer enquanto o Auth ainda está inicializando
 * (ex.: renovando um token vencido). Uma leitura feita nesse intervalo sairia sem sessão e falharia;
 * esperar aqui evita erro falso na primeira tela. Nunca espera para sempre: após o limite, a leitura
 * segue normalmente e o tratamento de erro/cópia local existente decide.
 */
suspend fun awaitAuthSessionReady(timeoutMillis: Long = 10_000L) {
    val client = SupabaseProvider.client ?: return
    val status = client.auth.sessionStatus
    if (status.value !is SessionStatus.Initializing) return
    withTimeoutOrNull(timeoutMillis) {
        status.first { it !is SessionStatus.Initializing }
    }
}
