package com.babycatbe.nevesestoque.ui.load

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Mantém só a carga mais recente de uma tela.
 *
 * Um novo pedido cancela o anterior antes de começar: um resultado antigo nunca
 * sobrescreve o estado de um refresh mais novo (ex.: refresh pedido logo depois de
 * uma gravação enquanto a carga inicial ainda estava em andamento).
 */
class LatestLoad {
    private var job: Job? = null

    fun launch(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job {
        job?.cancel()
        return scope.launch(block = block).also { job = it }
    }
}

/**
 * Igual a runCatching, mas não transforma cancelamento em erro: uma carga cancelada
 * por outra mais nova termina em silêncio, sem mostrar mensagem de falha.
 */
suspend inline fun <T> loadCatching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

/**
 * Executa [onRefresh] uma única vez para cada novo valor de [refreshKey] (> 0).
 *
 * O LaunchedEffect sozinho repetia o refresh toda vez que a tela voltava à composição
 * (ex.: ao voltar de um detalhe), mesmo sem nova gravação. O último valor tratado fica
 * no estado salvo da tela e sobrevive à navegação.
 */
@Composable
fun RefreshOnKeyChange(refreshKey: Long, onRefresh: () -> Unit) {
    var handledKey by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0L && refreshKey != handledKey) {
            handledKey = refreshKey
            onRefresh()
        }
    }
}
